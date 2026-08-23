// Inside MainActivity.kt -> onCreate()
val webSettings: WebSettings = webView.settings
webSettings.javaScriptEnabled = true
webSettings.domStorageEnabled = true
webSettings.cacheMode = WebSettings.LOAD_DEFAULT // Caches CDNs for offline use

