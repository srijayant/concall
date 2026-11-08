# Claude

Two different things share this name. The tile is the Claude website inside the app. A coding session in this repo should follow [agent.md](agent.md) and the lessons in [learnings.md](learnings.md).

## The Claude tile

| | |
| --- | --- |
| App after login | `https://claude.ai/new` |
| Email sign-in | `https://claude.ai/login` |
| Paste is allowed on | `claude.ai`, `anthropic.com` |
| Native app | `com.anthropic.claude` |
| Accent | `#D97757` |

`AiService.CLAUDE` holds those values. `LoginUrls` treats `/login` and `/magic` as the login wall, and `/new`, `/chat`, `/recents`, and `/project` as the signed-in app. The bare `https://claude.ai/` home is not treated as signed in, because a logged-out visit often stops there before redirecting.

Claude's email login sends a message from `@mail.anthropic.com`.

- If the link opens in this same WebView, the tile signs in.
- If the link opens in Gmail or Chrome, Claude treats that as another browser and shows a code. Type the code on the sign-in page that is still open here.
- Or copy the link and tap **Paste sign-in link**. The tile only navigates when the host is `claude.ai` or `anthropic.com`.

"Continue with Google" on claude.ai is refused inside the tile, same as every other in-app browser. The sign-in screen stays on the email form. Do not route Claude through a Google account inside the WebView.

claude.ai sometimes shows a Cloudflare check. The person has to solve it in the tile. There is no bypass in this app.

## A Claude coding session

- Change behavior in the files listed in [agent.md](agent.md). URL policy belongs in `LoginUrls`, with a unit test.
- Do not spoof the WebView into looking like Chrome so Google will accept a login. That already failed.
- Do not add an API key for Claude. This app does not call the Anthropic API. The tile is the website.
- The sign-in screen's hint for Claude is the code line. ChatGPT and Grok get the password-manager line. Keep that split.
- Pop-out reparents the same WebView. A Claude voice chat should survive the move. Do not reload on pop-out.
