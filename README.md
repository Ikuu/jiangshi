# Jiangshi

A personal manga reader for Android, forked from [Yōkai](https://github.com/null2264/yokai).

Jiangshi tracks Yōkai closely and keeps its database migrations, so a Yōkai backup restores cleanly. The changes on top of Yōkai are small and aimed at personal use.

## Changes from Yōkai

* **Per-source reading mode**: set a default reading mode for each source in its extension settings. Sources without one use the global default.
* **No telemetry**: Firebase, Crashlytics and analytics are removed from the build, along with the crash report toggle. Builds don't need a `google-services.json`.
* **No in-app updater**: Yōkai's updater would offer Yōkai releases, so it's disabled in every flavor.
* **Suwayomi tracking**: works with newer Suwayomi servers that leave `meta` out of REST responses.
* **Tracker User-Agent**: trackers see requests as coming from `Ikuu/jiangshi`, not Yōkai.
* **Rotation in double page mode**: rotating the device no longer jumps back a page, including when the current page becomes part of a spread.
* **Branding**: new name, icons and application IDs (`.jiangshi`, `.debugJiangshi`, `.nightlyJiangshi`), so it installs alongside Yōkai.
* **Leaner build**: arm64-v8a only, one APK named `jiangshi-v<version>-arm64-v8a.apk`.
* **Trimmed About screen**: Yōkai's community links and changelog sheet are removed. The version line shows which Yōkai release the build is based on.

Everything else, including the reader, trackers, library, backups and extensions, comes from Yōkai, J2K and Tachiyomi/Mihon.

Tracker logins for MyAnimeList, AniList, Shikimori and Bangumi still use Yōkai's OAuth apps and the `yokai://` callback. If Yōkai is installed too, Android asks which app should finish the login; pick Jiangshi.

*Requires Android 8.0 or higher.*

## Building

```sh
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # signed release build
```

Release builds are signed with a key configured in `~/.gradle/gradle.properties`. Keep these values out of the repo:

```properties
JIANGSHI_STORE_FILE=/path/to/keystore.jks
JIANGSHI_STORE_PASSWORD=...
JIANGSHI_KEY_ALIAS=...
JIANGSHI_KEY_PASSWORD=...
```

If these aren't set, the release build is left unsigned.

## Releasing

Pushing a tag like `v0.1.2` runs `.github/workflows/release.yml`. It builds a signed release APK and attaches it to a GitHub release. The tag has to match `jiangshiVersion`, so bump that first.

The workflow needs these repository secrets:

* `JIANGSHI_STORE_BASE64`: the keystore, base64-encoded (`base64 -w0 keystore.jks`)
* `JIANGSHI_STORE_PASSWORD`, `JIANGSHI_KEY_ALIAS`, `JIANGSHI_KEY_PASSWORD`

The R8 mapping is kept as a workflow artifact for deobfuscating stack traces. Pull requests get a build and unit test run from `build_check.yml`.

## Versioning

`versionName` is Jiangshi's own version (`jiangshiVersion` in `app/build.gradle.kts`). `versionCode` follows Yōkai so its migrations keep running after an upgrade.

## Syncing with Yōkai

This repo is a GitHub fork of Yōkai. Jiangshi's changes live on `main`, and `master` is Yōkai's branch.

```sh
git remote add upstream https://github.com/null2264/yokai.git   # once
git fetch upstream
git merge upstream/master
```

Yōkai's release and mirror workflows, Renovate config, funding links and issue templates are deleted on `main`. If a merge reports a modify/delete conflict on one of them, keep it deleted with `git rm <file>`. Those workflows still exist on `master`, so keep them disabled in the repo's Actions settings so they don't run when `master` is pushed.

## Disclaimer

The developer(s) of this application have no affiliation with the content providers available, and this application hosts zero content.

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 null2264

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
