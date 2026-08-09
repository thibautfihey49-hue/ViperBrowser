package com.viperbrowser

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Initialisation OBLIGATOIRE sur Xiaomi
        WebView.setWebContentsDebuggingEnabled(false)
        
        // ✅ Charge le layout SANS ERREUR
        setContentView(R.layout.activity_main)

        // ✅ Récupère les vues
        val webView = findViewById<WebView>(R.id.webView)
        val urlBar = findViewById<EditText>(R.id.urlBar)
        val btnGo = findViewById<Button>(R.id.btnGo)

        // ✅ Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // ✅ Bouton aller
        btnGo.setOnClickListener {
            var url = urlBar.text.toString().trim()
            if (url.isNotEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                webView.loadUrl(url)
            }
        }

        // ✅ Page d'accueil
        webView.loadUrl("https://www.google.com")
    }
}
