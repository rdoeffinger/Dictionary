package com.hughes.android.util;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;

public class IntentLauncher implements OnClickListener {
  
  final Context context;
  final Intent intent;
  
  public IntentLauncher(final Context context, final Intent intent) {
    this.context = context;
    this.intent = intent;
  }

  protected void onGo() {
  }


  private void go() {
    onGo();
    context.startActivity(intent);
  }

  @Override
  public void onClick(View v) {
    go();
  }
  
  

}
