package com.hughes.android.dictionary;

import android.app.Activity;
import android.os.Bundle;

public final class AboutActivity extends Activity {

  public static final String CURRENT_DICT_INFO = "currentDictInfo";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    ((DictionaryApplication)getApplication()).applyTheme(this);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.about_activity);
  }

}
