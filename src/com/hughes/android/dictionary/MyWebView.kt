package com.hughes.android.dictionary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hughes.android.dictionary.engine.HtmlEntry
import com.hughes.util.StringUtil

class MyWebView : WebView {
    @JvmField
    var activity: HtmlDisplayActivity? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        // Zoom controls are deprecated, ugly and are misplaced
        // (e.g. hidden behind notch/navigation buttons)
        settings.displayZoomControls = false

        // TODO: check why AUTO does not work and consider using it (API 29/Android 10 only)
        //getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
        val webViewClient: WebViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                if (HtmlEntry.isQuickdicUrl(url)) {
                    Log.d(LOG, "Handling Quickdic URL: $url")
                    val result = Intent()
                    quickdicUrlToIntent(url, result)
                    Log.d(LOG, "SEARCH_TOKEN=" + result.getStringExtra(C.SEARCH_TOKEN))
                    activity!!.setResult(Activity.RESULT_OK, result)
                    activity!!.finish()
                    return true
                }
                return false
            }
        }
        setWebViewClient(webViewClient)
    }

    public override fun onCreateContextMenu(menu: ContextMenu?) {
        super.onCreateContextMenu(menu)
    }

    companion object {
        private const val LOG = "MyWebView"

        private fun quickdicUrlToIntent(url: String, intent: Intent) {
            val firstColon = url.indexOf("?")
            if (firstColon == -1) return
            val secondColon = url.indexOf("&", firstColon + 1)
            if (secondColon == -1) return
            intent.putExtra(
                C.SEARCH_TOKEN,
                StringUtil.decodeFromUrl(url.substring(secondColon + 1))
            )
        }
    }
}
