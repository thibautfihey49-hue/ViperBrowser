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
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViperBrowser"
        private const val MOTEUR_RECHERCHE = "https://www.google.com/search?q="
        private const val CANAL_NOTIF_DL = "telechargements"
        private const val PERM_STOCKAGE = 12345

        // ❌ PISTES TROMPEUSES
        private val A_IGNORER = listOf(
            "ping.gif", "tracking.gif", "beacon.gif", "pixel.gif", "stats.gif",
            "analytics.gif", "track.gif", "ad.gif", "banner.gif", "loader.gif",
            "loading.gif", "preload.gif", "jwplayer.js", "advert.js"
        )

        // 🎬 DÉTECTION VIDÉO
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

        // =============================================================
        // 🛡️ BLOCAGE ABSOLU — TOUTES LES RÈGLES EN UNE FOIS
        // =============================================================

        // 🔴 DOMAINES PUBLICITAIRES ENTIÈREMENT BLOQUÉS
        private val BLOCAGE_DOMAINE = setOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "googletagmanager.com", "googletagservices.com", "amazon-adsystem.com",
            "adform.net", "adroll.com", "adtech.com", "adnxs.com", "rubiconproject.com",
            "criteo.com", "taboola.com", "outbrain.com", "pubmatic.com", "openx.net",
            "smartadserver.com", "teads.tv", "teads.com", "teads.a", "teads.b",
            "spotx.tv", "spotxchange.com", "spotx.qa", "freewheel.tv", "brid.tv",
            "adstir.com", "adcolony.com", "unityads.unity3d.com", "admob.com",
            "admanager.google.com", "ads.google.com", "pagead2.googlesyndication.com",
            "ad.doubleclick.net", "l.doubleclick.net", "static.doubleclick.net",
            "google-analytics.com", "analytics.google.com", "ssl.google-analytics.com",
            "hotjar.com", "mouseflow.com", "fullstory.com", "mixpanel.com", "segment.com",
            "quantserve.com", "scorecardresearch.com", "chartbeat.com", "newrelic.com",
            "sentry.io", "datadoghq.com", "amplitude.com", "heapanalytics.com",
            "crashlytics.com", "bugsnag.com", "logrocket.com", "posthog.com",
            "conversionpixel.net", "facebook.com/tr", "facebook.com/b", "connect.facebook.net/en_US/fbevents.js",
            "analytics.twitter.com", "ads.yahoo.com", "analytics.yahoo.com", "bing.com/bat.js",
            "clarity.ms", "adbutler.com", "adzerk.net", "adthrive.com", "mediavine.com",
            "triplelift.com", "sovrn.com", "lijit.com", "sharethrough.com", "revcontent.com",
            "casalemedia.com", "concert.io", "distroscale.com", "adblade.com", "admixer.com",
            "adunity.com", "adverity.com", "telaria.com", "tremorvideo.com", "aniview.com",
            "jwplayer.com/ad", "jwplayer.com/ads", "ima.akamaized.net", "googleads.g.doubleclick.net",
            "adservice.google.com", "adservice.google.fr", "adservice.googleusercontent.com",
            "adx.google.com", "adx.l.google.com", "cm.g.doubleclick.net",
            "partnerad.l.doubleclick.net", "www.googleadservices.com", "www.googletagmanager.com",
            "www.google-analytics.com", "beacon.teads.tv", "pixel.teads.com", "track.teads.com",
            "adserver.teads.com", "vpaid.teads.tv", "vast.teads.com", "preroll.teads.tv",
            "ima.teads.tv", "delivery.teads.tv", "cdn.teads.tv", "static.teads.com",
            "sync.teads.com", "bid.teads.tv", "exchange.teads.com", "rtb.teads.com",
            "ad.teads.com", "adtechus.com", "adtelligent.com", "adtrgt.com", "adunit.io",
            "adventori.com", "adveris.com", "adverticum.net", "advertex.io", "advision.com",
            "adwily.com", "adx.live", "adxcg.com", "adzerk.net", "aerserv.com", "afp.ai",
            "agkn.com", "akamaized.net/ads", "algorix.co", "amgdgt.com", "amobee.com",
            "anewrelic.com", "apex.travel", "appier.com", "applift.com", "appnexus.com",
            "appodeal.com", "appsflyer.com", "aptus.com", "aralego.com", "area17.com",
            "art19.com", "atdmt.com", "auctionads.com", "aura.com", "automattic.com/track",
            "avocet.io", "awin.com", "axp.com", "bannernow.com", "beachfront.io", "beeswax.com",
            "betterads.com", "bidswitch.net", "bizon.ai", "blismedia.com", "bluekai.com",
            "brightcove.com/ads", "btb.io", "bytedance.com/ads", "c1exchange.com", "criteo.net",
            "connectad.io", "consensys.net/ads", "conversantmedia.com", "cpmstar.com",
            "creative.ak.fbcdn.net", "custplace.com", "cxense.com", "dable.io", "deepintent.com",
            "demandbase.com", "demdex.net", "disqusads.com", "distillery.io", "dotomi.com",
            "doubleverify.com", "dstillery.com", "duckdaotsu.com", "ebay.com/ads", "e-planning.net",
            "emxdgt.com", "engageya.com", "enterscale.com", "ezoic.com", "f-squared.com",
            "facebook.com/ads", "fairbid.com", "fanads.com", "fandom.com/ads", "feedad.com",
            "flashtalking.com", "fmpub.net", "freewheel.tv", "futuri.com", "fyber.com",
            "gamoshi.org", "genius.com/ads", "getintent.com", "gfp.one", "githack.com/ads",
            "globossp.com", "grapeshot.com", "gumgum.com", "h1-analytics.com", "habx.com",
            "hive.co", "hotmart.com/ads", "hypers.com", "idealmedia.com", "imrworldwide.com",
            "infusion.com", "inmobi.com", "innity.com", "integralads.com", "intentiq.com",
            "iponweb.com", "iprospect.com", "isocket.com", "ix.com", "jivox.com", "jumboproduct.com",
            "kargo.com", "kedro.com", "kenshoo.com", "keywee.co", "kiosked.com", "knares.com",
            "kochava.com", "krxd.net", "larky.com", "leagueanalytics.com", "ligatus.com",
            "liveintent.com", "lkqd.net", "logical.com", "lotame.com", "m1.2mdn.net", "m6r.eu",
            "marinsoftware.com", "matomo.org", "media.net", "mediatrust.com", "meetrics.com",
            "mercle.com", "metapex.com", "mgo2.com", "microad.net", "mightyhive.com", "minutemedia.com",
            "mobfox.com", "moat.com", "mojing.com", "monarchads.com", "mopub.com", "moreover.com",
            "msads.net", "msn.com/ads", "mydas.jp", "narrative.io", "navegg.com", "nuggad.net",
            "oblivious.net", "ocdm.io", "omnitagjs.com", "onclick.com", "onead.com", "onnetwork.com",
            "openpass.com", "oracle.com/ads", "outbrain.com", "overwolf.com/ads", "p7.org",
            "parsely.com", "path.com", "paypal.com/ads", "pearl.com", "perimeterx.com",
            "pinterest.com/ads", "piano.io", "pixalate.com", "plaid.com/ads", "platform-cdn.com",
            "plista.com", "polka.io", "prebid.org", "prisa.com", "prospectus.com", "pubgears.com",
            "pubtech.com", "pubvantage.com", "purch.com", "qontext.io", "quora.com/ads", "ramp.com",
            "rapidspike.com", "reson8.com", "revjet.com", "rhythmone.com", "robin8.com", "ru4.com",
            "s4c.com", "salesforce.com/ads", "samba.tv", "scoopwhoop.com/ads", "scorecardresearch.com",
            "seenthis.com", "sfr.fr/ads", "sharethrough.com", "simpli.fi", "sovrn.com", "sparteo.com",
            "spot.im", "spotxchange.com", "springserve.com", "stickyad.tv", "stroeer.com", "sulvo.com",
            "swoop.com", "taboola.com", "taggify.net", "tapad.com", "taptap.com/ads", "teads.com",
            "thetradedesk.com", "tribalfusion.com", "truelink.com", "trustarc.com", "turn.com",
            "twitter.com/ads", "ucdconnect.ie/ads", "unruly.co", "upsellit.com", "valueclick.com",
            "velocid.com", "verisign.com/ads", "verticalscope.com", "videohub.tv", "vidible.tv",
            "viralheat.com", "vungle.com", "w55c.net", "weborama.com", "weborq.com", "wunderloop.com",
            "xandr.com", "xiti.com", "yahoo.com/ads", "yandex.ru/ads", "yld.com", "yotpo.com",
            "zedo.com", "zemanta.com", "zenaps.com", "zergnet.com", "zypmedia.com", "ad.", "ads."
        )

        // 🔴 MOTIFS DE CHEMIN / FICHIER PUBLICITAIRES
        private val BLOCAGE_MOTIF = setOf(
            "/ad.", "/ad-", "/_ad/", "/ads.", "/ads-", "/ads/", "/adx/", "/adserver/",
            "/advert/", "/advertisement/", "/banner/", "/banners/", "/pop/", "/popup/",
            "/popunder/", "/preroll/", "/midroll/", "/postroll/", "/vast/", "/vpaid/",
            "/ima/", "/admanager/", "/adexchange/", "/analytics/", "/tracking/", "/tracker/",
            "/beacon/", "/pixel/", "/stats/", "/gtm.js", "/ga.js", "/gtag.js", "/fbq.js",
            "/pixel.js", "/analytics.js", "/tracking.js", "/beacon.js", "/stats.js",
            "/advert.js", "/ad.js", "/ads.js", "/vast.js", "/vpaid.js", "/ima.js",
            "/companion.js", "/preroll.js", "/adplayer.js", "/ad-serving/", "/ad-tech/",
            "/adnetwork/", "/ad-banner/", "/ad-popup/", "/ad-container/", "/ad-wrapper/",
            "/ad-placeholder/", "/ad-banner-", "/ad_container_", "/div-ad-", "/div_ad_",
            "ad.", "ads.", "ad-", "_ad.", "-ad.", ".ad.", ".ads.", "ad_", "_ad_",
            "banner.", "popup.", "popunder.", "preroll.", "vast.", "vpaid.", "ima.",
            "advert.", "advertising.", "tracking.", "beacon.", "pixel.", "analytics.",
            "affiliate.", "sponsor.", "promo.", "promotion.", "adstatus=", "adposition=",
            "adunit=", "ad_slot=", "ad_container=", "ad_wrapper=", "ad_format=", "ad_type=",
            "ad_size=", "ad_width=", "ad_height=", "ad_page=", "ad_url=", "ad_ref=",
            "ad_zone=", "ad_block=", "ad_div=", "ad_id=", "ad_client=", "ad_slot_id=",
            "ad_query=", "ad_keyword=", "ad_campaign=", "ad_group=", "ad_creative=",
            "vast_url=", "vpaid_url=", "ad_tag=", "ad_url=", "ad_source=", "ad_network=",
            "preroll_ad=", "midroll_ad=", "postroll_ad=", "ad_video=", "video_ad=",
            "ima_sdk.js", "google.ima", "MediaFile", "VASTAdTagURI", "AdWrapper",
            "LinearAd", "NonLinearAd", "CompanionAd", "AdSystem", "AdTitle", "AdDescription",
            "Impression", "ClickThrough", "ClickTracking", "VideoClicks", "MediaFiles",
            "vast.xml", "vpaid.js", "adTagUrl", "ad_tag_url", "vastUrl", "vpaidUrl",
            "prerollUrl", "adUrl", "advertUrl", "adServerUrl", "ad_request_url",
            "jwplayer.ads", "jwplayer.advertising", "jwplayer.preroll", "jwplayer.vast",
            "jwplayer.vpaid", "jwplayer.ima", "jwplayer.ad", "jwplayer_ad", "jwplayerAds",
            "clickad", "clickAd", "showAd", "show_ads", "loadAd", "loadAds", "getAd",
            "getAds", "displayAd", "displayAds", "renderAd", "renderAds", "serveAd",
            "serveAds", "fetchAd", "fetchAds", "requestAd", "requestAds", "adClick",
            "adImpression", "adView", "adShown", "adDisplayed", "adContainer", "adWrapper"
        )

        // 🔴 PARAMÈTRES DE SUIVI À SUPPRIMER DES URLS
        private val PARAMS_SUIVI = setOf(
            "gclid", "fbclid", "yclid", "mc_eid", "mc_cid", "gbraid", "wbraid",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_name", "utm_id", "utm_keyword", "utm_group", "ad_id", "adid",
            "cid", "client_id", "session_id", "tracking_id", "trackid", "ref",
            "referrer", "source", "medium", "campaign", "keyword", "term", "content"
        )

        // 🔴 INJECTION JS — SUPPRESSION TOTALE DES PUBS DANS LA PAGE
        private val JS_PARAGE_COMPLET = """
            (function(){
                'use strict';
                // 1. Supprimer TOUS les éléments contenant "ad" / "pub" / "banner"
                const motsPubs = ['ad','ads','ad-','ad_','pub','pub-','pub_','banner','popup',
                                  'popunder','preroll','vast','vpaid','ima','advert','sponsor',
                                  'promo','teads','adserver','adContainer','adWrapper','adBox',
                                  'adInner','adContent','adFrame','adBanner','adBlock','adUnit',
                                  'adSlot','adZone','adSpace','adSection','adDiv','adIframe',
                                  'adScript','adVideo','adPlayer','adOverlay','adLayer','adMask',
                                  'adBackdrop','adModal','adDialog','adFull','adInterstitial',
                                  'adText','adLink','adImage','adMedia','adCompanion','adDisplay'];
                for(let mot of motsPubs){
                    try{
                        document.querySelectorAll('[id*="'+mot+'"],[class*="'+mot+'"],[name*="'+mot+'"]').forEach(el=>{
                            el.remove(); el.style.display='none'; el.innerHTML=''; el.src='about:blank';
                        });
                    }catch(e){}
                }
                // 2. Supprimer TOUS les scripts publicitaires
                const scriptsPubs = [/ad[s]?\.js/i, /vast\.js/i, /vpaid\.js/i, /ima\.js/i, /preroll\.js/i,
                                     /gtag\.js/i, /gtm\.js/i, /ga\.js/i, /analytics\.js/i, /fbq\.js/i,
                                     /pixel\.js/i, /beacon\.js/i, /teads\.js/i, /advert\.js/i];
                document.querySelectorAll('script').forEach(s=>{
                    if(s.src){
                        for(let r of scriptsPubs){
                            if(r.test(s.src)){try{s.remove();}catch(e){}}
                        }
                    }
                });
                // 3. Supprimer TOUS les iframes publicitaires
                document.querySelectorAll('iframe').forEach(iframe=>{
                    const src = iframe.src.toLowerCase();
                    if(src.includes('ad')||src.includes('vast')||src.includes('vpaid')||
                       src.includes('ima')||src.includes('doubleclick')||src.includes('teads')||
                       src.includes('ads')||src.includes('banner')||src.includes('popup')){
                        try{iframe.remove();}catch(e){}
                    }
                });
                // 4. DÉSACTIVER COMPLÈTEMENT LES API PUBLICITAIRES
                try{window.gtag=()=>{};window.ga=()=>{};window.fbq=()=>{};window.ima=undefined;
                    window.google=window.google||{};window.google.ima=undefined;
                    window.google_tag_manager=undefined;window.__ga=undefined;
                    window.dataLayer=[];window._gaq=[];window.gaGlobal=undefined;
                    window.teads=undefined;window.Teads=undefined;window.vast=undefined;
                    window.vpaid=undefined;window.preroll=undefined;window.AdManager=undefined;
                    window.AdSense=undefined;window.Ads=undefined;window.Ad=undefined;
                    window.getAds=()=>[];window.showAds=()=>{};window.loadAds=()=>{};
                    window.requestAds=()=>{};window.displayAds=()=>{};window.renderAds=()=>{};
                }catch(e){}
                // 5. BLOQUER LES REDIRECTIONS PUBS
                const ouvrir = window.open;
                window.open = function(u,t,w){
                    if(u&&(u.includes('ad')||u.includes('vast')||u.includes('teads')||
                       u.includes('doubleclick')||u.includes('ads'))) return null;
                    return ouvrir.call(window,u,t,w);
                };
                console.log('✅ BLOCAGE PUBS ACTIF — API désactivées + éléments supprimés');
            })();
        """.trimIndent()
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

            Log.d(TAG, "🛡️ BLOCAGE ABSOLU — VERSION FINALE")

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

    private fun nettoyerUrlSuivi(url: String): String {
        if (!url.contains("?")) return url
        val base = url.substringBefore("?")
        val params = url.substringAfter("?").split("&").filter { param ->
            val cle = param.substringBefore("=").lowercase(Locale.ROOT)
            cle !in PARAMS_SUIVI
        }
        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }

    private fun configurerWebView() {
        val r = webView.settings
        r.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        r.domStorageEnabled = true
        r.javaScriptEnabled = true
        r.loadsImagesAutomatically = true
        r.mediaPlaybackRequiresUserGesture = false
        r.javaScriptCanOpenWindowsAutomatically = false
        r.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        r.useWideViewPort = true
        r.loadWithOverviewMode = true
        r.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        r.setSupportZoom(true)
        r.builtInZoomControls = true
        r.displayZoomControls = false
        r.allowFileAccess = false
        r.allowContentAccess = false
        r.blockNetworkImage = false
        r.blockNetworkLoads = false
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

        // 🛡️ BLOCAGE ABSOLU — TOUS LES NIVEAUX
        private fun estBloque(urlMin: String): Boolean {
            // NIVEAU 1 — Domaine entier
            for (domaine in BLOCAGE_DOMAINE) {
                if (urlMin.contains(domaine)) {
                    Log.d(TAG, "🚫 BLOQUÉ [DOMAINE]: $domaine")
                    return true
                }
            }
            // NIVEAU 2 — Motif dans l'URL
            for (motif in BLOCAGE_MOTIF) {
                if (urlMin.contains(motif)) {
                    Log.d(TAG, "🚫 BLOQUÉ [MOTIF]: $motif")
                    return true
                }
            }
            return false
        }

        override fun shouldInterceptRequest(v: WebView?, rq: WebResourceRequest?): WebResourceResponse? {
            val url = rq?.url.toString()
            val urlMin = url.lowercase(Locale.ROOT)

            // ❌ IGNORER LES PISTES TROMPEUSES
            for (faux in A_IGNORER) {
                if (urlMin.contains(faux)) {
                    Log.d(TAG, "🚫 IGNORÉ (fausse piste): $faux")
                    return super.shouldInterceptRequest(v, rq)
                }
            }

            // 🛡️ BLOCAGE ABSOLU
            if (estBloque(urlMin)) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
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

            // 🎬 DÉTECTION PAR HÉBERGEUR
            if (premiereDetection && urlVideoDetectee == null && urlHlsDetectee == null) {
                for (hote in HOSTS_VIDEO) {
                    if (urlMin.contains(hote)) {
                        premiereDetection = false
                        runOnUiThread { dlButton.visibility = View.VISIBLE }
                        Log.d(TAG, "🎬 SITE VIDÉO DÉTECTÉ: $hote")
                        break
                    }
                }
            }

            return super.shouldInterceptRequest(v, rq)
        }

        override fun onPageFinished(v: WebView?, url: String?) {
            enChargement.set(false)

            // 🛡️ PARAGE COMPLET DE LA PAGE — SUPPRIME PUBS + DÉSACTIVE API
            v?.evaluateJavascript(JS_PARAGE_COMPLET, null)

            // 🎬 RECHERCHE VIDÉO DANS LE CODE DE LA PAGE
            url?.let {
                val delais = listOf(800L, 1800L, 3200L)
                delais.forEachIndexed { index, delai ->
                    Thread {
                        Thread.sleep(delai)
                        runOnUiThread {
                            val jsRechercheVideo = """
                                (function(){
                                    var videos = document.querySelectorAll('video');
                                    var trouve = [];
                                    for(var i=0;i<videos.length;i++){
                                        var src = videos[i].src;
                                        if(!src){var s = videos[i].querySelectorAll('source'); if(s.length>0) src = s[0].src;}
                                        if(src && (src.includes('.m3u8') || src.includes('.mp4') || src.includes('blob:'))) trouve.push(src);
                                    }
                                    var pageHTML = document.documentElement.outerHTML;
                                    var regM3u8 = /https?:\/\/[^"']+\.m3u8[^"']*/g;
                                    var regMp4 = /https?:\/\/[^"']+\.mp4[^"']*/g;
                                    var m3u8Trouve = pageHTML.match(regM3u8);
                                    var mp4Trouve = pageHTML.match(regMp4);
                                    if(m3u8Trouve && m3u8Trouve.length>0) trouve.push(...m3u8Trouve);
                                    if(mp4Trouve && mp4Trouve.length>0) trouve.push(...mp4Trouve);
                                    trouve.length>0 ? trouve[0] : 'RIEN';
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
                                                    Log.d(TAG, "✅✅✅ HLS TROUVÉ: $urlFinale")
                                                }
                                            } else {
                                                if (urlVideoDetectee == null) {
                                                    urlVideoDetectee = urlFinale
                                                    dlButton.visibility = View.VISIBLE
                                                    Log.d(TAG, "✅✅✅ VIDÉO TROUVÉE: $urlFinale")
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
