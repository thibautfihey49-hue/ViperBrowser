package com.viperbrowser

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)

        configurerWebView()
        configurerSecurite()
        configurerBouton()

        urlInput.setText("google.com")
        webView.loadUrl("https://www.google.com")
    }

    private fun configurerWebView() {
        val reglages = webView.settings
        reglages.javaScriptEnabled = true
        reglages.domStorageEnabled = true
        reglages.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        reglages.loadsImagesAutomatically = true
        reglages.setSupportZoom(true)
        reglages.builtInZoomControls = true
        reglages.displayZoomControls = false
        reglages.useWideViewPort = true
        reglages.loadWithOverviewMode = true
        reglages.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        reglages.allowFileAccess = false
        reglages.allowContentAccess = false
        reglages.databaseEnabled = false
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
    }

    private fun configurerSecurite() {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = MonClientWeb()
    }

    private fun configurerBouton() {
        val bouton = findViewById<Button>(R.id.goButton)
        bouton.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            webView.loadUrl(url)
        }
        urlInput.setOnKeyListener { _, code, _ ->
            if (code == KeyEvent.KEYCODE_ENTER) {
                var url = urlInput.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                webView.loadUrl(url)
                true
            } else {
                false
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.clearCache(true)
        webView.clearHistory()
        super.onDestroy()
    }

    private inner class MonClientWeb : WebViewClient() {
        private val BLOQUER = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "google-analytics.com", "hotjar.com",
            "segment.com", "mixpanel.com", "pixel.", "beacon.", "tracking."
        )

        override fun shouldInterceptRequest(vue: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val url = rq?.url.toString().lowercase()
            for (nom in BLOQUER) {
                if (url.contains(nom)) {
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
            }
            return true
        }

        override fun onPageFinished(vue: WebView?, url: String?) {
            if (url != null) {
                urlInput.setText(url)
            }
        }
    }
}
