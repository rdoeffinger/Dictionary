package com.hughes.android.dictionary;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class NoDictionaryActivity extends Activity {
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      setContentView(R.layout.no_dictionary);
      
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      final String dictFile = prefs.getString(getString(R.string.dictFileKey), getString(R.string.dictFileDefault));

      final boolean canReadDict = new File(dictFile).canRead();
        
      final TextView statusText = (TextView) findViewById(R.id.statusTextId);
      if (!canReadDict) {
        statusText.setText(String.format(getString(R.string.unableToReadDictionaryFile), dictFile));
      } else {
        statusText.setText(String.format(getString(R.string.unableToReadDictionaryFile), dictFile));
      }

      final Button downloadButton = (Button) findViewById(R.id.downloadDict);
      downloadButton.setOnClickListener(new OnClickListener() {
        public void onClick(View arg0) {
          DictionaryActivity.startDownloadDictActivity(NoDictionaryActivity.this);
        }});

      final Button prefsButton = (Button) findViewById(R.id.preferences);
      prefsButton.setOnClickListener(new OnClickListener() {
        public void onClick(View arg0) {
          startActivity(new Intent(NoDictionaryActivity.this, PreferenceActivity.class));
        }});

      final Button launchButton = (Button) findViewById(R.id.launchDict);
      launchButton.setEnabled(canReadDict);
      launchButton.setOnClickListener(new OnClickListener() {
        public void onClick(View arg0) {
          startActivity(new Intent(NoDictionaryActivity.this, DictionaryActivity.class));
        }});
}

}
