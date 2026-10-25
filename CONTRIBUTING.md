# Contributing to Jiangshi

Jiangshi is a small, personal project. Bug reports, suggestions and pull requests are welcome, but there's no guarantee a feature request will be picked up.

## Reporting a bug

[Open an issue](https://github.com/Ikuu/jiangshi/issues/new) and include:

* The Jiangshi version, from **Settings → About**
* Your device, its Android version, and which APK you installed (for example `arm64-v8a` or `universal`)
* The steps that trigger the bug, what you expected, and what happened instead
* For problems with a particular series, the source and extension it comes from

Screenshots or a screen recording help a lot for reader issues.

## Pull requests

1. Branch from `main` and keep each pull request to one change.
2. Match the surrounding code's style. `ktlintCodeStyle.xml` can be imported into Android Studio as the code style.
3. Build and test before opening the pull request:

   ```sh
   ./gradlew assembleStandardRelease
   ./gradlew testReleaseUnitTest testStandardReleaseUnitTest
   ```

   Pull requests run the same build and tests in CI.
4. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) style used in the history, for example `fix: Stop reader going back a page when rotating`. Release notes are written from these, so describe the change from a user's point of view where you can.

Don't commit signing keys, keystore passwords or `google-services.json`.

## AI-assisted contributions

Jiangshi itself is developed with AI assistance (see the README), so AI-assisted pull requests are fine. Please test your change on a real device or emulator and say how you tested it.

## Licence

Contributions are made under the [Apache License 2.0](LICENSE), the same licence as the rest of the project.
