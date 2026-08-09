package com.viperbrowser

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)

        configurerWebViewUltraRapide()
        configurerBouton()

        urlInput.setText("google.com")
        chargerUrl("https://www.google.com")
    }

    private fun configurerWebViewUltraRapide() {
        webView.settings.apply {
            // ⚡ VITESSE MAXIMUM
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // ⚡ OPTIMISATIONS MÉMOIRE ET CHARGEMENT
            allowFileAccess = false
            allowContentAccess = false
            databaseEnabled = false
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        }

        // ⚡ ACCÉLÉRATION MATÉRIELLE OBLIGATOIRE
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isDrawingCacheEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(vue: WebView?, url: String?) {
                url?.let { urlInput.setText(it) }
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
        if (!urlFinal.startsWith("http://") && !urlFinal.startsWith("https://")) {
            urlFinal = "https://$urlFinal"
        }
        webView.loadUrl(urlFinal)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
}
