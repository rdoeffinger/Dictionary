package com.gss.sample.auth;

import android.app.Activity;
import android.os.Bundle;

import com.pras.SpreadSheetFactory;

public class StartActivity extends Activity {

	String TAG = getClass().getName();
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * Pass the Android Authenticator Helper class to SpreadSheetFactory, so that
         * SpreadSheetFactory uses this custom authenticator instead of the default
         * com.pras.auth.BasicAuthenticatorImpl.java
         * 
         * Note: AndroidAuthenticator.java is just a sample implementation for Android 
         * platform. You can change it as per your need.
         */
        AndroidAuthenticator andoAuthenticator = new AndroidAuthenticator(this);
        SpreadSheetFactory factory = SpreadSheetFactory.getInstance(andoAuthenticator);
        factory.getAllSpreadSheets();
        
        // TODO: Do SpreadSheet/WorkSheet specific operations (Add/Edit/Delete etc.)
	}
}
