package com.viperbrowser

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.HashSet
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
        // 🔗 URL de ta liste — CORRIGÉE AVEC BON NOM DE COMPTE
        private const val LISTE_BLOCAGE = "https://raw.githubusercontent.com/thibautfihey49-hue/ViperBrowser/main/blocklist.txt"
        
        // 🔴 RÈGLES DE SECOURS (si liste distante indisponible)
        private val SECOURS = setOf(
            "teads.", "doubleclick.", "googlesyndication.", "googleadservices.",
            "/vast/", "/vpaid/", "/ima/", "/preroll/", "/ad.", "/ads.",
            "google-analytics.", "googletagmanager.", "spotx.", "freewheel.",
            "criteo.", "taboola.", "outbrain.", "pubmatic.", "openx.",
            "adform.", "adroll.", "adnxs.", "amazon-adsystem.", "pagead2."
        )
    }

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private val reglesBlocage = HashSet<String>()
    private val listeChargee = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)

        chargerListe()
        configurerWebView()
        configurerBoutons()
        webView.loadUrl("https://www.google.com")
    }

    private fun chargerListe() {
        // D'abord charger les règles de secours
        reglesBlocage.addAll(SECOURS)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(LISTE_BLOCAGE)
                val lignes = url.openStream().bufferedReader().readLines()
                for (ligne in lignes) {
                    val r = ligne.trim().lowercase(Locale.ROOT)
                    if (r.isNotEmpty() && !r.startsWith("#") && !r.startsWith("!") && r.length > 2) {
                        reglesBlocage.add(r)
                    }
                }
                listeChargee.set(true)
                Log.d(TAG, "✅ Liste chargée : ${reglesBlocage.size} règles")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Liste distante indisponible — secours uniquement")
                listeChargee.set(true)
            }
        }
    }

    private fun configurerWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(wv: WebView?, req: WebResourceRequest): WebResourceResponse? {
                val url = req.url.toString().lowercase(Locale.ROOT)
                val hote = req.url.host?.lowercase(Locale.ROOT) ?: ""

                if (estBloque(hote, url)) {
                    Log.d(TAG, "🚫 BLOQUÉ : ${req.url.host}")
                    return WebResourceResponse("text/plain", "utf-8",
                        ByteArrayInputStream(byteArrayOf()))
                }
                return null
            }

            override fun onPageStarted(wv: WebView?, url: String?, icon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
                urlBar.setText(url)
            }

            override fun onPageFinished(wv: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun estBloque(hote: String, url: String): Boolean {
        for (r in reglesBlocage) {
            if (hote.contains(r) || url.contains(r) || hote.endsWith(".$r")) {
                return true
            }
        }
        return false
    }

    private fun configurerBoutons() {
        findViewById<Button>(R.id.btnGo).setOnClickListener { chargerUrl() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<Button>(R.id.btnForward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { webView.reload() }
        urlBar.setOnEditorActionListener { _, _, _ -> chargerUrl(); true }
    }

    private fun chargerUrl() {
        var url = urlBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http")) {
            url = if (url.contains(".")) "https://$url" else "$MOTEUR_RECHERCHE$url"
        }
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
