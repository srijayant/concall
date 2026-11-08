# Learnings

These are the failures already paid for. Do not repeat them.

## The phone cannot download the build from the GitHub file page

A `.apk` committed to the repo sits in GitHub's malware scan. On a phone that scan hangs and the download never starts. Wrapping the apk in `dist/ai-conference-debug.zip` was necessary and not sufficient: opening that zip from the repository file view still hangs, because the zip contains an apk.

What does download:

- `https://raw.githubusercontent.com/.../dist/ai-conference-debug.zip` returns `application/zip` directly. Confirmed for 0.1.3, 18,028,487 bytes, zip magic `PK`.
- A Gofile share page also works on the phone. The 0.1.2 build was installed from one. The 0.1.3 page is linked from the README.

Tell the user the direct URL. Do not tell them to open the file on github.com.

## Google sign-in does not work in a tile

Since 2021 Google refuses OAuth in any embedded WebView (`disallowed_useragent`). This app already:

- identified as the phone's real Chrome and removed `; wv`
- stopped sending `X-Requested-With`
- set client hints
- injected a fake `window.chrome`

Google still blocks the page. A desktop Windows user-agent made the warning worse, not better. Stop tweaking the identity. Cancel the navigation to `accounts.google.com` and `appleid.apple.com`, and leave the email form on screen.

Auth Tab and Custom Tabs cannot hand the session back. Those APIs return the redirect URL only for a scheme or host we own, verified with Digital Asset Links. We do not own chatgpt.com, claude.ai, grok.com, or google.com. Changing `redirect_uri` fails Google's client check. Reading Chrome's cookies from the app is not possible, and should not be attempted.

Gemini has no email login. The working path is a Chrome Custom Tab, where the Google account is already signed in, or the installed Gemini app (`com.google.android.apps.bard`). That session stays in Chrome or in the Gemini app. It does not appear in the tile.

## Email login has to be large, and the link has to come back

Typing a password in a quarter-screen tile fails in practice. The **Sign in** control reparents the same WebView to full screen so autofill can attach. Autofill does not reliably fire in the small cell.

Claude's mail link, opened from Gmail, lands in Chrome. Claude then shows a code for the original browser. The sign-in screen tells the user to type the code, and **Paste sign-in link** loads an `https` URL only if the host is on that service's allow list. Anything else is rejected.

## Cookies die if they are not flushed

WebView cookies are not guaranteed to hit disk when the process is killed. `CookieManager.flush()` runs when sign-in finishes and in `onPause`. Skipping it looks like "I logged in and it forgot".

## OxygenOS will not put another app in the grid

The grid is WebViews in one activity on purpose. That sidesteps the one-flexible-window limit, which only applies to real other windows. Pop-out of our own WebView works as a single focus window. Launching the ChatGPT or Gemini app requests freeform and is usually ignored. The recovery is Recents → long-press → Floating window. A long dialog before the launch made that path feel harder. The sign-in button launches immediately and uses a toast for the Recents hint.

## Voice

Four open mics plus the speaker means each model hears the others. Only the selected tile may capture. Headphones are the real fix for crosstalk. Output mute is a DOM mute, not `AudioManager` per tile, because the tiles share one audio session.

## Other traps

- Detaching the WebView inside `onPageFinished` crashes or blanks the tile. Post the return-to-grid.
- Four live WebViews plus CameraX is enough to get the process killed. The foreground service is required, and tiles the user is not using should be removable.
- claude.ai and grok.com sometimes serve a Cloudflare challenge. The person solves it by hand.
- `setDownloadListener` is empty and file choosers are not attached. Attachment buttons in the sites do nothing. Wiring them is a real feature, not a one-line flag.
