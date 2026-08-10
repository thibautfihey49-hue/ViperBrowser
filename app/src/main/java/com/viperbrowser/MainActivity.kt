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
        private val EXT_VIDEO = listOf(".mp4", ".m3u8", ".webm", ".avi", ".mov", ".mkv", ".flv", ".mpd")
        private val HOSTS_VIDEO = listOf("youtube.com", "vimeo.com", "dailymotion.com", "twitch.tv", "video", "watch", "player")
        private const val PERM_STOCKAGE = 12345
        private const val CANAL_NOTIF_DL = "telechargements"
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var dlButton: Button
    private lateinit var listeDlButton: Button
    private lateinit var panneauDl: LinearLayout
    private lateinit var listeDlLayout: LinearLayout

    private val enChargement = AtomicBoolean(false)
    private var urlVideoDetectee: String? = null

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
            if (id != -1L) {
                actualiserProgression(id)
            }
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

            Log.d(TAG, "✅ PRÊT — Téléchargements complets + suivi")

        } catch (e: Exception) {
            Toast.makeText(this, "ERREUR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun creerCanalNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_NOTIF_DL, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
            canal.description = "Suivi des téléchargements"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(canal)
        }
    }

    private fun enregistrerRecepteur() {
        val filtre = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
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
            urlVideoDetectee?.let { lancerTelechargement(it) }
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
        val domaines = listOf(".com", ".fr", ".net", ".org", ".io", ".app", ".dev")
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
                setDescription("Téléchargement en cours...")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
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
                    telechargements.keys.toList().forEach { id ->
                        actualiserProgression(id)
                    }
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
