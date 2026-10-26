# Why a WebView shell?

## The problem

The official Apple Music app for Android TV needs Google Play services. On China-market Android TVs (for example a Sony BRAVIA China model) there is no `com.google.android.gms`, and the app hangs at the splash screen. Only the Play Store shell and a few Google TV pieces exist, with no working services behind them.

## Why the usual stand-ins don't work

Replacements such as microG need signature spoofing, which means a rooted device or a patched ROM. On a locked retail TV that is not a safe route, so there is no practical no-root way to make the official app run.

## Why a WebView shell works

Apple's web player at `music.apple.com` needs only a modern browser engine and DRM. A WebView shell uses just two things the TV already has:

- the system WebView (a current Chromium build), and
- Widevine DRM for protected playback.

It never calls Google services, so the missing `gms` doesn't matter.

## Why a free cursor, not focus-jumping

The web UI was designed for a mouse or touch. Moving focus between elements with the arrow keys jumps around unpredictably on those pages. A free mouse-style cursor driven by the D-pad behaves the way the page expects, so it is the default. Focus mode stays available on the Menu key.

## Checking another service on a similar TV

Before building anything for another streaming service, test its web player first:

1. Check the TV has a current WebView: `adb shell dumpsys webviewupdate` (look at the current package and version).
2. Check Widevine is present, for example by opening a Widevine test page in any WebView-based browser on the TV.
3. Open the service's web player in a WebView and see whether it loads, signs in and plays a track or video.
4. If the page works but the controls are awkward, a small shell like this one can add a cursor, key mapping and layout tweaks. If the site blocks the user agent or needs a higher Widevine security level than the TV offers, a shell will not help.
