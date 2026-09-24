# TeleRC direct updates

The Setup & Config page has a manual Check for updates button. It checks the latest **public** GitHub release for `Denberg28/TeleRC`, compares numeric version tags, selects a `TeleRC-v*.apk` asset with a SHA-256 digest, downloads it with Android DownloadManager, checks the digest and package/version, then opens Android's installer. Installation requires the user's confirmation and may require granting TeleRC permission to install unknown apps. No token or secret is packaged in the app.

## One-time signing setup

Android accepts an APK as an update only if its application ID and signing certificate match the installed app. GitHub runner debug keys do not provide continuity. Create a dedicated keystore **outside Git** and back it up securely. The four repository Actions secrets expected by `.github/workflows/release.yml` are:

| Secret | Value |
| --- | --- |
| `TELERC_KEYSTORE_BASE64` | Base64 of the `.jks` keystore bytes, on one line |
| `TELERC_KEYSTORE_PASSWORD` | Keystore password |
| `TELERC_KEY_ALIAS` | Key alias |
| `TELERC_KEY_PASSWORD` | Key password |

Example key creation on a trusted workstation:

```bash
keytool -genkeypair -v -keystore telerc-release.jks -alias telerc -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 telerc-release.jks
```

Store the keystore and passwords in a secure backup. Never commit them or send them through chat. `GITHUB_TOKEN` is provided by Actions for publishing; no personal access token is needed. Add the four secrets in the repository's Settings → Secrets and variables → Actions page. After source review and an approved public release, manually run **Signed APK release** in Actions. Its version comes from `app/build.gradle.kts` and it publishes an APK as a public GitHub release. This is a manual workflow to avoid accidental publication.

The existing v0.3.x builds are signed with ephemeral debug keys. Install the first stable-key release after uninstalling the debug build; future signed releases can update it in place. Back up any settings you need before uninstalling. Keep `applicationId` and signing key unchanged for future releases.

## Limits

The checker needs internet access, and a public GitHub release must exist. GitHub Actions build artifacts are not public releases and expire. A private release cannot be fetched safely by embedding a GitHub secret in the APK; it needs a separate authenticated delivery service. The downloaded APK is checked against GitHub's SHA-256 digest, its package name, and version code. Android's installer enforces signing certificate continuity.
