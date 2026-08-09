package com.viperbrowser
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val webView = findViewById<WebView>(R.id.web_view)
        val urlBar = findViewById<EditText>(R.id.url_bar)
        findViewById<Button>(R.id.btn_back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<Button>(R.id.btn_forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<Button>(R.id.btn_go).setOnClickListener {
            var u = urlBar.text.toString().trim()
            if (!u.startsWith("http")) u = "https://$u"
            webView.loadUrl(u)
        }
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { urlBar.setText(url) }
        }
        webView.loadUrl("https://duckduckgo.com")
    }
}
