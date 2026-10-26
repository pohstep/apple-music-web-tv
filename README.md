# Apple Music Web (TV)

A small Android TV app that opens the Apple Music web player (`music.apple.com`) in a full-screen WebView, with a remote-friendly mouse-style cursor and a dim "play mode" now-playing screen.

> **Unofficial.** Not affiliated with, endorsed by, or sponsored by Apple. "Apple Music" is a trademark of Apple Inc.

## Who it is for

Android TVs that cannot run the official Apple Music app because they have no Google Play services, for example China-market sets such as Sony BRAVIA models without `com.google.android.gms`. See [docs/WHY-A-WEBVIEW-SHELL.md](docs/WHY-A-WEBVIEW-SHELL.md) for the reasoning.

Requirements: Android 8.0+ (API 26), system WebView, Widevine DRM, an Apple Music subscription, and adb access to the TV.

## Install

Download `apple-music-web-2.2.2.apk` and its `.sha256` from the [Releases](../../releases) page, check the hash, then:

```
sha256sum -c apple-music-web-2.2.2.apk.sha256
adb connect <TV-IP>:5555
adb install apple-music-web-2.2.2.apk
```

Open "Apple Music Web" from the TV's app list and sign in on Apple's own page.

## Controls

| Remote key | Action |
|---|---|
| D-pad | Move the free cursor (default mode); hold to speed up |
| OK | Tap / click under the cursor |
| Menu (short press) | Switch between cursor mode and focus mode (arrows jump between links) |
| Menu (long press) | Reload the page |
| Back | Go back |
| Back (double press or long press) | Exit the app |
| Info / Yellow / Guide / Record, or the on-screen ♫ button | Toggle play mode |
| Play / Pause / Stop, Next / Fast-forward, Previous / Rewind | Media controls |

## Play mode

A dim now-playing screen for background listening: album art, title, artist, progress bar and a clock on a dark background instead of a bright web page. Music keeps playing. Back, the arrow keys or OK leave play mode.

Lyrics are best-effort: they appear only if Apple's page exposes timed lyrics for the current song.

## Background playback and control (v2.2+)

- **Media session:** the app publishes a real Android media session, so the system now-playing UI (and launchers that read active media sessions) can show the current song and send previous / play-pause / next.
- **Foreground service:** a media-playback service keeps music playing when you leave the app.
- **adb control broadcast:** `adb shell am broadcast -a dev.pohstep.applemusicweb.CONTROL -n dev.pohstep.applemusicweb/.ControlReceiver --es cmd next` (cmd: `play`, `pause`, `toggle`, `next`, `prev`).

## Changelog

- **2.2.2** - Next and previous keep playing instead of leaving the player paused.
- **2.2.1** - Fixes the next/previous buttons: they are now found by class, so they work in a non-English Apple Music UI.
- **2.2** - Media session, foreground playback service, adb control broadcast.
- **2.1** - Cursor mode as the default, sign-in popups in a child WebView.

## Build from source

Needs JDK 17 and the Android SDK (build-tools 34.0.0, platform android-31).

```
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

The script creates a local `debug.keystore` on first run (it is git-ignored; do not commit it), builds, signs and verifies `apple-music-web-2.2.2.apk`, and writes its `.sha256`. To make the keystore yourself:

```
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

A build with your own keystore will have a different hash and signature from the release APK, and can't be installed over it without uninstalling first.

## Disclaimer

- Unofficial and not affiliated with Apple.
- You sign in on Apple's own web page. The app does not read, store or transmit your credentials; it only keeps the normal WebView cookies/session on the TV.
- An Apple Music subscription is required.
- Playback depends on the TV supporting Widevine DRM; if your TV doesn't, the web player won't play.
- Provided as is, with no warranty. Use at your own risk.

## Licence

MIT, see [LICENSE](LICENSE).
