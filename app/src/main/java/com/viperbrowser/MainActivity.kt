package com.viperbrowser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
import android.widget.ProgressBar
import android.widget.Toast
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
        private const val LISTE_BLOCAGE = "https://raw.githubusercontent.com/thibautfihey49-hue/ViperBrowser/main/blocklist.txt"

        // ==============================================
        // 🛡️ BLOCAGE — RÈGLES LOCALES
        // ==============================================
        private val REGLES_BLOCAGE = setOf(
            // PUBS VIDÉO
            "/vast/", "/vpaid/", "/ima/", "/preroll/", "/midroll/", "/postroll/",
            "vast.", "vpaid.", "ima.", "preroll.", "adTagUrl", "ad_tag_url",
            "pre-roll", "post-roll", "mid-roll", "ad_manifest", "ad_manager",

            // DOMAINES PUBS N°1
            "teads.", "doubleclick.", "googlesyndication.", "googleadservices.",
            "google-analytics.", "googletagmanager.", "googletagservices.",
            "spotx.", "freewheel.", "brid.", "adcolony.", "smartadserver.",
            "criteo.", "taboola.", "outbrain.", "pubmatic.", "openx.",
            "rubiconproject.", "adform.", "adroll.", "adnxs.", "amazon-adsystem.",
            "media.net", "adthrive.", "casalemedia.", "sharethrough.", "admixer.",
            "telaria.", "tremorvideo.", "xandr.", "zemanta.", "zedo.",

            // ❌ DOMAINE DE PUBS DÉTECTÉ
            "livejasmin.", "crmrc.livejasmin.",

            // SCRIPTS PUBS
            "adsbygoogle.js", "pagead2.", "show_ads.js", "adframe.js",
            "/ad.", "/ads.", "/advert.", "/banner.", "/popup.", "/sponsor.",
            "/admanager.js", "/ads.js", "/vast.js", "/vpaid.js", "/ima.js",

            // TRACKING
            "/analytics.", "/track.", "/beacon.", "/pixel.", "/gtag.js", "/ga.js",
            "hotjar.", "mouseflow.", "fullstory.", "mixpanel.", "segment.",
            "clarity.ms", "crashlytics.", "bugsnag.", "logrocket.", "posthog.",

            // PARAMÈTRES URL
            "ad_id=", "ad_url=", "ad_click=", "ad_zone=", "client=ca-",
            "pagead=", "google_ad", "fb_event=", "analytics_id=", "utm_source="
        )

        // 🎬 FORMATS VIDÉO SEULEMENT — PRÉCIS
        private val EXTENSIONS_VIDEO = setOf(
            ".mp4", ".webm", ".m3u8", ".m4v", ".mov", ".avi", ".mkv", ".flv", ".ts"
        )

        // 🎬 MOTIFS SITES DE VIDÉO
        private val MOTIFS_SITE_VIDEO = listOf(
            "youtube.com/watch", "youtu.be/", "vimeo.com/", "dailymotion.com/",
            "videoplayback", "googlevideo", "/video/", "/watch/", "/embed/",
            "cdn.bilibili.com", "stream/video", "video-file", "media-video"
        )

        // ❌ FORMATS À IGNORER (PAS DES VIDÉOS)
        private val EXTENSIONS_A_IGNORER = setOf(
            ".gif", ".jpg", ".jpeg", ".png", ".webp", ".css", ".js", ".html", ".htm"
        )
    }

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private val reglesActives = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)

        reglesActives.addAll(REGLES_BLOCAGE)
        Log.d(TAG, "🛡️ Règles locales chargées : ${reglesActives.size}")

        chargerListeDistante()
        configurerWebView()
        configurerBoutons()
        webView.loadUrl("https://www.google.com")
    }

    private fun chargerListeDistante() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lignes = URL(LISTE_BLOCAGE).openStream().bufferedReader().readLines()
                var ajoutees = 0
                for (ligne in lignes) {
                    val r = ligne.trim().lowercase(Locale.ROOT)
                    if (r.isNotEmpty() && !r.startsWith("#") && !r.startsWith("!") && r.length > 2) {
                        if (reglesActives.add(r)) ajoutees++
                    }
                }
                Log.d(TAG, "✅ Liste distante : +$ajoutees règles — Total : ${reglesActives.size}")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Liste distante indisponible — règles locales uniquement")
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
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(wv: WebView?, req: WebResourceRequest): WebResourceResponse? {
                val url = req.url.toString().lowercase(Locale.ROOT)
                val hote = req.url.host?.lowercase(Locale.ROOT) ?: ""

                // 🛡️ BLOCAGE PUBLICITÉS
                if (estBloque(hote, url)) {
                    Log.d(TAG, "🚫 BLOQUÉ : ${req.url.host}")
                    return WebResourceResponse("text/plain", "utf-8",
                        ByteArrayInputStream(byteArrayOf()))
                }

                // 🎬 DÉTECTION VIDÉO — UNIQUEMENT SI VRAI FORMAT VIDÉO
                if (estVideo(url)) {
                    Log.d(TAG, "🎬 VIDÉO DÉTECTÉE : $url")
                    proposerTelechargement(url)
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
        for (r in reglesActives) {
            if (hote.contains(r) || url.contains(r) || hote.endsWith(".$r")) {
                return true
            }
        }
        return false
    }

    private fun estVideo(url: String): Boolean {
        val u = url.lowercase(Locale.ROOT)

        // ❌ EXCLURE LES IMAGES ET FICHIERS NON VIDÉO
        for (ext in EXTENSIONS_A_IGNORER) {
            if (u.endsWith(ext) || u.contains("$ext?")) return false
        }

        // ✅ VÉRIFIER LES EXTENSIONS VIDÉO
        for (ext in EXTENSIONS_VIDEO) {
            if (u.endsWith(ext) || u.contains("$ext?")) return true
        }

        // ✅ VÉRIFIER LES SITES DE VIDÉO
        for (motif in MOTIFS_SITE_VIDEO) {
            if (u.contains(motif)) return true
        }

        return false
    }

    private fun proposerTelechargement(url: String) {
        val nomFichier = URLUtil.guessFileName(url, null, null)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@MainActivity, "🎬 Vidéo détectée — $nomFichier", Toast.LENGTH_LONG).show()
            lancerTelechargement(url, nomFichier)
        }
    }

    private fun lancerTelechargement(url: String, nomFichier: String) {
        try {
            val uri = Uri.parse(url)
            val req = DownloadManager.Request(uri).apply {
                setTitle("Vidéo")
                setDescription(nomFichier)
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier)
                allowScanningByMediaScanner()
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(req)
            Toast.makeText(this, "✅ Téléchargement lancé", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erreur téléchargement", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erreur DL", e)
        }
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
