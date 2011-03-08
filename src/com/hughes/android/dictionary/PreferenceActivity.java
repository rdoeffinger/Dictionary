package com.hughes.android.dictionary;

import android.os.Bundle;

public class PreferenceActivity extends android.preference.PreferenceActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    ((DictionaryApplication)getApplication()).applyTheme(this);

    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }

}
