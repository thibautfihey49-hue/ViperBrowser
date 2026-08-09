package com.viperbrowser

import android.os.Bundle
import android.os.Build
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private val enChargement = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)
        configurerMAX()
        configurerBouton()
        urlInput.setText("")
        webView.loadUrl("about:blank")
    }

    private fun configurerMAX() {
        val r = webView.settings
        r.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        r.domStorageEnabled = true
        r.databaseEnabled = false
        r.javaScriptEnabled = true
        r.loadsImagesAutomatically = true
        r.defaultTextEncodingName = "UTF-8"
        r.useWideViewPort = true
        r.loadWithOverviewMode = true
        r.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        r.minimumFontSize = 8
        r.setSupportZoom(true)
        r.builtInZoomControls = true
        r.displayZoomControls = false
        r.allowFileAccess = false
        r.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) r.setGeolocationEnabled(false)
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))
            WebSettingsCompat.setForceDark(r, WebSettingsCompat.FORCE_DARK_OFF)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = Client()
    }

    private fun configurerBouton() {
        findViewById<Button>(R.id.goButton).setOnClickListener { if (!enChargement.get()) chargerUrl() }
        urlInput.setOnKeyListener { _, c, _ ->
            if (c == android.view.KeyEvent.KEYCODE_ENTER && !enChargement.get()) { chargerUrl(); true } else false
        }
    }

    private fun chargerUrl() {
        var u = urlInput.text.toString().trim()
        if (u.isEmpty()) return
        if (!URLUtil.isValidUrl(u)) u = "https://$u"
        webView.loadUrl(u)
    }

    override fun onBackPressed() {
        if (!enChargement.get() && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() { webView.clearCache(true); webView.destroy(); super.onDestroy() }

    private inner class Client : WebViewClient() {
        private val BLOQUER = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "amazon-adsystem.com", "adform.net",
            "adroll.com", "ads.", "ad.", "banner.", "popup.", "affiliate.",
            "google-analytics.com", "analytics.", "gtag.", "ga.js", "gtm.js",
            "hotjar.com", "segment.com", "mixpanel.com", "amplitude.com",
            "chartbeat.com", "quantserve.com", "scorecardresearch.com",
            "connect.facebook.net", "fbq.", "pixel.", "ads.x.com",
            "beacon.", "tracking.", "track.", "stats.", "metrics.", "telemetry.",
            "utm_", "source=", "campaign=", "gclid=", "fbclid="
        )
        override fun shouldInterceptRequest(v: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val u = rq?.url.toString().lowercase()
            for (h in BLOQUER) if (u.contains(h))
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            return super.shouldInterceptRequest(v, rq)
        }
        override fun onPageStarted(v: WebView?, u: String?, ic: android.graphics.Bitmap?) {
            enChargement.set(true); u?.let { urlInput.setText(it) }
        }
        override fun onPageFinished(v: WebView?, u: String?) { enChargement.set(false) }
        override fun shouldOverrideUrlLoading(v: WebView?, rq: WebResourceRequest?): Boolean {
            val u = rq?.url.toString()
            if (u.startsWith("http")) v?.loadUrl(u)
            return true
        }
    }
}
