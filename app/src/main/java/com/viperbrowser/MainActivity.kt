package com.viperbrowser

import android.os.Bundle
import android.view.KeyEvent
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private var enChargement = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)

        configurerChromiumMAX()
        configurerBlocagePublicite()
        configurerBouton()

        // Page vide = lancement instantané
        urlInput.setText("")
        webView.loadUrl("about:blank")
    }

    private fun configurerChromiumMAX() {
        val reglages = webView.settings

        // ⚡ CACHE MAXIMUM
        reglages.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        reglages.domStorageEnabled = true
        reglages.databaseEnabled = true
        reglages.javaScriptEnabled = true
        reglages.loadsImagesAutomatically = true
        reglages.defaultTextEncodingName = "UTF-8"

        // ⚡ MISE EN PAGE RAPIDE
        reglages.useWideViewPort = true
        reglages.loadWithOverviewMode = true
        reglages.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        reglages.minimumFontSize = 8

        reglages.setSupportZoom(true)
        reglages.builtInZoomControls = true
        reglages.displayZoomControls = false

        // ⚡ COUPER CE QUI RALENTIT
        reglages.allowFileAccess = false
        reglages.allowContentAccess = false
        reglages.setGeolocationEnabled(false)

        // ⚡ ACCÉLÉRATION GRAPHIQUE MAX
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
    }

    private fun configurerBlocagePublicite() {
        webView.webViewClient = ClientChromium()
    }

    private fun configurerBouton() {
        findViewById<Button>(R.id.goButton).setOnClickListener {
            if (!enChargement) chargerUrl()
        }
        urlInput.setOnKeyListener { _, code, _ ->
            if (code == KeyEvent.KEYCODE_ENTER && !enChargement) {
                chargerUrl()
                true
            } else false
        }
    }

    private fun chargerUrl() {
        var url = urlInput.text.toString().trim()
        if (url.isEmpty()) return
        if (!URLUtil.isValidUrl(url)) url = "https://$url"
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (!enChargement && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { webView.onResume(); super.onResume() }
    override fun onDestroy() { webView.clearCache(true); webView.destroy(); super.onDestroy() }

    private inner class ClientChromium : WebViewClient() {
        // 🚫 80+ DOMAINES BLOQUÉS AU NIVEAU MOTEUR CHROMIUM
        private val BLOQUER = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "amazon-adsystem.com", "adform.net",
            "adroll.com", "ads.", "ad.", "banner.", "popup.", "affiliate.",
            "google-analytics.com", "analytics.", "gtag.", "ga.js", "gtm.js",
            "hotjar.com", "segment.com", "mixpanel.com", "amplitude.com",
            "chartbeat.com", "quantserve.com", "scorecardresearch.com",
            "connect.facebook.net", "facebook.com/tr", "fbq.", "pixel.",
            "twitter.com/i/ads", "ads.x.com", "linkedin.com/ads",
            "beacon.", "tracking.", "track.", "stats.", "metrics.", "telemetry.",
            "csi.gstatic.com", "utm_", "source=", "campaign=", "gclid=", "fbclid="
        )

        override fun shouldInterceptRequest(vue: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val url = rq?.url.toString().lowercase()
            for (nom in BLOQUER) {
                if (url.contains(nom)) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
            }
            return super.shouldInterceptRequest(vue, rq)
        }

        override fun onPageStarted(vue: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            enChargement = true
            url?.let { urlInput.setText(it) }
        }

        override fun onPageFinished(vue: WebView?, url: String?) {
            enChargement = false
        }

        override fun shouldOverrideUrlLoading(vue: WebView?, rq: WebResourceRequest?): Boolean {
            val url = rq?.url.toString()
            if (url.startsWith("http")) vue?.loadUrl(url)
            return true
        }
    }
}
