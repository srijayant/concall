package com.aiconference.app.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconference.app.tiles.Tile

/**
 * Creates and configures the WebView backing one AI tile.
 *
 * Key behaviors:
 *  - Mic/camera reach the page through [WebChromeClient.onPermissionRequest].
 *    Audio capture is only granted to the tile currently selected as the
 *    "talking to" target (see [MicArbiter]), so AI voice modes don't all
 *    listen at once and feed back into each other.
 *  - A document-start script wraps `getUserMedia` so we can mute/unmute the
 *    page's mic tracks and media output with JS, without reloading.
 *  - The default WebView UA token ("; wv") is removed so Google sign-in
 *    doesn't reject the view with disallowed_useragent.
 */
object TileWebViewFactory {

    /** Decides, at grant time, whether a tile may open the microphone. */
    interface MicArbiter {
        fun mayCaptureAudio(tileId: String): Boolean
        fun onPageReady(tileId: String)
    }

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
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun create(appContext: Context, tile: Tile, arbiter: MicArbiter): WebView {
        val service = requireNotNull(tile.service) { "Only web tiles own a WebView" }
        // MutableContextWrapper lets the same WebView be re-hosted by the main
        // grid activity or the pop-out activity without leaking either.
        val webView = WebView(MutableContextWrapper(appContext))

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        // Give each tile its own cookie/storage profile where supported, so
        // e.g. two Google accounts or parallel sessions don't collide.
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
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // Strip the WebView markers; Google sign-in blocks UAs containing "; wv".
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, INIT_SCRIPT, setOf("*"))
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = false // keep navigation (incl. OAuth redirects) inside the tile

            override fun onPageFinished(view: WebView, url: String?) {
                // Fallback injection when DOCUMENT_START_SCRIPT is unavailable,
                // and re-assert mute state after navigations.
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

            // Google sign-in opens a popup window; route it back into this tile.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message,
            ): Boolean {
                val transport = resultMsg.obj as WebView.WebViewTransport
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        view.loadUrl(request.url.toString())
                        temp.destroy()
                        return true
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.loadUrl(service.url)
        return webView
    }

    fun setMicEnabled(webView: WebView, enabled: Boolean) {
        webView.evaluateJavascript("window.__aiconfSetMic && window.__aiconfSetMic($enabled);", null)
    }

    fun setOutputEnabled(webView: WebView, enabled: Boolean) {
        webView.evaluateJavascript("window.__aiconfSetOutput && window.__aiconfSetOutput($enabled);", null)
    }
}
