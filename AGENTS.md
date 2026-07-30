# AI Agents Guide

This file documents conventions and resources for AI agents working on this repository.

## Documentation

Comprehensive project analysis and quick-reference guides are maintained in `local/ai-docs/`:

| File | Purpose | Size |
|------|---------|------|
| `local/ai-docs/treinwijzer-light-phone-analysis.md` | Full project analysis, architecture, commit history, recommendations | ~27KB |
| `local/ai-docs/treinwijzer-ai-quickref.md` | Quick reference, restrictions, common tasks, code patterns | ~11KB |

**Note**: The `local/` directory is git-ignored. These files are for AI agent reference only and are not version-controlled.

## Repository Overview

- **Project**: Treinwijzer - Dutch train planner for Light Phone III
- **Upstream**: `lightphone/light-sdk` (forked at commit `ddf33e4`)
- **Tool ID**: `nl.treinwijzer.lightphone`
- **All Treinwijzer code**: In `tool/` module only

## Quick Start for AI Agents

1. **Read first**: `local/ai-docs/treinwijzer-ai-quickref.md`
2. **Deep dive**: `local/ai-docs/treinwijzer-light-phone-analysis.md`
3. **Key constraints**: See RESTRICTIONS section in quickref

## Important Rules

- **DO NOT** modify SDK modules (`sdk/*`) - only `tool/` module contains Treinwijzer code
- **DO NOT** use restricted Android APIs (see quickref for full list)
- **DO NOT** commit `local.properties` or any secrets
- **ALL** external communication (PR descriptions, comments) must be human-authored

## Where to Find Things

| What | Location |
|------|----------|
| Treinwijzer source | `tool/src/main/kotlin/nl/treinwijzer/lightphone/` |
| Tool config | `tool/lighttool.toml` |
| Build config | `tool/build.gradle.kts` |
| Data models | `tool/src/main/kotlin/nl/treinwijzer/lightphone/Models.kt` |
| API client | `tool/src/main/kotlin/nl/treinwijzer/lightphone/TreinwijzerApi.kt` |
| Main UI | `tool/src/main/kotlin/nl/treinwijzer/lightphone/TreinwijzerScreen.kt` |
| ViewModel | `tool/src/main/kotlin/nl/treinwijzer/lightphone/TreinwijzerViewModel.kt` |
| Tests | `tool/src/test/kotlin/nl/treinwijzer/lightphone/ContractAndLogicTest.kt` |

## Useful Commands

```bash
# Sync with upstream
git fetch origin && git fetch upstream && git merge upstream/main

# Build and test
./gradlew :tool:testDebugUnitTest :tool:lintDebug :tool:assembleDebug

# Check all modules
./gradlew check

# Run emulator
./scripts/start-emulator.sh
./scripts/install-lightos.sh
./scripts/install-tool.sh
```

## AI Policy

From CONTRIBUTING.md:
- All communication must come from a human
- You are responsible for any code from your account
- Must be able to explain changes in your own words
- Delete LLM-generated comments (they are overly verbose)
- We (Light team) are responsible for merged code

## Common Pitfalls

- Trying to use `LocalContext` - **BLOCKED** - Use LightScreen APIs instead
- Using reflection - **BLOCKED**
- Adding dependencies not in `ALLOWED_DEPENDENCIES` - **BLOCKED**
- Modifying SDK modules - **NOT ALLOWED**
- Using `startActivity()` - **BLOCKED** - Use `navigateTo()` instead

See `plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt` for complete allow/block lists.

## Validation and Testing

**CRITICAL**: AI agents **CANNOT** directly access the Android emulator display. A human MUST perform validation.

**ALL CODE CHANGES MADE BY AI ARE UNVALIDATED UNTIL A HUMAN CONFIRMS THEM IN THE EMULATOR.**

This includes:
- All commits made by Mistral Vibe
- All UI changes (alignment, styling, text)
- All behavioral changes (scrolling, navigation, logic)

AI must always add "UNVALIDATED - requires emulator testing" to commit messages.

### Emulator Testing (HUMAN ONLY)
After AI implements changes, a **human must** validate in the Android emulator:

1. **Reload the tool**: Run `./scripts/install-tool.sh` to install the updated version
2. **Take screenshots**: Capture before/after screenshots for visual changes
3. **Test functionality**: Verify the change works as intended
4. **Document results**: Note any issues or confirm success

### AI Agent Limitations
- **CANNOT** run emulator (requires display server access)
- **CANNOT** take screenshots
- **CANNOT** visually verify UI changes
- **CAN** only implement code changes

### Documentation Requirements
- AI must clearly state in commit message: "UNVALIDATED - requires emulator testing"
- Commit message must describe changes and what needs validation
- Visual changes should include description of expected appearance
- Behavioral changes should describe the new behavior and test steps

### Change Validation Checklist
- [ ] AI implements code changes
- [ ] AI commits with "UNVALIDATED" notice
- [ ] Human runs `./scripts/install-tool.sh`
- [ ] Human tests in emulator
- [ ] Human takes screenshots for UI changes
- [ ] Human confirms success or reports issues
