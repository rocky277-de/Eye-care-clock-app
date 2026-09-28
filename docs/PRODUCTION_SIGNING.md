# Production Signing Setup

This project builds a signed APK and Android App Bundle through GitHub Actions.

## Required GitHub Actions secrets

Create these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Never commit the keystore or passwords to the repository.

## Create the keystore on Android/Termux

Run locally in Termux:

```bash
pkg update
pkg install openjdk-17
mkdir -p ~/eyecare-signing
cd ~/eyecare-signing

keytool -genkeypair -v \
  -keystore eyecare-release.jks \
  -alias eyecare \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Choose a strong keystore password and key password and keep them in a secure password manager.

## Convert the keystore to Base64

In Termux:

```bash
base64 -w 0 eyecare-release.jks > eyecare-release.base64.txt
cat eyecare-release.base64.txt
```

Copy that Base64 value directly into the GitHub Actions secret named `KEYSTORE_BASE64`.

Do not paste the Base64 value, keystore password, or key password into ChatGPT.

## Add the secrets on GitHub

Open the repository settings and go to:

**Settings → Secrets and variables → Actions → New repository secret**

Add all four secrets.

## Verify the production build

After the four secrets are configured, run:

**Actions → Production Release Build → Run workflow**

The workflow will build:

- signed `app-release.apk`
- signed `app-release.aab`

It also verifies that both files exist before uploading them as workflow artifacts.

## Important

The release keystore is the app's signing identity. Keep a secure backup of the original `eyecare-release.jks` and its passwords. Losing it can prevent future updates from being signed with the same identity.
