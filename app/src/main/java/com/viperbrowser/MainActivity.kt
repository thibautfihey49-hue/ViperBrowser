package com.viperbrowser

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
        private const val CANAL_NOTIF_DL = "telechargements"
        private const val PERM_STOCKAGE = 12345

        // ❌ FICHIERS À IGNORER — PISTES TROMPEUSES
        private val A_IGNORER = listOf(
            "ping.gif", "tracking.gif", "beacon.gif", "pixel.gif", "stats.gif",
            "analytics.gif", "track.gif", "ad.gif", "banner.gif", "loader.gif",
            "loading.gif", "preload.gif", "player.js", "jwplayer.js", "advert.js"
        )

        // 🎬 HLS EN PREMIER + TOUS FORMATS VIDÉO
        private val MOTIFS_HLS = listOf(
            ".m3u8", "index.m3u8", "master.m3u8", "playlist.m3u8",
            "/hls/", "/m3u8/", "/live/", "/stream/", ".m3u8?"
        )
        private val EXT_VIDEO = listOf(
            ".mp4", ".webm", ".avi", ".mov", ".mkv", ".flv", ".mpd",
            ".ts", ".m4v", ".3gp", ".ogg", ".ogv", ".wmv", ".asf"
        )
        private val HOSTS_VIDEO = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com", "twitch.tv",
            "googlevideo.com", "ytimg.com", "vimeocdn.com", "akamaihd.net",
            "fb.watch", "facebook.com/video", "instagram.com/reel", "tiktok.com",
            "bilibili.com", "nicovideo.jp", "jwplayer", "video.", "watch",
            "player", "embed", "stream", "cdn.v", "vod.", "media.", "live.",
            "flemmix.me", "streamtape", "vidoza", "doodstream", "voe.sx",
            "rabbitstream", "uqload", "mega.nz", "streaming", "serveur",
            "cdnplayer", "video-player", "player-src", "source"
        )
        private val MOTS_VIDEO = listOf(
            ".m3u8", "video/MP2T", "application/x-mpegURL", "vnd.apple.mpegurl",
            "videoUrl", "contentUrl", "source src=", "\"url\"", "\"video\"",
            "videoplayback", "manifest.mpd", "/v/", "/watch/", "/embed/",
            "blob:http", "fileSequence", ".mp4", "jwplayer.config", "sources",
            "file:", "src:", "video:", "streamUrl", "link:\"", "direct"
        )

        // 🛡️ BLOCAGE PUBS ULTRA
        private val BLOCAGE_PUBS = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "amazon-adsystem.com", "adform.net",
            "adroll.com", "adnxs.com", "criteo.com", "taboola.com",
            "ads.", "/ad/", "/ads/", "ad.", "banner.", "popup.",
            "beacon.", "tracking.", "gtag.", "ga.js", "gclid=", "fbclid=",
            "vast.", "vpaid.", "ima.js", "google.ima", "admanager"
        )
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var dlButton: Button
    private lateinit var listeDlButton: Button
    private lateinit var panneauDl: LinearLayout
    private lateinit var listeDlLayout: LinearLayout

    private val enChargement = AtomicBoolean(false)
    private var urlVideoDetectee: String? = null
    private var urlHlsDetectee: String? = null
    private var premiereDetection = true
    private var tentativeJs = 0

    private val telechargements = ConcurrentHashMap<Long, ItemTelechargement>()
    private var gestionnaireDl: DownloadManager? = null
    private var surveillerActif = true

    data class ItemTelechargement(
        val id: Long,
        val nom: String,
        var progression: Int = 0,
        var etat: Int = DownloadManager.STATUS_PENDING,
        var taille: Long = 0,
        var tailleCourante: Long = 0,
        var vue: View? = null
    )

    private val recepteurDl = object : BroadcastReceiver() {
        override fun onReceive(contexte: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id != -1L) actualiserProgression(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            urlInput = findViewById(R.id.urlInput)
            dlButton = findViewById(R.id.dlButton)
            listeDlButton = findViewById(R.id.listeDlButton)
            panneauDl = findViewById(R.id.panneauTelechargements)
            listeDlLayout = findViewById(R.id.listeTelechargements)
            webView = findViewById(R.id.webView)

            urlInput.text.clear()
            urlInput.hint = "Rechercher ou entrer une URL..."

            gestionnaireDl = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            creerCanalNotification()
            verifierPermissions()
            configurerWebView()
            configurerBoutons()
            enregistrerRecepteur()
            demarrerSurveillanceProgression()

            Log.d(TAG, "✅ PRÊT — Ignore ping.gif + détection forcée vidéos")

        } catch (e: Exception) {
            Toast.makeText(this, "ERREUR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun creerCanalNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_NOTIF_DL, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(canal)
        }
    }

    private fun enregistrerRecepteur() {
        val filtre = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recepteurDl, filtre, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recepteurDl, filtre)
        }
    }

    private fun verifierPermissions() {
        val besoins = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            besoins.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            besoins.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            besoins.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val aDemander = besoins.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (aDemander.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, aDemander.toTypedArray(), PERM_STOCKAGE)
        }
    }

    private fun configurerWebView() {
        val r = webView.settings
        r.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        r.domStorageEnabled = true
        r.javaScriptEnabled = true
        r.loadsImagesAutomatically = true
        r.mediaPlaybackRequiresUserGesture = false
        r.javaScriptCanOpenWindowsAutomatically = true
        r.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        r.useWideViewPort = true
        r.loadWithOverviewMode = true
        r.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        r.setSupportZoom(true)
        r.builtInZoomControls = true
        r.displayZoomControls = false
        r.allowFileAccess = false
        r.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) r.setGeolocationEnabled(false)
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = ClientWeb()
    }

    private fun configurerBoutons() {
        findViewById<Button>(R.id.goButton).setOnClickListener {
            if (!enChargement.get()) chargerOuRechercher()
        }
        dlButton.setOnClickListener {
            val urlCible = urlHlsDetectee ?: urlVideoDetectee
            urlCible?.let { lancerTelechargement(it) }
                ?: Toast.makeText(this, "Aucune vidéo détectée", Toast.LENGTH_SHORT).show()
        }
        listeDlButton.setOnClickListener {
            panneauDl.visibility = if (panneauDl.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
        urlHlsDetectee = null
        premiereDetection = true
        tentativeJs = 0
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
        val domaines = listOf(".com", ".fr", ".net", ".org", ".io", ".app", ".dev", ".me")
        for (ext in domaines) if (u.contains(ext)) return "https://$s"
        if (u.contains(".") && !u.contains(" ")) return "https://$s"
        return null
    }

    private fun lancerTelechargement(url: String) {
        try {
            val uri = Uri.parse(url)
            val nomFichier = when {
                url.contains(".m3u8") -> "video_hls_${System.currentTimeMillis()}.m3u8"
                url.contains(".mpd") -> "video_dash_${System.currentTimeMillis()}.mpd"
                else -> URLUtil.guessFileName(url, null, "video/*")
            }

            val requete = DownloadManager.Request(uri).apply {
                setMimeType(when {
                    url.contains(".m3u8") -> "application/vnd.apple.mpegurl"
                    url.contains(".mpd") -> "application/dash+xml"
                    else -> "video/*"
                })
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                setTitle(nomFichier)
                setDescription("Téléchargement en cours...")
                setAllowedOverMetered(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier)
                allowScanningByMediaScanner()
            }

            val idDl = gestionnaireDl!!.enqueue(requete)
            val item = ItemTelechargement(idDl, nomFichier)
            telechargements[idDl] = item
            ajouterVueTelechargement(item)
            panneauDl.visibility = View.VISIBLE
            Toast.makeText(this, "⬇ Démarré : $nomFichier", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ajouterVueTelechargement(item: ItemTelechargement) {
        val ligne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xFFF1F8E9.toInt())
        }
        val titre = TextView(this).apply {
            text = item.nom
            textSize = 12f
            setTextColor(0xFF2E7D32.toInt())
            isSingleLine = true
        }
        val barre = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16)
                .also { it.setMargins(0, 4, 0, 0) }
        }
        val statut = TextView(this).apply {
            text = "En attente..."
            textSize = 10f
            setTextColor(0xFF757575.toInt())
        }
        ligne.addView(titre)
        ligne.addView(barre)
        ligne.addView(statut)
        listeDlLayout.addView(ligne)
        item.vue = ligne
    }

    private fun demarrerSurveillanceProgression() {
        Thread {
            while (surveillerActif && !isFinishing) {
                try {
                    telechargements.keys.toList().forEach { actualiserProgression(it) }
                    Thread.sleep(800)
                } catch (_: Exception) { break }
            }
        }.start()
    }

    private fun actualiserProgression(id: Long) {
        val requete = DownloadManager.Query().setFilterById(id)
        val curseur: Cursor? = gestionnaireDl!!.query(requete)
        curseur?.use {
            if (it.moveToFirst()) {
                val colTaille = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val colActu = it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val colEtat = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val taille = it.getLong(colTaille)
                val courant = it.getLong(colActu)
                val etat = it.getInt(colEtat)
                val prog = if (taille > 0) ((courant * 100) / taille).toInt() else 0

                runOnUiThread {
                    val item = telechargements[id] ?: return@runOnUiThread
                    item.progression = prog
                    item.taille = taille
                    item.tailleCourante = courant
                    item.etat = etat
                    mettreAJourVue(item)
                }
            }
        }
    }

    private fun mettreAJourVue(item: ItemTelechargement) {
        val ligne = item.vue as? LinearLayout ?: return
        val barre = ligne.getChildAt(1) as? ProgressBar ?: return
        val statut = ligne.getChildAt(2) as? TextView ?: return

        barre.progress = item.progression
        statut.text = when (item.etat) {
            DownloadManager.STATUS_PENDING -> "En attente..."
            DownloadManager.STATUS_RUNNING -> "⬇ ${item.progression}% • ${formaterTaille(item.tailleCourante)}/${formaterTaille(item.taille)}"
            DownloadManager.STATUS_SUCCESSFUL -> "✅ Terminé"
            DownloadManager.STATUS_FAILED -> "❌ Échoué"
            else -> "État: ${item.etat}"
        }
        if (item.etat == DownloadManager.STATUS_SUCCESSFUL || item.etat == DownloadManager.STATUS_FAILED) {
            statut.setTextColor(if (item.etat == DownloadManager.STATUS_SUCCESSFUL) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt())
        }
    }

    private fun formaterTaille(octets: Long): String {
        return when {
            octets < 1024 -> "$octets B"
            octets < 1024 * 1024 -> "${octets / 1024} KB"
            octets < 1024 * 1024 * 1024 -> "${String.format("%.1f", octets / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.1f", octets / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    private inner class ClientWeb : WebViewClient() {

        override fun shouldInterceptRequest(v: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val url = rq?.url.toString()
            val urlMin = url.lowercase(Locale.ROOT)

            // ❌ IGNORER LES PISTES TROMPEUSES (ping.gif, etc.)
            for (faux in A_IGNORER) {
                if (urlMin.contains(faux)) {
                    Log.d(TAG, "🚫 IGNORÉ (fausse piste): $faux")
                    return super.shouldInterceptRequest(v, rq)
                }
            }

            // 🛡️ BLOCAGE PUBS EN PREMIER
            for (pub in BLOCAGE_PUBS) {
                if (urlMin.contains(pub)) {
                    Log.d(TAG, "🚫 PUBS BLOQUÉE: $urlMin")
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
            }

            // 🎬 DÉTECTION HLS EN PRIORITÉ
            if (urlHlsDetectee == null) {
                for (hls in MOTIFS_HLS) {
                    if (urlMin.contains(hls)) {
                        urlHlsDetectee = url
                        runOnUiThread { dlButton.visibility = View.VISIBLE }
                        Log.d(TAG, "🎬 HLS DÉTECTÉ: $url")
                        return super.shouldInterceptRequest(v, rq)
                    }
                }
            }

            // 🎬 DÉTECTION VIDÉO PAR URL DIRECTE
            if (urlVideoDetectee == null && urlHlsDetectee == null) {
                for (ext in EXT_VIDEO) {
                    if (urlMin.contains(ext)) {
                        urlVideoDetectee = url
                        runOnUiThread { dlButton.visibility = View.VISIBLE }
                        Log.d(TAG, "🎬 VIDÉO DÉTECTÉE: $url")
                        return super.shouldInterceptRequest(v, rq)
                    }
                }
            }

            // 🎬 DÉTECTION PAR HÉBERGEUR / SITE DE STREAMING
            if (premiereDetection && urlVideoDetectee == null && urlHlsDetectee == null) {
                for (hote in HOSTS_VIDEO) {
                    if (urlMin.contains(hote)) {
                        premiereDetection = false
                        runOnUiThread { dlButton.visibility = View.VISIBLE }
                        Log.d(TAG, "🎬 SITE VIDÉO DÉTECTÉ: $hote → Recherche en cours...")
                        break
                    }
                }
            }

            return super.shouldInterceptRequest(v, rq)
        }

        override fun onPageFinished(v: WebView?, url: String?) {
            enChargement.set(false)

            // 🎬 INJECTION JAVASCRIPT — CHERCHE LA VIDÉO CACHÉE
            url?.let {
                // Répète 3 fois avec délai car la vidéo arrive après chargement
                val delais = listOf(1200L, 2500L, 4000L)
                delais.forEachIndexed { index, delai ->
                    Thread {
                        Thread.sleep(delai)
                        runOnUiThread {
                            val jsRechercheVideo = """
                                (function(){
                                    // 1. Cherche balises <video> et <source>
                                    var videos = document.querySelectorAll('video');
                                    var trouve = [];
                                    for(var i=0;i<videos.length;i++){
                                        var src = videos[i].src;
                                        if(!src){var s = videos[i].querySelectorAll('source'); if(s.length>0) src = s[0].src;}
                                        if(src && (src.includes('.m3u8') || src.includes('.mp4') || src.includes('blob:'))) trouve.push(src);
                                    }
                                    // 2. Cherche dans tout le code de la page
                                    var pageHTML = document.documentElement.outerHTML;
                                    var regM3u8 = /https?:\/\/[^"']+\.m3u8[^"']*/g;
                                    var regMp4 = /https?:\/\/[^"']+\.mp4[^"']*/g;
                                    var m3u8Trouve = pageHTML.match(regM3u8);
                                    var mp4Trouve = pageHTML.match(regMp4);
                                    if(m3u8Trouve && m3u8Trouve.length>0) trouve.push(...m3u8Trouve);
                                    if(mp4Trouve && mp4Trouve.length>0) trouve.push(...mp4Trouve);
                                    // 3. Renvoie les résultats
                                    if(trouve.length>0){
                                        window.VIPER_FOUND_VIDEO = trouve[0];
                                        console.log('VIPER-VIDEO-FOUND:', trouve[0]);
                                        trouve[0];
                                    } else 'RIEN';
                                })();
                            """.trimIndent()
                            v?.evaluateJavascript(jsRechercheVideo) { resultat ->
                                Log.d(TAG, "🔍 JS tentative ${index+1}: $resultat")
                                resultat?.let { res ->
                                    if (res.contains(".m3u8") || res.contains(".mp4") || res.contains("blob:")) {
                                        var urlFinale = res.trim().removeSurrounding("\"")
                                        if (urlFinale.startsWith("http")) {
                                            if (urlFinale.contains(".m3u8")) {
                                                if (urlHlsDetectee == null) {
                                                    urlHlsDetectee = urlFinale
                                                    dlButton.visibility = View.VISIBLE
                                                    Log.d(TAG, "✅✅✅ HLS TROUVÉ PAR JS: $urlFinale")
                                                }
                                            } else {
                                                if (urlVideoDetectee == null) {
                                                    urlVideoDetectee = urlFinale
                                                    dlButton.visibility = View.VISIBLE
                                                    Log.d(TAG, "✅✅✅ VIDÉO TROUVÉE PAR JS: $urlFinale")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.start()
                }
            }
        }

        override fun onPageStarted(v: WebView?, u: String?, ic: android.graphics.Bitmap?) {
            enChargement.set(true)
            urlVideoDetectee = null
            urlHlsDetectee = null
            premiereDetection = true
            tentativeJs = 0
            runOnUiThread { dlButton.visibility = View.GONE }
            u?.let { urlInput.setText(it) }
        }

        override fun shouldOverrideUrlLoading(v: WebView?, rq: WebResourceRequest?): Boolean {
            val u = rq?.url.toString()
            if (u.startsWith("http")) v?.loadUrl(u)
            return true
        }
    }

    override fun onBackPressed() {
        if (panneauDl.visibility == View.VISIBLE) {
            panneauDl.visibility = View.GONE
        } else if (!enChargement.get() && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        surveillerActif = false
        unregisterReceiver(recepteurDl)
        webView.clearCache(true)
        webView.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(code: Int, liste: Array<out String>, resultat: IntArray) {
        super.onRequestPermissionsResult(code, liste, resultat)
        if (code == PERM_STOCKAGE && resultat.isNotEmpty() && resultat.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "✅ Permissions OK", Toast.LENGTH_SHORT).show()
        }
    }
}
