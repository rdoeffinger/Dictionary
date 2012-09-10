package com.hughes.android.dictionary;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.webkit.WebView;

public class MyWebView extends WebView {

    public MyWebView(Context context) {
        super(context);
    }
    
    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu) {
        super.onCreateContextMenu(menu);
    }

}
