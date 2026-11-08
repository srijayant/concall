package com.aiconference.app.web

import com.aiconference.app.tiles.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUrlsTest {

    @Test
    fun hostAndPathIgnoreQueryAndWww() {
        assertEquals("chatgpt.com", LoginUrls.hostOf("https://www.chatgpt.com/auth/login?next=/"))
        assertEquals("/auth/login", LoginUrls.pathOf("https://www.chatgpt.com/auth/login?next=/"))
        assertEquals("/", LoginUrls.pathOf("https://chatgpt.com"))
    }

    @Test
    fun googleAndAppleSignInAreBlocked() {
        assertTrue(LoginUrls.isBlocked("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(LoginUrls.isBlocked("https://accounts.google.com/signin/rejected"))
        assertTrue(LoginUrls.isBlocked("https://appleid.apple.com/auth/authorize"))
        assertTrue(LoginUrls.isBlocked("https://chatgpt.com/auth/login?error=disallowed_useragent"))
        assertFalse(LoginUrls.isBlocked("https://gemini.google.com/app"))
        assertFalse(LoginUrls.isBlocked("https://claude.ai/login"))
    }

    @Test
    fun emailLoginPagesStayInTheSignInScreen() {
        assertTrue(LoginUrls.isLoginPage(AiService.CHATGPT, "https://chatgpt.com/auth/login"))
        assertTrue(LoginUrls.isLoginPage(AiService.CHATGPT, "https://auth.openai.com/log-in"))
        assertTrue(LoginUrls.isLoginPage(AiService.CLAUDE, "https://claude.ai/login"))
        assertTrue(LoginUrls.isLoginPage(AiService.GROK, "https://accounts.x.ai/sign-in?redirect=grok-com"))
        assertFalse(LoginUrls.isLoginPage(AiService.CHATGPT, "https://chatgpt.com/"))
        assertFalse(LoginUrls.isLoginPage(AiService.CLAUDE, "https://claude.ai/new"))
    }

    @Test
    fun signedInAppIsTheProductNotTheLoginWall() {
        assertTrue(LoginUrls.isSignedInApp(AiService.CHATGPT, "https://chatgpt.com/"))
        assertTrue(LoginUrls.isSignedInApp(AiService.CHATGPT, "https://chatgpt.com/c/abc"))
        assertFalse(LoginUrls.isSignedInApp(AiService.CHATGPT, "https://chatgpt.com/auth/login"))
        assertTrue(LoginUrls.isSignedInApp(AiService.CLAUDE, "https://claude.ai/new"))
        assertFalse(LoginUrls.isSignedInApp(AiService.CLAUDE, "https://claude.ai/"))
        assertTrue(LoginUrls.isSignedInApp(AiService.GROK, "https://grok.com/"))
        assertFalse(LoginUrls.isSignedInApp(AiService.GROK, "https://accounts.x.ai/sign-in"))
        assertTrue(LoginUrls.isSignedInApp(AiService.GEMINI, "https://gemini.google.com/app"))
    }

    @Test
    fun returnToGridOnlyAfterLoginWasActuallySeen() {
        assertFalse(
            LoginUrls.shouldReturnToGrid(true, false, AiService.CHATGPT, "https://chatgpt.com/")
        )
        assertTrue(
            LoginUrls.shouldReturnToGrid(true, true, AiService.CHATGPT, "https://chatgpt.com/")
        )
        assertFalse(
            LoginUrls.shouldReturnToGrid(true, true, AiService.CHATGPT, "https://chatgpt.com/auth/login")
        )
    }

    @Test
    fun pastedLinkMustBeHttpsOnThatService() {
        assertTrue(
            LoginUrls.pasteAllowed(
                AiService.CLAUDE,
                "Sign in: https://claude.ai/magic-link/abc).",
            )
        )
        assertEquals(
            "https://claude.ai/magic-link/abc",
            LoginUrls.extractHttpsUrl("Sign in: https://claude.ai/magic-link/abc)."),
        )
        assertFalse(LoginUrls.pasteAllowed(AiService.CLAUDE, "http://claude.ai/magic-link/abc"))
        assertFalse(LoginUrls.pasteAllowed(AiService.CLAUDE, "https://notclaude.ai/login"))
        assertFalse(LoginUrls.pasteAllowed(AiService.CHATGPT, "https://evil.com/chatgpt.com/auth"))
        assertTrue(LoginUrls.pasteAllowed(AiService.CHATGPT, "https://auth.openai.com/u/click?t=1"))
    }
}
