package com.hughes.android.dictionary;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.EditText;

import com.hughes.android.util.PersistentObjectCache;

public class DictionaryEditActivity extends Activity {

  List<DictionaryConfig> dictionaryConfigs;
  private DictionaryConfig dictionaryConfig;

  public static final String DICT_INDEX = "dictIndex";

  /** Called when the activity is first created. */
  @SuppressWarnings("unchecked")
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.edit_activity);

    PersistentObjectCache.init(this);
    dictionaryConfigs = (List<DictionaryConfig>) PersistentObjectCache.init(
        this).read(DictionaryListActivity.DICTIONARY_CONFIGS);

    final Intent intent = getIntent();
    dictionaryConfig = dictionaryConfigs.get(intent.getIntExtra(DICT_INDEX, 0));

    // Write stuff from object into fields.
    
    ((EditText) findViewById(R.id.dictionaryName)).setText(dictionaryConfig.name);
    ((EditText) findViewById(R.id.localFile)).setText(dictionaryConfig.localFile);
    ((EditText) findViewById(R.id.wordListFile)).setText(dictionaryConfig.wordList);
    ((EditText) findViewById(R.id.downloadUrl)).setText(dictionaryConfig.downloadUrl);
  }

  @Override
  protected void onPause() {
    super.onPause();
    
    // Read stuff from fields into object.
    dictionaryConfig.name = ((EditText) findViewById(R.id.dictionaryName)).getText().toString();
    dictionaryConfig.localFile = ((EditText) findViewById(R.id.localFile)).getText().toString();
    dictionaryConfig.wordList = ((EditText) findViewById(R.id.wordListFile)).getText().toString();
    dictionaryConfig.downloadUrl = ((EditText) findViewById(R.id.downloadUrl)).getText().toString();
    
    PersistentObjectCache.getInstance().write(
        DictionaryListActivity.DICTIONARY_CONFIGS, dictionaryConfigs);
  }
  
  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem newDictionaryMenuItem = menu.add(R.string.downloadDictionary);
    newDictionaryMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            startDownloadDictActivity(DictionaryEditActivity.this, dictionaryConfig);
            return false;
          }
        });
    
    return true;
  }

  static void startDownloadDictActivity(final Context context, final DictionaryConfig dictionaryConfig) {
    final Intent intent = new Intent(context, DownloadActivity.class);
    intent.putExtra(DownloadActivity.SOURCE, dictionaryConfig.downloadUrl);
    intent.putExtra(DownloadActivity.DEST, dictionaryConfig.localFile);
    context.startActivity(intent);
  }


}
