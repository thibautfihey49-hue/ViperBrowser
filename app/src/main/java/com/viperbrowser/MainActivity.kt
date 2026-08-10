package com.viperbrowser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
        private const val LISTE_BLOCAGE = "https://raw.githubusercontent.com/thibautfihey49-hue/ViperBrowser/main/blocklist.txt"

        // ==============================================
        // 🛡️ BLOCAGE — RÈGLES TOUJOURS ACTIVES (pas besoin de liste distante)
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

        // 🎬 MOTIFS DÉTECTION VIDÉO
        private val MOTIFS_VIDEO = listOf(
            "video/", "mp4", "webm", "m3u8", "blob:http", ".mp4", ".webm", ".m3u8",
            "/video/", "/watch/", "/embed/", "videoplayback", "googlevideo",
            "youtube.com/watch", "youtu.be/", "vimeo.com/", "dailymotion.com/",
            "cdn.bilibili.com", "video-file", "media-video", "stream/video"
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

        // ✅ Charge d'abord les règles locales GARANTIES
        reglesActives.addAll(REGLES_BLOCAGE)
        Log.d(TAG, "🛡️ Règles locales chargées : ${reglesActives.size}")

        // 🚀 Tente de charger la liste complète en plus
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
                Log.w(TAG, "⚠️ Liste distante indisponible — utilisation règles locales uniquement")
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

                // 🎬 DÉTECTION VIDÉO
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
        for (motif in MOTIFS_VIDEO) {
            if (u.contains(motif)) return true
        }
        return false
    }

    private fun proposerTelechargement(url: String) {
        val nomFichier = URLUtil.guessFileName(url, null, null)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@MainActivity, "🎬 Vidéo détectée — Téléchargement : $nomFichier", Toast.LENGTH_LONG).show()
            lancerTelechargement(url, nomFichier)
        }
    }

    private fun lancerTelechargement(url: String, nomFichier: String) {
        try {
            val uri = Uri.parse(url)
            val req = DownloadManager.Request(uri).apply {
                setTitle("Téléchargement vidéo")
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
