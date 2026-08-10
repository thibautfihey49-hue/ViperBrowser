package com.viperbrowser

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
        private val EXT_VIDEO = listOf(".mp4", ".m3u8", ".webm", ".avi", ".mov", ".mkv", ".flv", ".mpd")
        private val HOSTS_VIDEO = listOf("youtube.com", "vimeo.com", "dailymotion.com", "twitch.tv", "video", "watch", "player")
        private const val PERM_STOCKAGE = 12345
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var dlButton: Button
    private val enChargement = AtomicBoolean(false)
    private var urlVideoDetectee: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(false)
            }

            setContentView(R.layout.activity_main)

            urlInput = findViewById(R.id.urlInput)
            dlButton = findViewById(R.id.dlButton)
            webView = findViewById(R.id.webView)

            urlInput.text.clear()
            urlInput.hint = "Rechercher ou entrer une URL..."

            verifierPermissions()
            configurerWebView()
            configurerBoutons()

            webView.loadUrl("about:blank")

            Log.d(TAG, "✅ PRÊT — Barre vide + Vitesse max + Vidéo OK")

        } catch (e: Exception) {
            Toast.makeText(this, "ERREUR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifierPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERM_STOCKAGE)
            }
        }
    }

    private fun configurerWebView() {
        val r = webView.settings

        // ⚡ VITESSE MAX
        r.cacheMode = WebSettings.LOAD_DEFAULT
        r.domStorageEnabled = true
        r.databaseEnabled = false

        // 🎬 VIDÉOS — RÉGLAGES ESSENTIELS
        r.javaScriptEnabled = true
        r.loadsImagesAutomatically = true
        r.mediaPlaybackRequiresUserGesture = false
        r.javaScriptCanOpenWindowsAutomatically = true
        r.domStorageEnabled = true

        // 📋 COMPATIBILITÉ SITES
        r.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // 📐 MISE EN PAGE
        r.useWideViewPort = true
        r.loadWithOverviewMode = true
        r.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        r.minimumFontSize = 8
        r.defaultFontSize = 16

        // 🔍 ZOOM
        r.setSupportZoom(true)
        r.builtInZoomControls = true
        r.displayZoomControls = false

        // 🔒 SÉCURITÉ + COMPATIBILITÉ
        r.allowFileAccess = false
        r.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            r.setGeolocationEnabled(false)
        }

        // 🎨 ACCÉLÉRATION GRAPHIQUE
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false

        // 🍪 COOKIES
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = ClientWeb()
    }

    private fun configurerBoutons() {
        findViewById<Button>(R.id.goButton).setOnClickListener {
            if (!enChargement.get()) chargerOuRechercher()
        }

        dlButton.setOnClickListener {
            urlVideoDetectee?.let { lancerTelechargement(it) }
                ?: Toast.makeText(this, "Aucune vidéo détectée", Toast.LENGTH_SHORT).show()
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                if (!enChargement.get()) chargerOuRechercher()
                true
            } else false
        }
    }

    private fun chargerOuRechercher() {
        var saisie = urlInput.text.toString().trim()
        if (saisie.isBlank()) return
        saisie = saisie.replace("\\s+".toRegex(), " ")
        val urlFinale = quandEstCeUneUrl(saisie) ?: "${MOTEUR_RECHERCHE}${saisie.replace(" ", "+")}"
        urlVideoDetectee = null
        dlButton.visibility = View.GONE
        webView.loadUrl(urlFinale)
        urlInput.clearFocus()
        val clavier = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        clavier.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    private fun quandEstCeUneUrl(s: String): String? {
        val u = s.trim().lowercase(Locale.ROOT)
        if (u.startsWith("http://") || u.startsWith("https://")) return s
        if (u.startsWith("www.")) return "https://$s"
        val domaines = listOf(".com", ".fr", ".net", ".org", ".io", ".app", ".dev", ".edu", ".gov", ".uk", ".de", ".es", ".it")
        for (ext in domaines) if (u.contains(ext)) return "https://$s"
        if (u.contains(".") && !u.contains(" ")) return "https://$s"
        return null
    }

    private fun lancerTelechargement(url: String) {
        try {
            val uri = Uri.parse(url)
            val nomFichier = URLUtil.guessFileName(url, null, "video/mp4")
            val requete = DownloadManager.Request(uri).apply {
                setMimeType("video/*")
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                setTitle(nomFichier)
                setDescription("Téléchargement ViperBrowser")
                setAllowedOverMetered(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(requete)
            Toast.makeText(this, "⬇ Téléchargement démarré", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ClientWeb : WebViewClient() {
        private val BLOQUER = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "amazon-adsystem.com", "adform.net",
            "adroll.com", "ads.", "ad.", "banner.", "popup.", "affiliate.",
            "google-analytics.com", "analytics.", "gtag.", "ga.js", "gtm.js",
            "beacon.", "tracking.", "track.", "utm_", "gclid=", "fbclid="
        )

        override fun shouldInterceptRequest(v: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val url = rq?.url.toString()
            val urlMin = url.lowercase(Locale.ROOT)

            for (ext in EXT_VIDEO) {
                if (urlMin.contains(ext)) {
                    urlVideoDetectee = url
                    runOnUiThread { dlButton.visibility = View.VISIBLE }
                    break
                }
            }
            if (urlVideoDetectee == null) {
                for (hote in HOSTS_VIDEO) {
                    if (urlMin.contains(hote)) {
                        urlVideoDetectee = url
                        runOnUiThread { dlButton.visibility = View.VISIBLE }
                        break
                    }
                }
            }
            for (pub in BLOQUER) {
                if (urlMin.contains(pub)) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
            }
            return super.shouldInterceptRequest(v, rq)
        }

        override fun onPageStarted(v: WebView?, u: String?, ic: android.graphics.Bitmap?) {
            enChargement.set(true)
            urlVideoDetectee = null
            runOnUiThread { dlButton.visibility = View.GONE }
            u?.let { urlInput.setText(it) }
        }

        override fun onPageFinished(v: WebView?, u: String?) { enChargement.set(false) }

        override fun shouldOverrideUrlLoading(v: WebView?, rq: WebResourceRequest?): Boolean {
            val u = rq?.url.toString()
            if (u.startsWith("http")) v?.loadUrl(u)
            return true
        }
    }

    override fun onBackPressed() {
        if (!enChargement.get() && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() { webView.clearCache(true); webView.destroy(); super.onDestroy() }

    override fun onRequestPermissionsResult(code: Int, liste: Array<out String>, resultat: IntArray) {
        super.onRequestPermissionsResult(code, liste, resultat)
        if (code == PERM_STOCKAGE && resultat.isNotEmpty() && resultat[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "✅ Permissions OK", Toast.LENGTH_SHORT).show()
        }
    }
}
