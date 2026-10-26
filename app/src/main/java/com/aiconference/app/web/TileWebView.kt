package com.aiconference.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconference.app.tiles.Tile

/**
 * Creates and configures the WebView backing one AI tile.
 *
 * Navigation never leaves the tile: OxygenOS/Chrome "open in app" banners,
 * intent:// deep links, and target=_blank popups are all trapped here so
 * ChatGPT/Gemini/Claude/Grok stay in their conference blocks.
 */
object TileWebViewFactory {

    /** Decides, at grant time, whether a tile may capture the microphone. */
    interface MicArbiter {
        fun mayCaptureAudio(tileId: String): Boolean
        fun onPageReady(tileId: String)
    }

    /**
     * Desktop Chrome UA. Mobile UAs make these sites push an "Open in app"
     * interstitial that launches the system browser (and then 404s). Desktop
     * UA gets the real web app, which is what we want inside a tile.
     */
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val INIT_SCRIPT = """
        (function() {
          if (window.__aiconfInit) return;
          window.__aiconfInit = true;
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

          // Keep every link and window.open inside this tile.
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
            // Same-tile only: a true "new window" is what OxygenOS turns into Chrome.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = DESKTOP_UA
        }

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
                intercept(view, Uri.parse(url))

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                intercept(view, request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(INIT_SCRIPT, null)
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

            // Belt and suspenders if a page still tries to spawn a window.
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
                        intercept(view, request.url)
                        return true
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }

        // Don't let a download prompt bounce into Chrome either.
        webView.setDownloadListener { _, _, _, _, _ -> }

        webView.loadUrl(service.url)
        return webView
    }

    /**
     * @return true if the WebView should NOT do its default thing (which on
     * OxygenOS is often "open Chrome"). http(s) stays in-tile by returning
     * false; everything else is swallowed or rewritten to https.
     */
    private fun intercept(view: WebView, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        when (scheme) {
            "http", "https" -> return false
            "about", "javascript", "blob", "data" -> return false
            "intent" -> {
                rewriteIntent(uri.toString())?.let { view.loadUrl(it) }
                return true
            }
            "android-app" -> {
                rewriteAndroidApp(uri)?.let { view.loadUrl(it) }
                return true
            }
            else -> return true // market, chatgpt, googlechrome, etc. — stay in tile
        }
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
        // android-app://package/https/host/path
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
