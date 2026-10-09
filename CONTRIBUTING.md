# Contributing

Bug reports, translations and improvements are welcome. For a bug report, include the tool version, what happened and steps to reproduce it. Screenshots help with layout issues.

Keep Treinwijzer changes in `tool/`. The SDK modules follow [Light’s upstream repository](https://github.com/lightphone/light-sdk); SDK changes should be discussed there.

Before sending a change:

- Explain what it changes and why.
- Run `./gradlew check`.
- Test the affected screens and behaviour in the LightOS emulator, including Dutch and English text.
- Keep credentials, local configuration and build outputs out of Git.

Respect the SDK’s API and dependency restrictions and our [code of conduct](CODE_OF_CONDUCT.md).
