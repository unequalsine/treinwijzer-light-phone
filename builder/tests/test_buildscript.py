from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path

import pytest

from lightbuilder import buildscript
from lightbuilder.__main__ import cmd_collect, cmd_prepare
from lightbuilder.extract import ExtractionError, extract
from .test_extract import _make_dev_repo


@pytest.mark.parametrize("text", [
    '[plugins]\nid = "evil"',
    '[android]\napplicationId = "com.evil"',
    'dependencies = ["file:evil.jar"]',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.+"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:latest.release"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.0-SNAPSHOT"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.0@jar"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.0:classifier"',
    '[[dependencies]]\nmodule = "${System.exit(1)}:x:1"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.4.2"\nconfiguration = "annotationProcessor"',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.4.2"\nconfiguration = []',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.4.2"\nfiles = ["evil.jar"]',
    '[[dependencies]]\nmodule = "io.ktor:ktor-client-core:3.4.2"\nexclude = ["${evil}:x"]',
    '[buildConfig]\nDEBUG = "true"',
    '[buildConfig]\n"${evil}" = "value"',
    '[buildConfig]\nVALUE = 42',
    '[buildConfig]\nVALUE = "unterminated',
    '[release]\nminify = "true"',
    '[release]\ncommand = "evil"',
])
def test_executable_or_unsupported_configuration_rejected(text: str) -> None:
    with pytest.raises(buildscript.BuildConfigError):
        buildscript.render(text)


def test_dependencies_bom_processors_and_exclusions() -> None:
    script = buildscript.render('''
[[dependencies]]
module = "androidx.compose:compose-bom:2026.03.01"
platform = true
[[dependencies]]
module = "androidx.compose.ui:ui"
[[dependencies]]
module = "androidx.room:room-compiler:2.7.0"
configuration = "ksp"
[[dependencies]]
module = "io.github.david-allison:anki-android-backend:0.1.70-anki26.09.3"
exclude = ["com.google.protobuf:protobuf-javalite"]
''')
    assert 'implementation(platform("androidx.compose:compose-bom:2026.03.01"))' in script
    assert 'implementation("androidx.compose.ui:ui")' in script
    assert 'ksp("androidx.room:room-compiler:2.7.0")' in script
    assert 'exclude(group = "com.google.protobuf", module = "protobuf-javalite")' in script


@pytest.mark.parametrize("text,expected", [("", "false"), ("[release]\nminify = false", "false"),
                                          ("[release]\nminify = true", "true")])
def test_release_minification_is_declarative(text: str, expected: str) -> None:
    script = buildscript.render(text)
    assert f"isMinifyEnabled = {expected}" in script
    assert f"isShrinkResources = {expected}" in script


def test_buildconfig_values_are_encoded_as_data() -> None:
    script = buildscript.render('''[buildConfig]
VALUE = '${System.exit(1)} " \\ newline'
''')
    assert '${System.exit(1)}' not in script
    assert 'buildConfigField("String", "VALUE", hostedBuildConfigString("' in script
    assert 'alias(libs.plugins.light.sdk)' in script


def test_harmless_strings_do_not_trigger_lexical_policy() -> None:
    value = "resolutionStrategy applicationId = x apply(from pluginManager.apply"
    script = buildscript.render('[buildConfig]\nERROR_HELP = ' + json.dumps(value))
    assert value not in script
    assert json.dumps(value).encode("ascii").hex() in script


def test_long_unicode_value_uses_bounded_jvm_literals() -> None:
    value = "😀" * 8192
    script = buildscript.render('[buildConfig]\nVALUE = ' + json.dumps(value, ensure_ascii=False))
    line = next(line for line in script.splitlines() if '"VALUE"' in line)
    chunks = re.findall(r'"([0-9a-f]+)"', line)
    assert max(map(len, chunks)) <= 16000
    assert bytes.fromhex("".join(chunks)).decode("ascii") == json.dumps(value)


def test_literal_backslashes_and_control_characters_remain_distinct() -> None:
    assert buildscript._literal("\\f") == r'"\\f"'
    assert buildscript._literal("\f") == r'"\u000c"'
    assert buildscript._literal("\\u0022") == r'"\\u0022"'


def test_prepare_archives_generated_script_and_declarative_inputs(tmp_path: Path) -> None:
    dev = _make_dev_repo(tmp_path / "dev")
    dst = tmp_path / "workspace" / "tool"
    out = tmp_path / "out"
    assert cmd_prepare(argparse.Namespace(dev_repo=dev, workspace_tool=dst, tool_path="tool", output_dir=out)) == 0
    with zipfile.ZipFile(out / "extracted-source.zip") as archive:
        assert archive.read("build.gradle.kts") == (dst / "build.gradle.kts").read_bytes()
        assert archive.read("lightbuild.toml") == b""
        files = json.loads((out / "extraction.json").read_text())["files"]
        assert set(archive.namelist()) == {f if "/" not in f else "src/main/" + f for f in files}


def test_invalid_declarative_config_fails_prepare(tmp_path: Path) -> None:
    dev = _make_dev_repo(tmp_path / "dev")
    (dev / "tool/lightbuild.toml").write_text('[tasks]\ncommand = "evil"')
    with pytest.raises(ExtractionError, match="lightbuild.toml"):
        extract(dev, tmp_path / "workspace/tool")


@pytest.mark.parametrize("mutation", ["script", "missing", "config"])
def test_collect_rejects_unprepared_or_modified_build_logic(tmp_path: Path, mutation: str) -> None:
    dev = _make_dev_repo(tmp_path / "dev")
    workspace = tmp_path / "workspace"
    extract(dev, workspace / "tool")
    if mutation == "script":
        (workspace / "tool/build.gradle.kts").write_text('tasks.register("replaceApk") {}')
    elif mutation == "missing":
        (workspace / "tool/lightbuild.toml").unlink()
    else:
        (workspace / "tool/lightbuild.toml").write_text('[buildConfig]\nNEW = "value"')
    output = tmp_path / "out"
    assert cmd_collect(argparse.Namespace(workspace=workspace, output_dir=output)) == 2
    assert not (output / "recipe.json").exists()
    assert not (output / "tool-unsigned.apk").exists()
    assert json.loads((output / "error.json").read_text())["kind"] == "policy_violation"


def test_collect_legitimate_prepared_output(tmp_path: Path) -> None:
    dev = _make_dev_repo(tmp_path / "dev")
    workspace = tmp_path / "workspace"
    extract(dev, workspace / "tool")
    apk = workspace / "tool/build/outputs/apk/release/tool-release-unsigned.apk"
    apk.parent.mkdir(parents=True)
    apk.write_bytes(b"test artifact")
    out = tmp_path / "out"
    args = argparse.Namespace(workspace=workspace, output_dir=out, tool_git_url="https://example.com/tool",
                              tool_git_commit="a" * 40, sdk_git_ref="b" * 40,
                              image_digest="sha256:" + "c" * 64, tool_git_ref="main",
                              gradle_command='[":tool:assembleRelease"]', source_date_epoch=0)
    assert cmd_collect(args) == 0
    assert (out / "tool-unsigned.apk").read_bytes() == apk.read_bytes()
    assert json.loads((out / "recipe.json").read_text())["tool"]["id"] == "com.example.mytool"
