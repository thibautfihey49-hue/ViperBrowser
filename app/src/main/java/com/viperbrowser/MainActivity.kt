package com.viperbrowser

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Initialisation OBLIGATOIRE sur Xiaomi
        WebView.setWebContentsDebuggingEnabled(false)
        
        // ✅ Charge le layout SANS chercher de vue introuvable
        setContentView(R.layout.activity_main)
        
        // ⏳ La WebView sera configurée quand le layout aura la bonne vue
    }
}
