package com.hughes.android.dictionary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hughes.android.dictionary.engine.HtmlEntry;

public class MyWebView extends WebView {
    
    static final String LOG = "MyWebView";
    
    HtmlDisplayActivity activity;

    public MyWebView(Context context) {
        super(context);
    }
    
    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getSettings().setSupportZoom(true);
        getSettings().setBuiltInZoomControls(true);
        
        final WebViewClient webViewClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (HtmlEntry.isQuickdicUrl(url)) {
                    Log.d(LOG, "Handling Quickdic URL: " + url);
                    final Intent result = new Intent();
                    HtmlEntry.quickdicUrlToIntent(url, result);
                    activity.setResult(Activity.RESULT_OK, result);
                    activity.finish();
                    return true;
                }
                // TODO Auto-generated method stub
                return super.shouldOverrideUrlLoading(view, url);
            }
        };
        setWebViewClient(webViewClient);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu) {
        super.onCreateContextMenu(menu);
    }

}
