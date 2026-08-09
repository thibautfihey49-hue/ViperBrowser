package com.viperbrowser

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var boutonCastDisponible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        configurerWebView()
        detecterVideos()

        // ✅ Initialiser Cast
        try {
            CastContext.getSharedInstance(this)
            boutonCastDisponible = true
        } catch (e: Exception) {
            boutonCastDisponible = false
        }

        webView.loadUrl("https://www.google.com")
    }

    private fun configurerWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
    }

    // ✅ Détection vidéo SIMPLE et SÛRE
    private fun detecterVideos() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(vue: WebView?, requete: WebResourceRequest?): Boolean {
                val url = requete?.url.toString()
                val estVideo = url.contains("video")
                             || url.contains(".mp4")
                             || url.contains("youtube.com/watch")
                             || url.contains("vimeo.com")

                if (estVideo) {
                    Toast.makeText(this@MainActivity, "📹 Vidéo détectée", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }
    }

    // ✅ Bouton Cast dans le menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_cast, menu)
        if (boutonCastDisponible) {
            val boutonCast = menu.findItem(R.id.bouton_cast)
            CastButtonFactory.setUpMediaRouteButton(this, boutonCast)
        }
        return true
    }
}
