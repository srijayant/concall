# Architecture

One Android process. One grid. No server.

```
MainActivity
  ConferenceScreen          Compose UI: grid, sign-in, add sheet
  TileManager               tile list, WebView pool, which mic is live
    TileWebViewFactory      one WebView per AI tile
    CameraTile              CameraX preview, not a WebView
  AudioController           speaker vs earpiece for the mixed output
  CallGridService           foreground service so OxygenOS does not kill the call
  PopOutActivity            reparents one WebView into its own task
  ChromeSignIn              Custom Tab for Gemini
```

Package `com.aiconference.app`. `minSdk` 26, `targetSdk` 35. Version is in `app/build.gradle.kts`.

## Grid

`ConferenceApp` creates one `TileManager` for the process. On startup the manager adds the camera tile and one web tile per `AiService` entry: ChatGPT, Gemini, Claude, Grok. The cap is six tiles (`TileManager.MAX_TILES`).

A tile is either `WEB` or `CAMERA`. Web tiles never hold a second WebView. `TileManager.webViews` creates the view on first use and keeps it across recomposition, rotation (`configChanges` is set), and pop-out.

`WebViewHost` moves that view between parents. The view was built with a `MutableContextWrapper`, so the base context swaps to the current activity and back to the application when detached. That is what lets a Claude or ChatGPT session keep its scroll position and voice chat when it pops out.

`PopOutActivity` is a separate task (`documentLaunchMode=always`, its own affinity). Stock OxygenOS shows one flexible window, so opening a second pop-out finishes the first. The grid cell then shows "bring back".

Native ChatGPT / Gemini / Claude / Grok apps are launched beside the grid, never inside it. `FreeformLauncher` asks for freeform bounds and `LAUNCH_ADJACENT`. OxygenOS usually opens them full screen anyway. The user turns that into a floating window from Recents.

## WebView

`TileWebViewFactory` enables JavaScript, DOM storage, third-party cookies, and autofill. Each service gets its own WebView profile when `MULTI_PROFILE` exists (`tile-CHATGPT`, and so on), so their cookies do not collide.

The first URL is `emailLoginUrl` when the service has one, otherwise `url`. A saved session redirects into the product. Gemini has no email URL, so it loads `https://gemini.google.com/app`.

Navigation stays in the tile. `http`/`https` load normally. `intent:` and `android-app:` are rewritten to an https fallback or dropped. `window.open` and `target=_blank` are forced into the same view. Downloads are ignored. File uploads are not implemented.

Main-frame navigations to Google or Apple sign-in are cancelled. The email form underneath stays. `LoginUrls` decides three things: blocked sign-in, login wall, and signed-in app. The grid shows **Sign in** on a login wall. That flag reparents the same WebView into a full-screen page. After a login wall has been seen, a later signed-in URL returns to the grid. The return is posted, not done inside `onPageFinished`.

## Audio

Every tile plays into the app's audio session, so the others are heard together.

The microphone is exclusive. `TileManager.activeMicTileId` is the only tile allowed to capture. An injected script wraps `getUserMedia` and toggles track `enabled`, and mutes `<audio>` / `<video>` for output mute. That avoids reloading the page. `WebChromeClient.onPermissionRequest` also denies audio capture when this tile is not the active one.

`AudioController` only picks the output device: built-in speaker, or clear it for the earpiece. It does not mute a tile.

## Process lifetime

`CallGridService` is a foreground service of type camera, microphone, and media playback, limited to the permissions actually granted. It exists because OxygenOS kills a background WebView that is playing or listening. The notification brings `MainActivity` back.

Camera preview is CameraX in `CameraTile`, front camera by default. It is a participant tile, not the AI's camera.

## What is not here

No accounts of our own, no tokens, no Firebase, no websocket between the AIs. They do not see each other except through the room speaker, which is why only one mic is open. Chrome's cookie jar is not the WebView's cookie jar. Signing Gemini in with Chrome does not sign the Gemini tile in.
