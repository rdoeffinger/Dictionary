// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.hughes.android.dictionary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.hughes.util.StringUtil
import java.io.IOException
import java.nio.charset.StandardCharsets

class HtmlDisplayActivity : AppCompatActivity() {
    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        DictionaryApplication.applyTheme(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.html_display_activity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val title = intent.getStringExtra(TITLE)
        if (title != null) setTitle(title)

        ViewCompat.setOnApplyWindowInsetsListener(
            toolbar
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }

        val webView = findViewById<MyWebView>(R.id.webView)
        ViewCompat.setOnApplyWindowInsetsListener(
            webView
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        val htmlRes = intent.getIntExtra(HTML_RES, -1)
        var html: String?
        if (htmlRes != -1) {
            val res = getResources().openRawResource(htmlRes)
            html = StringUtil.readToString(res)
            try {
                res.close()
            } catch (_: IOException) {
            }
        } else {
            html = intent.getStringExtra(HTML)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val fontSize: String = prefs.getString(getString(R.string.fontSizeKey), "14")!!
        val fontSizeSp: Int = try {
            fontSize.trim { it <= ' ' }.toInt()
        } catch (_: NumberFormatException) {
            14
        }
        webView.settings.defaultFontSize = fontSizeSp
        // No way to get pure UTF-8 data into WebView
        html = Base64.encodeToString(html!!.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
        // Use loadURL to allow specifying a charset
        webView.loadUrl("data:text/html;charset=utf-8;base64,$html")
        webView.activity = this

        val textToHighlight = intent.getStringExtra(TEXT_TO_HIGHLIGHT)
        if (!textToHighlight.isNullOrEmpty()) {
            Log.d(LOG, "NOT Highlighting text: $textToHighlight")
            // This isn't working:
            // webView.findAll(textToHighlight);
            // webView.showFindDialog(textToHighlight, false);
        }
    }

    override fun onBackPressed() {
        val webView = findViewById<MyWebView>(R.id.webView)
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Explicitly handle the up button press so
        // we return to the dictionary.
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val LOG = "QuickDic"

        private const val HTML_RES = "html_res"
        private const val HTML = "html"
        private const val TITLE = "title"
        private const val TEXT_TO_HIGHLIGHT = "textToHighlight"

        @JvmStatic
        fun getHelpLaunchIntent(c: Context?): Intent {
            val intent = Intent(c, HtmlDisplayActivity::class.java)
            intent.putExtra(HTML_RES, R.raw.help)
            return intent
        }

        @JvmStatic
        fun getHtmlIntent(
            c: Context?, html: String?, textToHighlight: String?,
            title: String?
        ): Intent {
            val intent = Intent(c, HtmlDisplayActivity::class.java)
            intent.putExtra(HTML, html)
            intent.putExtra(TEXT_TO_HIGHLIGHT, textToHighlight ?: "")
            if (title != null) intent.putExtra(TITLE, title)
            return intent
        }
    }
}
