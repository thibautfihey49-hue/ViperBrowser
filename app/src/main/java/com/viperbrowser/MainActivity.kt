package com.viperbrowser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
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
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var pageProgress: ProgressBar
    private lateinit var statutSecure: TextView
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnGo: Button
    private lateinit var btnPrivate: Button
    private lateinit var btnVideo: Button
    private lateinit var btnCast: Button
    private lateinit var btnDlList: Button
    private lateinit var dlStatusBar: View
    private lateinit var dlName: TextView
    private lateinit var dlSpeed: TextView
    private lateinit var dlProgress: ProgressBar
    private lateinit var gestureDetector: GestureDetector
    private val handler = Handler(Looper.getMainLooper())

    private var isPrivateMode = false
    private val downloadIds = mutableListOf<Long>()
    private val downloadStartTime = mutableMapOf<Long, Long>()
    private var downloadReceiver: BroadcastReceiver? = null
    private var detectedVideoUrl: String? = null

    companion object {
        private const val REQUEST_PERMISSIONS = 1002
        private val VIDEO_PATTERNS = listOf(
            Pattern.compile("""https?://[^\s"'<>]+\.(mp4|webm|m3u8|m4v)(\?[^\s"'<>]*)?""")
        )
        private val AD_BLOCKED = listOf(
            "doubleclick.net", "googlesyndication.com", "ads.", "ad.", "analytics.",
            "tracker.", "facebook.com/tr", "scorecardresearch.com", "quantserve.com",
            "hotjar.com", "adserver.", "ampproject.org", "adroll.com", "criteo.com", "taboola.com"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.web_view)
        urlBar = findViewById(R.id.url_bar)
        pageProgress = findViewById(R.id.page_progress)
        statutSecure = findViewById(R.id.statut_secure)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnGo = findViewById(R.id.btn_go)
        btnPrivate = findViewById(R.id.btn_private)
        btnVideo = findViewById(R.id.btn_video)
        btnCast = findViewById(R.id.btn_cast)
        btnDlList = findViewById(R.id.btn_dl_list)
        dlStatusBar = findViewById(R.id.dl_status_bar)
        dlName = findViewById(R.id.dl_name)
        dlSpeed = findViewById(R.id.dl_speed)
        dlProgress = findViewById(R.id.dl_progress)

        checkPermissions()
        setupWebViewMaxPerformance()
        setupDownloader()
        setupDownloadReceiverSafe()
        setupGestures()

        webView.loadUrl("https://duckduckgo.com")
        urlBar.setText("duckduckgo.com")

        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnGo.setOnClickListener { loadUrlFromBar() }
        btnPrivate.setOnClickListener { togglePrivateMode() }
        btnVideo.setOnClickListener { detectAndDownloadVideo() }
        btnCast.setOnClickListener { openCastMenu() }
        btnDlList.setOnClickListener { showDownloadsList() }
        urlBar.setOnEditorActionListener { _, _, _ -> loadUrlFromBar(); true }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_DISTANCE = 80f
            private val SWIPE_VELOCITY = 100f
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (Math.abs(dx) > SWIPE_DISTANCE && Math.abs(vx) > SWIPE_VELOCITY) {
                    if (dx < 0 && webView.canGoForward()) { webView.goForward(); return true }
                    else if (dx > 0 && webView.canGoBack()) { webView.goBack(); return true }
                }
                return false
            }
        })
        webView.setOnTouchListener { _, e -> gestureDetector.onTouchEvent(e); false }
    }

    private fun setupWebViewMaxPerformance() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            allowFileAccess = false
            allowContentAccess = false
            saveFormData = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = false
            useWideViewPort = true
            loadWithOverviewMode = true
            loadsImagesAutomatically = true
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageProgress.visibility = View.VISIBLE
                pageProgress.progress = 10
                urlBar.setText(url?.replace("https://", "")?.replace("http://", ""))
                val secure = url?.startsWith("https") == true
                statutSecure.text = if (secure) "🔒 SÉCURISÉ" else "⚠️ NON SÉCURISÉ"
                statutSecure.setTextColor(android.graphics.Color.parseColor(if (secure) "#00FFCC" else "#FF6666"))
                detectedVideoUrl = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageProgress.progress = 100
                handler.postDelayed({ pageProgress.visibility = View.GONE }, 400)
                extractVideoUrlsFromPage()
            }

            override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): WebResourceResponse? {
                val u = req?.url?.toString() ?: return null
                AD_BLOCKED.forEach { if (u.contains(it, ignoreCase = true)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }}
                VIDEO_PATTERNS.forEach { m ->
                    val matcher = m.matcher(u)
                    if (matcher.find() && !u.contains(".html") && !u.contains(".css") && !u.contains(".js")) {
                        if (detectedVideoUrl.isNullOrEmpty() || u.length > (detectedVideoUrl?.length ?: 0)) {
                            detectedVideoUrl = u
                        }
                    }
                }
                return null
            }
        }
    }

    private fun extractVideoUrlsFromPage() {
        webView.evaluateJavascript("""
            (function(){var v=document.querySelector('video');if(!v)return null;var u=v.currentSrc||v.src;if(u)return u;var s=v.querySelector('source');return s?s.src:null;})()
        """) { res ->
            if (!res.isNullOrEmpty() && res != "null") detectedVideoUrl = res.removeSurrounding("\"")
        }
    }

    private fun togglePrivateMode() {
        isPrivateMode = !isPrivateMode
        if (isPrivateMode) {
            webView.clearCache(true); webView.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            Toast.makeText(this, "🕶️ MODE PRIVÉ ACTIF", Toast.LENGTH_SHORT).show()
            btnPrivate.setBackgroundColor(android.graphics.Color.parseColor("#CC3333"))
        } else {
            webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            Toast.makeText(this, "🕶️ MODE PRIVÉ DÉSACTIVÉ", Toast.LENGTH_SHORT).show()
            btnPrivate.setBackgroundColor(android.graphics.Color.parseColor("#252540"))
        }
    }

    // ✅ SIGNATURE CORRIGÉE : 5 paramètres (url, ua, disposition, mimeType, taille)
    private fun setupDownloader() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            startDownloadMaxSpeed(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun startDownloadMaxSpeed(url: String, ua: String, disp: String, mime: String) {
        try {
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                val ck = CookieManager.getInstance().getCookie(url)
                if (!ck.isNullOrEmpty()) addRequestHeader("Cookie", ck)
                addRequestHeader("User-Agent", ua)
                addRequestHeader("Connection", "Keep-Alive")
                val fn = URLUtil.guessFileName(url, disp, mime)
                setTitle(fn)
                setDescription("TÉLÉCHARGEMENT ULTRA RAPIDE")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            downloadIds.add(id)
            downloadStartTime[id] = System.currentTimeMillis()
            dlName.text = URLUtil.guessFileName(url, disp, mime)
            dlStatusBar.visibility = View.VISIBLE
            dlProgress.progress = 0
            dlSpeed.text = "MAX"
            Toast.makeText(this, "⬇️ TÉLÉCHARGEMENT LANCÉ", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ ERREUR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDownloadReceiverSafe() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, i: Intent?) {
                val id = i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id != -1L && downloadIds.contains(id)) updateDlProgress(id)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun updateDlProgress(id: Long) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val c = dm.query(DownloadManager.Query().setFilterById(id))
            c?.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) {
                        dlProgress.progress = (soFar * 100 / total).toInt()
                        val sec = (System.currentTimeMillis() - (downloadStartTime[id] ?: 0)) / 1000.0
                        if (sec > 1) dlSpeed.text = String.format(Locale.FRANCE, "%.0f KB/s", soFar / 1024.0 / sec)
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        dlStatusBar.visibility = View.GONE
                        Toast.makeText(this, "✅ TÉLÉCHARGEMENT TERMINÉ !", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun showDownloadsList() {
        try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
        catch (_: Exception) { Toast.makeText(this, "📂 Voir dans Téléchargements", Toast.LENGTH_SHORT).show() }
    }

    private fun detectAndDownloadVideo() {
        Toast.makeText(this, "🔍 RECHERCHE VIDÉO...", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            val url = detectedVideoUrl
            if (!url.isNullOrEmpty() && url.length > 10) {
                Toast.makeText(this, "✅ VIDÉO TROUVÉE !", Toast.LENGTH_SHORT).show()
                startDownloadMaxSpeed(url, webView.settings.userAgentString, "", "video/*")
            } else {
                Toast.makeText(this, "❌ AUCUNE VIDÉO DÉTECTÉE", Toast.LENGTH_LONG).show()
            }
        }, 600)
    }

    private fun openCastMenu() {
        val url = webView.url ?: ""
        val vid = detectedVideoUrl
        val opts = mutableListOf("🔗 DIFFUSER CETTE PAGE", "📺 APPAREILS DE DIFFUSION")
        if (!vid.isNullOrEmpty()) opts.add(1, "📹 DIFFUSER LA VIDÉO")
        AlertDialog.Builder(this)
            .setTitle("📺 DIFFUSER / CAST")
            .setItems(opts.toTypedArray()) { _, which ->
                when (which) {
                    0 -> castUrl(url)
                    1 -> if (!vid.isNullOrEmpty()) castUrl(vid) else openSystemCast()
                    else -> openSystemCast()
                }
            }.show()
    }

    private fun castUrl(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val apps = packageManager.queryIntentActivities(i, 0)
            if (apps.isNotEmpty()) { startActivity(i); Toast.makeText(this, "📺 DIFFUSION LANCÉE", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(this, "⚠️ Aucune app de diffusion trouvée", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "❌ ERREUR: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun openSystemCast() {
        try {
            val i = Intent("android.settings.CAST_SETTINGS")
            if (packageManager.queryIntentActivities(i, 0).isNotEmpty()) startActivity(i)
            else Toast.makeText(this, "📺 Ouvre Google Home", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }

    private fun loadUrlFromBar() {
        var u = urlBar.text.toString().trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http") && !u.startsWith("about:")) {
            u = if (!u.contains(".") || u.contains(" ")) "https://duckduckgo.com/?q=${u.replace(" ", "+")}" else "https://$u"
        }
        webView.loadUrl(u)
        urlBar.clearFocus()
    }

    override fun onKeyDown(code: Int, e: KeyEvent?): Boolean {
        if (code == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true }
        if (code == KeyEvent.KEYCODE_BACK && !webView.canGoBack()) { confirmExit(); return true }
        return super.onKeyDown(code, e)
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("QUITTER VIPERBROWSER")
            .setMessage("EFFACER CACHE, HISTORIQUE ET COOKIES AVANT DE QUITTER ?")
            .setPositiveButton("✅ OUI") { _, _ ->
                webView.clearCache(true); webView.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                webView.destroy(); finish()
            }
            .setNegativeButton("❌ NON") { _, _ -> webView.destroy(); finish() }
            .setNeutralButton("ANNULER", null)
            .show()
    }

    override fun onDestroy() {
        try { downloadReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        webView.apply { stopLoading(); removeAllViews(); destroy() }
        super.onDestroy()
    }
}
