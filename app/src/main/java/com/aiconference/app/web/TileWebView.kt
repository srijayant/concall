package com.aiconference.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconference.app.tiles.Tile

/**
 * Creates and configures the WebView backing one AI tile.
 *
 * Navigation never leaves the tile. The WebView is disguised as the device's
 * real Chrome (UA + client hints) so email/password login works. Google's
 * "Sign in with Google" button is still blocked by Google policy in any
 * WebView — we detect that page and the UI offers Back + email instead.
 */
object TileWebViewFactory {

    interface MicArbiter {
        fun mayCaptureAudio(tileId: String): Boolean
        fun onPageReady(tileId: String)
    }

    const val INIT_SCRIPT = """
        (function() {
          if (window.__aiconfInit) return;
          window.__aiconfInit = true;

          try {
            Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; } });
          } catch (e) {}
          window.chrome = window.chrome || { runtime: {}, app: {}, csi: function() {}, loadTimes: function() {} };

          window.__aiconfStreams = [];
          window.__aiconfOutputOn = true;
          if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
            var orig = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
            navigator.mediaDevices.getUserMedia = function(c) {
              return orig(c).then(function(s) {
                window.__aiconfStreams.push(s);
                if (window.__aiconfMicOn === false) {
                  s.getAudioTracks().forEach(function(t) { t.enabled = false; });
                }
                return s;
              });
            };
          }
          window.__aiconfSetMic = function(on) {
            window.__aiconfMicOn = on;
            (window.__aiconfStreams || []).forEach(function(s) {
              s.getAudioTracks().forEach(function(t) { t.enabled = on; });
            });
          };
          function applyOutput(root) {
            var els = root.querySelectorAll ? root.querySelectorAll('audio,video') : [];
            for (var i = 0; i < els.length; i++) els[i].muted = !window.__aiconfOutputOn;
          }
          window.__aiconfSetOutput = function(on) {
            window.__aiconfOutputOn = on;
            applyOutput(document);
          };
          new MutationObserver(function() {
            if (!window.__aiconfOutputOn) applyOutput(document);
          }).observe(document.documentElement, { childList: true, subtree: true });

          window.open = function(url) {
            if (url) window.location.href = url;
            return window;
          };
          document.addEventListener('click', function(e) {
            var a = e.target && e.target.closest ? e.target.closest('a') : null;
            if (a && a.target === '_blank') a.target = '_self';
          }, true);
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun create(appContext: Context, tile: Tile, arbiter: MicArbiter): WebView {
        val service = requireNotNull(tile.service) { "Only web tiles own a WebView" }
        val webView = WebView(MutableContextWrapper(appContext))

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                androidx.webkit.ProfileStore.getInstance()
                    .getOrCreateProfile("tile-${service.name}")
                    .let { WebViewCompat.setProfile(webView, it.name) }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        applyChromeIdentity(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, INIT_SCRIPT, setOf("*"))
            }
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                intercept(view, Uri.parse(url), tile)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                intercept(view, request.url, tile)

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(INIT_SCRIPT, null)
                tile.loginBlocked = isBlockedLoginUrl(url)
                arbiter.onPageReady(tile.id)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val ctx = webView.context
                val granted = request.resources.filter { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED &&
                                arbiter.mayCaptureAudio(tile.id)
                        else -> false
                    }
                }
                if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as WebView.WebViewTransport
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        intercept(view, request.url, tile)
                        return true
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.setDownloadListener { _, _, _, _, _ -> }

        webView.loadUrl(service.url)
        return webView
    }

    /**
     * Look like the phone's real Chrome, not a WebView and not a fake
     * Windows desktop. Google's "browser may not be secure" triggers hard
     * on UA mismatch (our previous desktop UA) and on the "; wv" token.
     */
    private fun applyChromeIdentity(webView: WebView) {
        val defaultUa = WebSettings.getDefaultUserAgent(webView.context)
        val ua = defaultUa
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
        webView.settings.userAgentString = ua

        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val chrome = Regex("Chrome/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: "131.0.0.0"
            fun brand(name: String, full: String) = UserAgentMetadata.BrandVersion.Builder()
                .setBrand(name)
                .setMajorVersion(full.substringBefore('.'))
                .setFullVersion(full)
                .build()
            runCatching {
                WebSettingsCompat.setUserAgentMetadata(
                    webView.settings,
                    UserAgentMetadata.Builder()
                        .setBrandVersionList(
                            listOf(
                                brand("Google Chrome", chrome),
                                brand("Chromium", chrome),
                                brand("Not_A Brand", "8.0.0.0"),
                            )
                        )
                        .setFullVersion(chrome)
                        .setPlatform("Android")
                        .setPlatformVersion(Build.VERSION.RELEASE ?: "14")
                        .setArchitecture("arm")
                        .setModel(Build.MODEL.orEmpty())
                        .setMobile(true)
                        .setBitness(64)
                        .setWow64(false)
                        .build(),
                )
            }
        }
    }

    private fun intercept(view: WebView, uri: Uri, tile: Tile): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        when (scheme) {
            "http", "https" -> {
                tile.loginBlocked = isBlockedLoginUrl(uri.toString())
                return false
            }
            "about", "javascript", "blob", "data" -> return false
            "intent" -> {
                rewriteIntent(uri.toString())?.let { view.loadUrl(it) }
                return true
            }
            "android-app" -> {
                rewriteAndroidApp(uri)?.let { view.loadUrl(it) }
                return true
            }
            else -> return true
        }
    }

    fun isBlockedLoginUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "accounts.google.com" ||
            host == "accounts.youtube.com" ||
            host.endsWith(".google.com") && url.contains("signin", ignoreCase = true) ||
            host == "appleid.apple.com" ||
            url.contains("disallowed_useragent", ignoreCase = true) ||
            url.contains("signin/rejected", ignoreCase = true)
    }

    private fun rewriteIntent(url: String): String? = runCatching {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        intent.getStringExtra("browser_fallback_url")
            ?: intent.getStringExtra("S.browser_fallback_url")
            ?: intent.data?.let { data ->
                when (data.scheme?.lowercase()) {
                    "http", "https" -> data.toString()
                    else -> data.buildUpon().scheme("https").build().toString()
                }
            }
    }.getOrNull()

    private fun rewriteAndroidApp(uri: Uri): String? {
        val segs = uri.pathSegments
        if (segs.size >= 2 && segs[0] in listOf("http", "https")) {
            return "${segs[0]}://${segs.drop(1).joinToString("/")}"
        }
        return null
    }

    fun setMicEnabled(webView: WebView, enabled: Boolean) {
        webView.evaluateJavascript("window.__aiconfSetMic && window.__aiconfSetMic($enabled);", null)
    }

    fun setOutputEnabled(webView: WebView, enabled: Boolean) {
        webView.evaluateJavascript("window.__aiconfSetOutput && window.__aiconfSetOutput($enabled);", null)
    }
}
