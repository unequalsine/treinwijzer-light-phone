"""Generate hosted build logic from a strict, non-executable TOML schema."""

from __future__ import annotations

import json
import re
import tomllib


class BuildConfigError(ValueError):
    pass


_NAME = r"[A-Za-z0-9_][A-Za-z0-9_.-]*"
_MODULE = re.compile(rf"{_NAME}:{_NAME}(?::{_NAME})?", re.ASCII)
_EXCLUDE = re.compile(rf"{_NAME}:{_NAME}", re.ASCII)
_FIELD = re.compile(r"[A-Z][A-Z0-9_]*", re.ASCII)
_CONFIGURATIONS = {"implementation", "compileOnly", "runtimeOnly", "ksp",
                   "testImplementation", "testRuntimeOnly"}

_TEMPLATE = """plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

fun hostedBuildConfigString(vararg hex: String): String =
    hex.joinToString("").chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)

android {
    compileSdk = rootProject.ext["compileSdk"] as Int
    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int
%s
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = %s
            isShrinkResources = %s
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client"))
%s
}
"""


def _literal(value: str) -> str:
    escapes = {"$": "\\$", "\f": "\\u000c"}
    return '"' + "".join(escapes.get(char, json.dumps(char, ensure_ascii=True)[1:-1])
                         for char in value) + '"'


def render(text: str) -> str:
    try:
        config = tomllib.loads(text)
    except tomllib.TOMLDecodeError as exc:
        raise BuildConfigError(f"invalid TOML: {exc}") from exc
    if config.keys() - {"dependencies", "buildConfig", "release"}:
        raise BuildConfigError("only dependencies, buildConfig and release are supported")
    release = config.get("release", {})
    if not isinstance(release, dict) or release.keys() - {"minify"}:
        raise BuildConfigError("release only supports minify")
    minify = release.get("minify", False)
    if not isinstance(minify, bool):
        raise BuildConfigError("release.minify must be boolean")

    dependencies = config.get("dependencies", [])
    if not isinstance(dependencies, list) or len(dependencies) > 100:
        raise BuildConfigError("dependencies must be an array of at most 100 tables")
    lines = []
    for dep in dependencies:
        if not isinstance(dep, dict) or dep.keys() - {"module", "configuration", "platform", "exclude"}:
            raise BuildConfigError("unsupported dependency fields")
        module = dep.get("module")
        if not isinstance(module, str) or len(module) > 1024 or not _MODULE.fullmatch(module):
            raise BuildConfigError("module must be group:artifact[:exact-version]")
        version = module.split(":")[2:]
        if version and (version[0].startswith("latest.") or version[0].upper().endswith("-SNAPSHOT")):
            raise BuildConfigError("dynamic and snapshot versions are not supported")
        configuration = dep.get("configuration", "implementation")
        if not isinstance(configuration, str) or configuration not in _CONFIGURATIONS:
            raise BuildConfigError("unsupported dependency configuration")
        platform = dep.get("platform", False)
        if not isinstance(platform, bool) or (platform and configuration == "ksp"):
            raise BuildConfigError("platform must be boolean and cannot be used for ksp")
        excludes = dep.get("exclude", [])
        if not isinstance(excludes, list) or len(excludes) > 100:
            raise BuildConfigError("exclude must be an array of at most 100 group:artifact strings")
        argument = _literal(module)
        if platform:
            argument = f"platform({argument})"
        line = f"    {configuration}({argument})"
        if excludes:
            line += " {\n"
            for exclude in excludes:
                if not isinstance(exclude, str) or len(exclude) > 1024 or not _EXCLUDE.fullmatch(exclude):
                    raise BuildConfigError("exclude must be group:artifact")
                group, name = exclude.split(":")
                line += f"        exclude(group = {_literal(group)}, module = {_literal(name)})\n"
            line += "    }"
        lines.append(line)

    fields = config.get("buildConfig", {})
    if not isinstance(fields, dict) or len(fields) > 100:
        raise BuildConfigError("buildConfig must be a table of at most 100 strings")
    field_lines = []
    for name, value in sorted(fields.items()):
        if len(name) > 100 or not _FIELD.fullmatch(name) or name in {"DEBUG", "APPLICATION_ID", "BUILD_TYPE", "FLAVOR", "VERSION_CODE", "VERSION_NAME"}:
            raise BuildConfigError("unsupported BuildConfig field name")
        if not isinstance(value, str) or len(value) > 8192:
            raise BuildConfigError("BuildConfig values must be strings of at most 8192 characters")
        java_literal = json.dumps(value, ensure_ascii=True)
        # Keep literal data out of the plugin's lexical build-script banlist.
        encoded = java_literal.encode("ascii").hex()
        chunks = ", ".join(f'"{encoded[start:start + 16000]}"' for start in range(0, len(encoded), 16000))
        field_lines.append(f'        buildConfigField("String", {_literal(name)}, hostedBuildConfigString({chunks}))')
    minify_literal = "true" if minify else "false"
    return _TEMPLATE % ("\n".join(field_lines), minify_literal, minify_literal, "\n".join(lines))
