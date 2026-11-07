# AI Conference

A conference-call-style Android app that puts **ChatGPT, Gemini, Claude, Grok, and your own camera** on one screen — up to six tiles — with no API keys. Each AI tile is that service's own web app running in an embedded WebView; your camera/mic tile is the fifth participant. Built for OnePlus / OxygenOS, works on stock Android too.

## How it works

- **One-activity grid (primary mode).** Android does not allow embedding other apps' windows, so the grid is built from WebViews inside a single activity. This also means the OxygenOS flexible-window limit never applies to the grid itself.
- **Pop-out (focus mode).** Any AI tile can be popped out into its own task, which OxygenOS can show as a flexible/freeform window. Stock OxygenOS renders only one flexible window, so popping out a second tile automatically returns the first to the grid. The WebView is *reparented*, not reloaded — the session and any ongoing voice chat continue.
- **Native apps.** The add-participant sheet can also launch the installed ChatGPT/Gemini/Claude/Grok app in a freeform window (they can never appear inside the grid — OS limitation).
- **Audio.** All tiles play into the shared app audio session, so every participant is heard. The microphone follows a "talking to" rule: only the one tile you select may capture audio (tap the voice icon on a tile), preventing four voice modes from hearing each other through the speaker. A global mic mute and speaker/earpiece toggle live in the bottom bar. Mute/unmute is done by wrapping `getUserMedia` with an injected script — no page reloads.

## Building

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and the Android SDK (platform 35).

## On-device setup (OnePlus / OxygenOS)

1. Grant camera, microphone, and notification permissions on first launch.
2. Sign in once per service. On a ChatGPT, Claude, or Grok tile, tap **Sign in** — the page opens full screen, so a password manager can fill the email login and a copied email link can be pasted back in. Gemini only accepts Google, which refuses in-app browsers: tap **Open in Chrome** (the Google account already in Chrome) or **Open Gemini app**. Sessions stay on the phone.
3. Optional, for multiple simultaneous pop-out windows: enable Developer options → *Enable freeform windows*, or run `adb shell settings put global enable_freeform_support 1` and reboot. Without it, pop-out works as a single-window focus mode.
4. Recommended: exclude the app from battery optimization so OxygenOS doesn't kill the session in the background (a foreground service with camera/mic/media types is used, but OxygenOS is aggressive).

## Known limitations

- **Gemini** cannot complete Google sign-in inside a tile. Google blocks that page in every in-app browser. Open in Chrome, or open the Gemini app.
- **ChatGPT, Claude, and Grok** sign in with email. “Continue with Google” on those pages is refused inside the tile; the sign-in screen stays on the email form. A magic-link email that opens in another app can be pasted back, or the code from that email typed into the page.
- **Cloudflare challenges** can appear on claude.ai / grok.com; solve them manually in the tile.
- **Voice crosstalk:** with speaker output, an AI's open mic would hear the other AIs — hence the single-open-mic rule. Headphones remove the issue entirely.
- **Memory:** four live WebViews plus the camera is heavy; mute/remove tiles you aren't using.
- File uploads inside tiles (attachment pickers) are not wired up yet.
