package com.viperbrowser

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Initialisation OBLIGATOIRE avant setContentView sur Xiaomi
        WebView.setWebContentsDebuggingEnabled(false)
        
        // ✅ Charge le layout SEULEMENT APRÈS WebView prêt
        setContentView(R.layout.activity_main)
        
        // ✅ Configure WebView pour éviter crash
        val webView = findViewById<WebView>(R.id.webView)
        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
    }
}
