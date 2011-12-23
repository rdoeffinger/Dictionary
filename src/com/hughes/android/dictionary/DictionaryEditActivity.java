// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Index;
import com.hughes.android.util.PersistentObjectCache;

public class DictionaryEditActivity extends Activity {

  static final String LOG = "QuickDic";

  QuickDicConfig quickDicConfig;
  private DictionaryConfig dictionaryConfig;
  
  final Handler uiHandler = new Handler();

  public static Intent getIntent(final int dictIndex) {
    final Intent intent = new Intent();
    intent.setClassName(DictionaryEditActivity.class.getPackage().getName(),
        DictionaryEditActivity.class.getName());
    intent.putExtra(C.DICT_INDEX, dictIndex);
    return intent;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    ((DictionaryApplication)getApplication()).applyTheme(this);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.edit_activity);

    final Intent intent = getIntent();

    final int dictIndex = intent.getIntExtra(C.DICT_INDEX, 0);
      
    PersistentObjectCache.init(this);
    try {
      quickDicConfig = PersistentObjectCache.init(this).read(
          C.DICTIONARY_CONFIGS, QuickDicConfig.class);
      dictionaryConfig = quickDicConfig.dictionaryConfigs.get(dictIndex);
    } catch (Exception e) {
      Log.e(LOG, "Failed to read QuickDicConfig.", e);
      quickDicConfig = new QuickDicConfig();
      dictionaryConfig = quickDicConfig.dictionaryConfigs.get(0);
    }
    
    // Write stuff from object into fields.

    ((EditText) findViewById(R.id.dictionaryName))
        .setText(dictionaryConfig.name);
    ((EditText) findViewById(R.id.localFile))
        .setText(dictionaryConfig.localFile);

    final TextWatcher textWatcher = new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before,
          int count) {
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
          int after) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        updateDictInfo();
      }
    };

    ((EditText) findViewById(R.id.localFile)).addTextChangedListener(textWatcher);

    final EditText downloadUrl = (EditText) findViewById(R.id.downloadUrl);
    downloadUrl.setText(dictionaryConfig.downloadUrl);
    downloadUrl.addTextChangedListener(textWatcher);
    
    final Button downloadButton = (Button) findViewById(R.id.downloadButton);
    downloadButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        startDownloadDictActivity(DictionaryEditActivity.this,
            dictionaryConfig);
      }
    });

    final Button openButton = (Button) findViewById(R.id.openButton);
    openButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        final Intent intent = DictionaryActivity.getIntent(DictionaryEditActivity.this, dictIndex, 0, "");
        startActivity(intent);
      }
    });

  }
  
  protected void onResume() {
    super.onResume();

    updateDictInfo();

    // Focus the download button so the keyboard doesn't pop up.
    final Button downloadButton = (Button) findViewById(R.id.downloadButton);
    downloadButton.requestFocus();
  }

  @Override
  protected void onPause() {
    super.onPause();

    // Read stuff from fields into object.
    dictionaryConfig.name = ((EditText) findViewById(R.id.dictionaryName))
        .getText().toString();
    dictionaryConfig.localFile = ((EditText) findViewById(R.id.localFile))
        .getText().toString();
    dictionaryConfig.downloadUrl = ((EditText) findViewById(R.id.downloadUrl))
        .getText().toString();

    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS,
        quickDicConfig);
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem newDictionaryMenuItem = menu
        .add(R.string.downloadDictionary);
    newDictionaryMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            startDownloadDictActivity(DictionaryEditActivity.this,
                dictionaryConfig);
            return false;
          }
        });
    
    final MenuItem dictionaryList = menu.add(getString(R.string.dictionaryList));
    dictionaryList.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        startActivity(DictionaryListActivity.getIntent(DictionaryEditActivity.this));
        return false;
      }
    });


    return true;
  }

  void updateDictInfo() {
    final String downloadUrl = ((EditText) findViewById(R.id.downloadUrl))
        .getText().toString();
    final String localFile = ((EditText) findViewById(R.id.localFile))
        .getText().toString();

    final Button downloadButton = (Button) findViewById(R.id.downloadButton);
    downloadButton.setEnabled(downloadUrl.length() > 0 && localFile.length() > 0);

    final Button openButton = (Button) findViewById(R.id.openButton);
    openButton.setEnabled(false);

    final TextView dictInfo = (TextView) findViewById(R.id.dictionaryInfo);
    if (!new File(localFile).canRead()) {
      dictInfo.setText(getString(R.string.fileNotFound, localFile));
      return;
    }

    try {
      final RandomAccessFile raf = new RandomAccessFile(localFile, "r");
      final Dictionary dict = new Dictionary(raf);
      final StringBuilder builder = new StringBuilder();
      builder.append(dict.dictInfo).append("\n");
      builder.append(
          getString(R.string.numPairEntries, dict.pairEntries.size())).append(
          "\n");
      for (final Index index : dict.indices) {
        builder.append("\n");
        builder.append(index.longName).append("\n");
        builder.append("  ").append(
            getString(R.string.numTokens, index.sortedIndexEntries.size()))
            .append("\n");
        builder.append("  ").append(
            getString(R.string.numRows, index.rows.size())).append("\n");
      }
      raf.close();
      dictInfo.setText(builder.toString());
      openButton.setEnabled(true);
      
    } catch (IOException e) {
      dictInfo.setText(getString(R.string.invalidDictionary, localFile, e
          .toString()));
    }
  }

  static void startDownloadDictActivity(final Context context,
      final DictionaryConfig dictionaryConfig) {
    final Intent intent = new Intent(context, DownloadActivity.class);
    intent.putExtra(DownloadActivity.SOURCE, dictionaryConfig.downloadUrl);
    intent.putExtra(DownloadActivity.DEST, dictionaryConfig.localFile + ".zip");
    context.startActivity(intent);
  }
  
  @Override
  public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      Log.d(LOG, "Clearing dictionary prefs.");
      DictionaryActivity.clearDictionaryPrefs(this);
    }
    return super.onKeyDown(keyCode, event);
  }

}
