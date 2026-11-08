package com.aiconference.app.web

import com.aiconference.app.tiles.AiService

/**
 * Decides which pages are a sign-in wall, which ones mean the account is in,
 * and which pasted links are safe to open inside a tile.
 *
 * Google's sign-in page is refused in every WebView. ChatGPT, Claude, and
 * Grok still accept email on their own login pages, so those are the path
 * the sign-in screen opens.
 */
object LoginUrls {

    private val httpsUrl = Regex("""https://\S+""")

    fun hostOf(url: String): String? {
        val rest = url.substringAfter("://", missingDelimiterValue = "")
        if (rest.isEmpty()) return null
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        val hostPort = if (authority.contains('@')) authority.substringAfterLast('@') else authority
        return hostPort.substringBefore(':').removePrefix("www.").lowercase().takeIf { it.isNotEmpty() }
    }

    fun pathOf(url: String): String {
        val rest = url.substringAfter("://", missingDelimiterValue = "")
        val raw = rest.substringAfter('/', missingDelimiterValue = "")
        val path = raw.substringBefore('?').substringBefore('#')
        return if (path.isEmpty()) "/" else "/$path"
    }

    /** Google and Apple refuse to authenticate inside an in-app WebView. */
    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false
        val host = hostOf(url) ?: return false
        return host == "accounts.google.com" ||
            host == "accounts.youtube.com" ||
            (host.endsWith(".google.com") && url.contains("signin", ignoreCase = true)) ||
            host == "appleid.apple.com" ||
            url.contains("disallowed_useragent", ignoreCase = true) ||
            url.contains("signin/rejected", ignoreCase = true)
    }

    fun isLoginPage(service: AiService, url: String): Boolean {
        if (isBlocked(url)) return true
        val host = hostOf(url) ?: return false
        val path = pathOf(url)
        return when (service) {
            AiService.CHATGPT ->
                host == "auth.openai.com" ||
                    ((host == "chatgpt.com" || host == "chat.openai.com") && path.startsWith("/auth"))
            AiService.CLAUDE ->
                host == "claude.ai" && (path.startsWith("/login") || path.startsWith("/magic"))
            AiService.GROK ->
                host == "accounts.x.ai" ||
                    (host == "grok.com" && (path.startsWith("/sign-in") || path.startsWith("/login")))
            AiService.GEMINI -> false
        }
    }

    /** The product itself, after the login wall. Bare marketing homepages stay out. */
    fun isSignedInApp(service: AiService, url: String): Boolean {
        if (url.isBlank() || isBlocked(url) || isLoginPage(service, url)) return false
        val host = hostOf(url) ?: return false
        val path = pathOf(url)
        return when (service) {
            AiService.CHATGPT ->
                (host == "chatgpt.com" || host == "chat.openai.com") &&
                    (path == "/" || path.startsWith("/c/") || path.startsWith("/g/") || path.startsWith("/a/"))
            AiService.CLAUDE ->
                host == "claude.ai" && (
                    path.startsWith("/new") ||
                        path.startsWith("/chat") ||
                        path.startsWith("/recents") ||
                        path.startsWith("/project")
                    )
            AiService.GROK -> host == "grok.com" && path == "/"
            AiService.GEMINI -> host == "gemini.google.com" && !path.startsWith("/signin")
        }
    }

    fun shouldReturnToGrid(signingIn: Boolean, sawLogin: Boolean, service: AiService, url: String): Boolean =
        signingIn && sawLogin && isSignedInApp(service, url)

    /** Pull the first https link out of a copied email, dropping trailing punctuation. */
    fun extractHttpsUrl(text: String): String? {
        val raw = httpsUrl.find(text.trim())?.value ?: return null
        return raw.trimEnd('.', ',', ';', ')', ']', '>', '"', '\'')
    }

    fun pasteAllowed(service: AiService, text: String): Boolean {
        val url = extractHttpsUrl(text) ?: return false
        val host = hostOf(url) ?: return false
        return service.signInLinkHosts.any { host == it || host.endsWith(".$it") }
    }
}
