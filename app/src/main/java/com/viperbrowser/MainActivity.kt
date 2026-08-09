package com.viperbrowser

import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val isCharging = AtomicBoolean(false)

    // 🚫 LISTE COMPLÈTE : PUBLICITÉ + PISTAGE + ANALYTIQUE + SOCIAL
    private val HOSTS_BLOQUES = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google",
        "amazon-adsystem.com", "adform.net", "adroll.com", "quantserve.com",
        "scorecardresearch.com", "chartbeat.com", "hotjar.com", "mouseflow.com",
        "segment.io", "segment.com", "mixpanel.com", "amplitude.com",
        "analytics.google.com", "google-analytics.com", "ga.", "gtag.",
        "facebook.com/tr", "connect.facebook.net", "pixel.", "fbcdn.net",
        "twitter.com/i/ads", "ads.x.com", "linkedin.com/ads",
        "beacon.", "tracking.", "track.", "stat.", "pixel.", "metrics.",
        "telemetry.", "crashlytics.", "firebase-config.", "app-measurement.",
        "csi.gstatic.com", "clients4.google.com", "translate.googleapis.com"
    )

    private val SCRIPTS_NETTOYAGE = """
        (function(){
            'use strict';
            // Supprimer TOUS les scripts pistage
            const motifs = ['analytics','gtag','ga(', 'fbq','pixel','beacon','track','stat','ad','adsbygoogle'];
            document.querySelectorAll('script, img, iframe, link').forEach(e => {
                const src = e.src || e.href || '';
                if (motifs.some(m => src.includes(m) || e.innerText?.includes(m))) e.remove();
            });
            // Désactiver événements pistage
            delete window.ga; delete window.gtag; delete window.fbq;
            // Supprimer les cookies tiers
            document.cookie = '*=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=' + location.hostname;
        })();
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)

        configurerVITESSE_MAX()
        configurerSECURITE_ABSOLUE()
        configurerBouton()

        urlInput.setText("google.com")
        chargerUrl("https://www.google.com")
    }

    // ⚡ VITESSE MAXIMALE — TOUS LES PARAMÈTRES
    private fun configurerVITESSE_MAX() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            minimumFontSize = 12
            minimumLogicalFontSize = 12

            allowFileAccess = false
            allowContentAccess = false
            databaseEnabled = false
            geolocationEnabled = false
            setNeedInitialFocus(false)
            savePassword = false
            setAllowUniversalAccessFromFileURLs(false)
            setAllowFileAccessFromFileURLs(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                safeBrowsingEnabled = true
            }
        }

        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isDrawingCacheEnabled = true
        webView.setInitialScale(100)
        webView.fastScrollEnabled = true
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
    }

    // 🔒 SÉCURITÉ ABSOLUE — BLOQUAGE + NETTOYAGE + ISOLATION
    private fun configurerSECURITE_ABSOLUE() {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptThirdPartyCookies(webView, false)
        cookies.acceptCookie() = true
        cookies.removeAllCookies(null) { }
        cookies.flush()

        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        webView.clearSslPreferences()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(vue: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
                val url = rq?.url.toString().lowercase()
                // 🚫 BLOQUER TOUT CE QUI EST DANS LA LISTE
                for (hote in HOSTS_BLOQUES) {
                    if (url.contains(hote)) {
                        return WebResourceResponse("text/plain", "UTF-8",
                            ByteArrayInputStream("".toByteArray()))
                    }
                }
                return super.shouldInterceptRequest(vue, rq)
            }

            override fun shouldOverrideUrlLoading(vue: WebView?, rq: WebResourceRequest?): Boolean {
                val url = rq?.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    vue?.loadUrl(url)
                    isCharging.set(true)
                }
                return true
            }

            override fun onPageStarted(vue: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { urlInput.setText(it) }
                super.onPageStarted(vue, url, favicon)
            }

            override fun onPageFinished(vue: WebView?, url: String?) {
                isCharging.set(false)
                // 🔒 NETTOYER LA PAGE APRÈS CHARGEMENT
                handler.postDelayed({ vue?.evaluateJavascript(SCRIPTS_NETTOYAGE, null) }, 150)
                super.onPageFinished(vue, url)
            }

            override fun onReceivedError(vue: WebView?, code: Int, desc: String?, url: String?) {
                super.onReceivedError(vue, code, desc, url)
            }
        }
    }

    private fun configurerBouton() {
        findViewById<Button>(R.id.goButton).setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) chargerUrl(url)
        }
        urlInput.setOnKeyListener { _, code, _ ->
            if (code == KeyEvent.KEYCODE_ENTER) {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) chargerUrl(url)
                true
            } else false
        }
    }

    private fun chargerUrl(url: String) {
        var urlFinal = url
        if (!URLUtil.isValidUrl(urlFinal)) {
            urlFinal = "https://${urlFinal.replace(" ", "+")}"
        }
        webView.loadUrl(urlFinal)
    }

    override fun onBackPressed() {
        if (isCharging.get()) return
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onPause() {
        webView.onPause()
        webView.clearCache(false)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null) { }
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
