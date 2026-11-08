# Agent guide

AI Conference is an Android app. It shows ChatGPT, Gemini, Claude, Grok, and the phone camera as tiles in one grid. There is no backend and no API key. Each AI tile is that service's own website in a WebView.

Read [architecture.md](architecture.md) and [learnings.md](learnings.md) before changing behavior. [claud.md](claud.md) is the Claude tile.

## Build and test

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

JDK 17+ and Android SDK platform 35. The debug apk lands at `app/build/outputs/apk/debug/app-debug.apk`. `local.properties` is gitignored. Do not commit it.

Login URL rules live in `app/src/main/java/com/aiconference/app/web/LoginUrls.kt` and are covered by `app/src/test/java/com/aiconference/app/web/LoginUrlsTest.kt`. Extend those tests when a host or path changes.

## Shipping a build the phone can install

GitHub's file page hangs on a phone. It malware-scans the apk inside the zip and the download never finishes. A raw file URL does download.

When the app binary changes:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Rebuild, then replace `dist/ai-conference-debug.zip` with a zip whose only entry is `ai-conference-debug.apk`.
3. Put a direct link in the README, not "open the file on GitHub". `raw.githubusercontent.com` works. A Gofile page is the backup when that host is blocked.
4. Do not commit a bare `.apk`. That scan hangs even harder.

## Rules that are easy to get wrong

- Do not try to beat Google's WebView sign-in block. Stripping `wv`, hiding `X-Requested-With`, and inventing `window.chrome` were already tried. Google still refuses the page. Send Gemini through Chrome Custom Tabs or the Gemini app. Keep ChatGPT, Claude, and Grok on their email pages.
- Do not put another app inside a tile. Android will not embed a foreign window. `FreeformLauncher` can only ask for a freeform window, and OxygenOS usually ignores that and opens full screen.
- Do not add API keys, a proxy, or a login server. The product is the services' own sites.
- One microphone at a time. `TileManager` is the arbiter. Output mute is injected JavaScript, not a reload.
- The WebView for a tile is one object, pooled in `TileManager` and reparented by `WebViewHost`. Do not create a second WebView to "refresh" a session.
- Do not destroy or detach a WebView inside `onPageFinished`. Post that work.
- Call `CookieManager.flush()` when sign-in ends and in `MainActivity.onPause`. Without the flush, a process kill drops the login.
- A pasted sign-in link may only load on that service's own hosts (`AiService.signInLinkHosts`).
- File attachments inside tiles are not wired. The download listener is intentionally empty. Do not half-enable either without a real picker and a real save path.

## Where to edit

| Change | File |
| --- | --- |
| Which site a tile opens, native package, paste hosts | `tiles/AiService.kt` |
| Login vs signed-in URL rules | `web/LoginUrls.kt` |
| WebView, Google block, autofill, cookies | `web/TileWebView.kt` |
| Sign-in screen and the grid | `ui/ConferenceScreen.kt` |
| Open Chrome or the native app | `web/ChromeSignIn.kt`, `ui/MainActivity.kt` |
| Mic, tile list, paste a link | `tiles/TileManager.kt` |
| Speaker route | `audio/AudioController.kt` |
| Pop-out window | `popout/PopOutActivity.kt`, `popout/FreeformLauncher.kt` |
| Keep the session alive | `service/CallGridService.kt` |
