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

package com.hughes.android.dictionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.hughes.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class HtmlDisplayActivity extends AppCompatActivity {

    private static final String LOG = "QuickDic";

    private static final String HTML_RES = "html_res";
    private static final String HTML = "html";
    private static final String TITLE = "title";
    private static final String TEXT_TO_HIGHLIGHT = "textToHighlight";

    public static Intent getHelpLaunchIntent(Context c) {
        final Intent intent = new Intent(c, HtmlDisplayActivity.class);
        intent.putExtra(HTML_RES, R.raw.help);
        return intent;
    }

    public static Intent getWhatsNewLaunchIntent(Context c) {
        final Intent intent = new Intent(c, HtmlDisplayActivity.class);
        intent.putExtra(HTML_RES, R.raw.whats_new);
        return intent;
    }

    public static Intent getHtmlIntent(Context c, final String html, final String textToHighlight,
                                       final String title) {
        final Intent intent = new Intent(c, HtmlDisplayActivity.class);
        intent.putExtra(HTML, html);
        intent.putExtra(TEXT_TO_HIGHLIGHT, textToHighlight == null ? "" : textToHighlight);
        if (title != null)
            intent.putExtra(TITLE, title);
        return intent;
    }

    public void onOkClick(View dummy) {
        finish();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        DictionaryApplication.INSTANCE.init(getApplicationContext());
        setTheme(DictionaryApplication.INSTANCE.getSelectedTheme().themeId);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.html_display_activity);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        final String title = getIntent().getStringExtra(TITLE);
        if (title != null)
            setTitle(title);

        final MyWebView webView = findViewById(R.id.webView);
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Unfortunately padding is ignored here, so set margins instead
            // Also getSupportActionBar().getHeight() is often 0 so get the size from the attribute instead
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
            int abSize = getResources().getDimensionPixelSize(tv.resourceId);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
            params.setMargins(
                    insets.left,
                    insets.top + abSize,
                    insets.right,
                    0
            );
            v.setLayoutParams(params);
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

        final int htmlRes = getIntent().getIntExtra(HTML_RES, -1);
        String html;
        if (htmlRes != -1) {
            InputStream res = getResources().openRawResource(htmlRes);
            html = StringUtil.readToString(res);
            try {
                res.close();
            } catch (IOException ignored) {
            }
        } else {
            html = getIntent().getStringExtra(HTML);
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String fontSize = prefs.getString(getString(R.string.fontSizeKey), "14");
        int fontSizeSp;
        try {
            fontSizeSp = Integer.parseInt(fontSize.trim());
        } catch (NumberFormatException e) {
            fontSizeSp = 14;
        }
        webView.getSettings().setDefaultFontSize(fontSizeSp);
        // No way to get pure UTF-8 data into WebView
        html = Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        // Use loadURL to allow specifying a charset
        webView.loadUrl("data:text/html;charset=utf-8;base64," + html);
        webView.activity = this;

        final String textToHighlight = getIntent().getStringExtra(TEXT_TO_HIGHLIGHT);
        if (textToHighlight != null && !textToHighlight.isEmpty()) {
            Log.d(LOG, "NOT Highlighting text: " + textToHighlight);
            // This isn't working:
            // webView.findAll(textToHighlight);
            // webView.showFindDialog(textToHighlight, false);
        }
    }

    @Override
    public void onBackPressed() {
        final MyWebView webView = findViewById(R.id.webView);
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Explicitly handle the up button press so
        // we return to the dictionary.
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
