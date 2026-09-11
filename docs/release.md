# Releasing to Google Play

The app is **AMlog**, `nl.schellenberg.amlog` on Play. Note that the Kotlin package is
still `nl.part66l.logbook` — `namespace` and `applicationId` are independent, and only the
latter is what Google identifies the app by. This covers getting a build onto a
Play testing track.

## The one thing that will break if you skip it

Drive sync authenticates with `Identity.getAuthorizationClient()` and an
`AuthorizationRequest`. There is no client ID in the app and no `google-services.json` —
Google identifies the app by **package name plus the SHA-1 fingerprint of the certificate
it was signed with**. Today that fingerprint is your *debug* keystore's, which is why Drive
works when you run from Android Studio.

A release build is signed with a different key, so its SHA-1 is different, so Google does
not recognise it, so **Drive sync fails** — and it fails the same way the missing-scope bug
did, which makes it easy to misdiagnose.

It is worse than one extra fingerprint, because Play re-signs. With Play App Signing
(mandatory for new apps) you upload a bundle signed with your *upload* key, and Google
strips that signature and re-signs with an *app signing* key it holds. Testers install
something signed with a certificate you never see until after the first upload.

So three fingerprints end up needing to be registered as Android OAuth clients in Google
Cloud Console for `nl.schellenberg.amlog` — the applicationId, not the Kotlin package:

1. **Debug** — so Android Studio builds keep working.
2. **Upload** — from your own keystore.
3. **Play app signing** — from Play Console → Test and release → Setup → App integrity,
   available only *after* the first bundle is uploaded.

Which means the first test build will have broken Drive sync until you go back and add (3).
That is expected, not a bug. Everything else in the app works offline regardless.

Get (1) and (2) with:

```
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
keytool -list -v -alias upload -keystore upload-keystore.jks
```

## Creating the upload key

Once only. Choose your own passwords; nothing here should end up in the repo.

```
keytool -genkey -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.example` to `keystore.properties` and fill in the passwords.
Both files are gitignored.

**Back up the .jks and its passwords somewhere you will still have in ten years.** Lose
them and you cannot ship an update to this listing, ever — the only remedy is a new
listing with a new package name, and existing installs never see it again. Play App
Signing does let you reset a lost *upload* key, but only while you still control the
Play Console account.

## Building

```
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Upload that, not an APK.
`versionCode` in `app/build.gradle.kts` must increase on every upload; `versionName` is
what testers see.

R8 is deliberately off — see the comment in `app/build.gradle.kts`.

## Before the first upload

- **Privacy policy.** Play requires a public URL for any app that touches camera or an
  account. Ours is unusual in a good way: the app has no backend and no analytics, and the
  only data leaving the device goes to the user's own Drive.
- **Data safety form.** Declare the camera photos and the Drive account email. Both stay
  on-device or in the user's own Drive.
- **OAuth consent screen.** With `drive.file`, `drive.appdata` and `userinfo.email`, check
  the current sensitive/restricted classification before submitting — an app in testing
  with a small user list can run unverified, but each tester must be added explicitly and
  will see an "unverified app" warning.
- **`android:allowBackup`.** See the note in `AndroidManifest.xml`.
