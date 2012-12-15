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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.hughes.util.StringUtil;

public final class HtmlDisplayActivity extends Activity {
    
  static final String LOG = "QuickDic";

  
  static final String HTML_RES = "html_res";
  static final String HTML = "html";
  static final String TEXT_TO_HIGHLIGHT = "textToHighlight";
  static final String SHOW_OK_BUTTON = "showOKButton";
  
  public static Intent getHelpLaunchIntent() {
    final Intent intent = new Intent();
    intent.setClassName(HtmlDisplayActivity.class.getPackage().getName(), HtmlDisplayActivity.class.getName());
    intent.putExtra(HTML_RES, R.raw.help);
    return intent;
  }

  public static Intent getWhatsNewLaunchIntent() {
    final Intent intent = new Intent();
    intent.setClassName(HtmlDisplayActivity.class.getPackage().getName(), HtmlDisplayActivity.class.getName());
    intent.putExtra(HTML_RES, R.raw.whats_new);
    return intent;
  }

  public static Intent getHtmlIntent(final String html, final String textToHighlight, final boolean showOkButton) {
    final Intent intent = new Intent();
    intent.setClassName(HtmlDisplayActivity.class.getPackage().getName(), HtmlDisplayActivity.class.getName());
    intent.putExtra(HTML, html);
    intent.putExtra(TEXT_TO_HIGHLIGHT, textToHighlight);
    intent.putExtra(SHOW_OK_BUTTON, showOkButton);
    return intent;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    setTheme(((DictionaryApplication)getApplication()).getSelectedTheme().themeId);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.html_display_activity);
    
    final int htmlRes = getIntent().getIntExtra(HTML_RES, -1);
    final String html;
    if (htmlRes != -1) {
      html = StringUtil.readToString(getResources().openRawResource(htmlRes));
    } else {
      html = getIntent().getStringExtra(HTML);
    }
    final MyWebView webView = (MyWebView) findViewById(R.id.webView);
    webView.loadData(html, "text/html", "utf-8");
    webView.activity = this;
    
    final String textToHighlight = getIntent().getStringExtra(TEXT_TO_HIGHLIGHT);
    if (textToHighlight != null && !"".equals(textToHighlight)) {
        Log.d(LOG, "NOT Highlighting text: " + textToHighlight);
        // This isn't working:
        // webView.findAll(textToHighlight);
        //webView.showFindDialog(textToHighlight, false);
    }
    
    final Button okButton = (Button) findViewById(R.id.okButton);
    okButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
    if (!getIntent().getBooleanExtra(SHOW_OK_BUTTON, true)) {
        okButton.setVisibility(Button.GONE);
    }
  }

}
