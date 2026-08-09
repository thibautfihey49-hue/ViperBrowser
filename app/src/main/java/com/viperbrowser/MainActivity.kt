package com.viperbrowser

import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private val enChargement = AtomicBoolean(false)
    private var derniereUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            // ✅ Initialisation WebView EN PREMIER pour Xiaomi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(false)
            }

            setContentView(R.layout.activity_main)

            urlInput = findViewById(R.id.urlInput)
            webView = findViewById(R.id.webView)

            configurerWebView()
            configurerBarreUrl()

            urlInput.setText("")
            webView.loadUrl("about:blank")

            Log.d(TAG, "✅ Prêt — Barre URL optimisée")

        } catch (e: Exception) {
            Toast.makeText(this, "ERREUR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun configurerBarreUrl() {
        val bouton = findViewById<Button>(R.id.goButton)

        // Bouton → charger instantanément
        bouton.setOnClickListener { if (!enChargement.get()) chargerOuRechercher() }

        // Touche Entrée du clavier → charger
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (!enChargement.get()) chargerOuRechercher()
                true
            } else false
        }

        // Touche Entrée physique
        urlInput.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !enChargement.get()) {
                chargerOuRechercher()
                true
            } else false
        }
    }

    // ⚡ DÉTECTION INTELLIGENTE : URL ou RECHERCHE
    private fun chargerOuRechercher() {
        var saisie = urlInput.text.toString().trim()
        if (saisie.isBlank()) return

        // Nettoyage
        saisie = saisie.replace("\\s+".toRegex(), " ")

        val urlFinale = quandEstCeUneUrl(saisie) ?: "${MOTEUR_RECHERCHE}${saisie.replace(" ", "+")}"

        derniereUrl = urlFinale
        webView.loadUrl(urlFinale)

        // Masquer clavier immédiatement
        urlInput.clearFocus()
        val clavier = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        clavier.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    // 🔍 DÉTECTION URL EN 1 MICROSECONDE
    private fun quandEstCeUneUrl(s: String): String? {
        val u = s.trim().lowercase(Locale.ROOT)

        // Cas 1 : Protocole explicite
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("www.")) {
            return if (u.startsWith("www.")) "https://$s" else s
        }

        // Cas 2 : Domaine connu avec extension
        val domaines = listOf(".com", ".fr", ".net", ".org", ".io", ".app", ".dev", ".edu", ".gov", ".uk", ".de", ".es", ".it", ".ca")
        for (ext in domaines) if (u.contains(ext)) return "https://$s"

        // Cas 3 : contient un point sans espace = probablement une URL
        if (u.contains(".") && !u.contains(" ")) return "https://$s"

        return null // → Recherche
    }

    private fun configurerWebView() {
        val r = webView.settings

        // ⚡ VITESSE MAX
        r.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        r.domStorageEnabled = true
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

        // 🔒 SÉCURITÉ
        r.allowFileAccess = false
        r.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) r.setGeolocationEnabled(false)

        // 🎨 RENDU FLUIDE
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = ClientWeb()
    }

    override fun onBackPressed() {
        if (!enChargement.get() && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() { webView.clearCache(true); webView.destroy(); super.onDestroy() }

    private inner class ClientWeb : WebViewClient() {
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
