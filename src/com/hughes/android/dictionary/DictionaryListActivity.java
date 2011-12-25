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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.hughes.android.util.PersistentObjectCache;

public class DictionaryListActivity extends ListActivity {

  static final String LOG = "QuickDic";
  
  QuickDicConfig quickDicConfig = new QuickDicConfig();
  
  
  public void onCreate(Bundle savedInstanceState) {
    ((DictionaryApplication)getApplication()).applyTheme(this);

    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);

    // UI init.
    setContentView(R.layout.list_activity);

    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int index,
          long id) {
        onClick(index);
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final int introMessageId = -1;
    if (prefs.getInt(C.INTRO_MESSAGE_SHOWN, 0) < introMessageId) {
      final AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setCancelable(false);
      final WebView webView = new WebView(getApplicationContext());
      webView.loadData(getString(R.string.thanksForUpdating), "text/html", "utf-8");
      builder.setView(webView);
      builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
               dialog.cancel();
          }
      });
      final AlertDialog alert = builder.create();
      alert.show();
      prefs.edit().putInt(C.INTRO_MESSAGE_SHOWN, introMessageId).commit();
    }
  }
  
  private void onClick(int dictIndex) {
    final Intent intent = DictionaryActivity.getIntent(this, dictIndex, 0, "");
    startActivity(intent);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    if (prefs.contains(C.DICT_INDEX) && prefs.contains(C.INDEX_INDEX)) {
      Log.d(LOG, "Skipping Dictionary List, going straight to dictionary.");
      startActivity(DictionaryActivity.getIntent(this, prefs.getInt(C.DICT_INDEX, 0), prefs.getInt(C.INDEX_INDEX, 0), prefs.getString(C.SEARCH_TOKEN, "")));
      //finish();
      return;
    }

    quickDicConfig = PersistentObjectCache.init(this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);
    if (quickDicConfig == null) {
      quickDicConfig = new QuickDicConfig();
      PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
    }
    if (quickDicConfig.currentVersion < QuickDicConfig.LATEST_VERSION) {
      Log.d(LOG, "Dictionary list is old, updating it.");
      quickDicConfig.addDefaultDictionaries();
      quickDicConfig.currentVersion = QuickDicConfig.LATEST_VERSION;
    }

    setListAdapter(new Adapter());
    
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem newDictionaryMenuItem = menu.add(R.string.addDictionary);
    newDictionaryMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            final DictionaryConfig dictionaryConfig = new DictionaryConfig();
            dictionaryConfig.name = getString(R.string.newDictionary);
            quickDicConfig.dictionaryConfigs.add(0, dictionaryConfig);
            dictionaryConfigsChanged();
            return false;
          }
        });

    final MenuItem addDefaultDictionariesMenuItem = menu.add(R.string.addDefaultDictionaries);
    addDefaultDictionariesMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            quickDicConfig.addDefaultDictionaries();
            dictionaryConfigsChanged();
            return false;
          }
        });

    final MenuItem removeAllDictionariesMenuItem = menu.add(R.string.removeAllDictionaries);
    removeAllDictionariesMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            quickDicConfig.dictionaryConfigs.clear();
            dictionaryConfigsChanged();
            return false;
          }
        });

    final MenuItem about = menu.add(getString(R.string.about));
    about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class
            .getPackage().getName(), AboutActivity.class.getCanonicalName());
        startActivity(intent);
        return false;
      }
    });
    
    final MenuItem preferences = menu.add(getString(R.string.preferences));
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        startActivity(new Intent(DictionaryListActivity.this,
            PreferenceActivity.class));
        return false;
      }
    });
    
    return true;
  }
  

  @Override
  public void onCreateContextMenu(final ContextMenu menu, final View view,
      final ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, view, menuInfo);
    
    final AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
    
    final MenuItem editMenuItem = menu.add(R.string.editDictionary);
    editMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        startActivity(DictionaryEditActivity.getIntent(adapterContextMenuInfo.position));
        return true;
      }
    });

    if (adapterContextMenuInfo.position > 0) {
      final MenuItem moveToTopMenuItem = menu.add(R.string.moveToTop);
      moveToTopMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          final DictionaryConfig dictionaryConfig = quickDicConfig.dictionaryConfigs.remove(adapterContextMenuInfo.position);
          quickDicConfig.dictionaryConfigs.add(0, dictionaryConfig);
          dictionaryConfigsChanged();
          return true;
        }
      });
    }

    final MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
    deleteMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        quickDicConfig.dictionaryConfigs.remove(adapterContextMenuInfo.position);
        dictionaryConfigsChanged();
        return true;
      }
    });

  }

  private void dictionaryConfigsChanged() {
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
    setListAdapter(getListAdapter());
  }

  class Adapter extends BaseAdapter {

    @Override
    public int getCount() {
      return quickDicConfig.dictionaryConfigs.size();
    }

    @Override
    public DictionaryConfig getItem(int position) {
      return quickDicConfig.dictionaryConfigs.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final DictionaryConfig dictionaryConfig = getItem(position);
      final TableLayout tableLayout = new TableLayout(parent.getContext());
      final TextView view = new TextView(parent.getContext());
      
      String name = dictionaryConfig.name;
      if (!new File(dictionaryConfig.localFile).canRead()) {
        name = getString(R.string.notOnDevice, dictionaryConfig.name);
      }

      view.setText(name);
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      tableLayout.addView(view);

      return tableLayout;
    }
    
  }

  public static Intent getIntent(final Context context) {
    DictionaryActivity.clearDictionaryPrefs(context);
    final Intent intent = new Intent();
    intent.setClassName(DictionaryListActivity.class.getPackage().getName(),
        DictionaryListActivity.class.getName());
    return intent;
  }

}
