package com.hughes.android.dictionary;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public final class AboutActivity extends Activity {

  public static final String CURRENT_DICT_INFO = "currentDictInfo";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.about_activity);

    final Intent intent = getIntent();

    final TextView currentDictInfo = (TextView) findViewById(R.id.currentDictInfo);
    currentDictInfo.setText(intent.getStringExtra(CURRENT_DICT_INFO));
  }

}
