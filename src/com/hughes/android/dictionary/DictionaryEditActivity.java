package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.EditText;
import android.widget.TextView;

import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Index;
import com.hughes.android.util.PersistentObjectCache;

public class DictionaryEditActivity extends Activity {

  QuickDicConfig quickDicConfig;
  private DictionaryConfig dictionaryConfig;

  
  public static Intent getIntent(final int dictIndex) {
    final Intent intent = new Intent();
    intent.setClassName(DictionaryEditActivity.class.getPackage().getName(), DictionaryEditActivity.class.getName());
    intent.putExtra(C.DICT_INDEX, dictIndex);
    return intent;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.edit_activity);

    PersistentObjectCache.init(this);
    quickDicConfig = PersistentObjectCache.init(
        this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);

    final Intent intent = getIntent();
    dictionaryConfig = quickDicConfig.dictionaryConfigs.get(intent.getIntExtra(C.DICT_INDEX, 0));

    // Write stuff from object into fields.
    
    ((EditText) findViewById(R.id.dictionaryName)).setText(dictionaryConfig.name);
    ((EditText) findViewById(R.id.localFile)).setText(dictionaryConfig.localFile);
    ((EditText) findViewById(R.id.wordListFile)).setText(dictionaryConfig.wordList);
    ((EditText) findViewById(R.id.downloadUrl)).setText(dictionaryConfig.downloadUrl);
    
    updateDictInfo();
    ((EditText) findViewById(R.id.localFile)).addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
      
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }
      
      @Override
      public void afterTextChanged(Editable s) {
        updateDictInfo();
      }
    });
    
  }

  @Override
  protected void onPause() {
    super.onPause();
    
    // Read stuff from fields into object.
    dictionaryConfig.name = ((EditText) findViewById(R.id.dictionaryName)).getText().toString();
    dictionaryConfig.localFile = ((EditText) findViewById(R.id.localFile)).getText().toString();
    dictionaryConfig.wordList = ((EditText) findViewById(R.id.wordListFile)).getText().toString();
    dictionaryConfig.downloadUrl = ((EditText) findViewById(R.id.downloadUrl)).getText().toString();
    
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
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
  
  void updateDictInfo() {
    final TextView dictInfo = (TextView) findViewById(R.id.dictionaryInfo);
    final String localFile = ((EditText) findViewById(R.id.localFile)).getText().toString();
    
    if (!new File(localFile).canRead()) {
      dictInfo.setText(getString(R.string.fileNotFound, localFile));
      return;
    }
    
    try {
      final RandomAccessFile raf = new RandomAccessFile(localFile, "r");
      final Dictionary dict = new Dictionary(raf);
      final StringBuilder builder = new StringBuilder();
      builder.append(dict.dictInfo).append("\n");
      builder.append(getString(R.string.numPairEntries, dict.pairEntries.size())).append("\n");
      for (final Index index : dict.indices) {
        builder.append("\n");
        builder.append(index.longName).append("\n");
        builder.append("  ").append(getString(R.string.numTokens, index.sortedIndexEntries.size())).append("\n");
        builder.append("  ").append(getString(R.string.numRows, index.rows.size())).append("\n");
      }
      raf.close();
      dictInfo.setText(builder.toString());
    } catch (IOException e) {
      dictInfo.setText(getString(R.string.invalidDictionary, localFile, e.toString()));
    }
  }

  static void startDownloadDictActivity(final Context context, final DictionaryConfig dictionaryConfig) {
    final Intent intent = new Intent(context, DownloadActivity.class);
    intent.putExtra(DownloadActivity.SOURCE, dictionaryConfig.downloadUrl);
    intent.putExtra(DownloadActivity.DEST, dictionaryConfig.localFile);
    context.startActivity(intent);
  }

}
