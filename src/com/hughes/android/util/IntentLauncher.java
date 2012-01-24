package com.hughes.android.util;

import android.app.Activity;
import android.content.Intent;

public class IntentLauncher {
  
  final Intent intent;
  final Activity activity;
  
  private IntentLauncher(final Intent intent, final Activity activity) {
    this.intent = intent;
    this.activity = activity;
  }
  
  private void go() {
    if (activity != null) {
      activity.finish();
    }
    activity.startActivity(intent);
  }
  
  

}
