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

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.util.IntentLauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DictionaryManagerActivity extends ListActivity {

  static final String LOG = "QuickDic";
  static boolean blockAutoLaunch = false;

  DictionaryApplication application;
  Adapter adapter;
  
  EditText filterText;
  CheckBox showLocal;
  
  Handler uiHandler;
  
  public static Intent getLaunchIntent() {
    final Intent intent = new Intent();
    intent.setClassName(DictionaryManagerActivity.class.getPackage().getName(),
        DictionaryManagerActivity.class.getName());
    intent.putExtra(C.CAN_AUTO_LAUNCH_DICT, false);
    return intent;
  }
  
  public void onCreate(Bundle savedInstanceState) {
    setTheme(((DictionaryApplication)getApplication()).getSelectedTheme().themeId);

    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);
    
    application = (DictionaryApplication) getApplication();

    // UI init.
    setContentView(R.layout.dictionary_manager_activity);
    
    filterText = (EditText) findViewById(R.id.filterText);
    showLocal = (CheckBox) findViewById(R.id.showLocal);
    
    filterText.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
      
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }
      
      @Override
      public void afterTextChanged(Editable s) {
        onFilterTextChanged();
      }
    });
    
    showLocal.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        onShowLocalChanged();
      }
    });

    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int index,
          long id) {
        onClick(index);
      }
    });
    
    getListView().setClickable(true);

    // ContextMenu.
    registerForContextMenu(getListView());

    blockAutoLaunch = false;
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final String thanksForUpdatingLatestVersion = getString(R.string.thanksForUpdatingVersion);
    if (!prefs.getString(C.THANKS_FOR_UPDATING_VERSION, "").equals(thanksForUpdatingLatestVersion)) {
      blockAutoLaunch = true;
      startActivity(HtmlDisplayActivity.getWhatsNewLaunchIntent());
      prefs.edit().putString(C.THANKS_FOR_UPDATING_VERSION, thanksForUpdatingLatestVersion).commit();
    }
  }
  
  @Override
  protected void onStart() {
    super.onStart();
    uiHandler = new Handler();
  }
  
  @Override
  protected void onStop() {
    super.onStop();
    uiHandler = null;
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    
    if (PreferenceActivity.prefsMightHaveChanged) {
      PreferenceActivity.prefsMightHaveChanged = false;
      finish();
      startActivity(getIntent());
    }
    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    showLocal.setChecked(prefs.getBoolean(C.SHOW_LOCAL, false));
    
    if (!blockAutoLaunch &&
        getIntent().getBooleanExtra(C.CAN_AUTO_LAUNCH_DICT, true) &&
        prefs.contains(C.DICT_FILE) && 
        prefs.contains(C.INDEX_INDEX)) {
      Log.d(LOG, "Skipping Dictionary List, going straight to dictionary.");
      startActivity(DictionaryActivity.getLaunchIntent(new File(prefs.getString(C.DICT_FILE, "")), prefs.getInt(C.INDEX_INDEX, 0), prefs.getString(C.SEARCH_TOKEN, "")));
      finish();
      return;
    }
    
    application.backgroundUpdateDictionaries(new Runnable() {
      @Override
      public void run() {
        if (uiHandler == null) {
          return;
        }
        uiHandler.post(new Runnable() {
          @Override
          public void run() {
            setListAdapter(adapter = new Adapter());
          }
        });
      }
    });

    setListAdapter(adapter = new Adapter());
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    application.onCreateGlobalOptionsMenu(this, menu);
    return true;
  }
  

  @Override
  public void onCreateContextMenu(final ContextMenu menu, final View view,
      final ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, view, menuInfo);
    
    final AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
    final int position = adapterContextMenuInfo.position;
    final DictionaryInfo dictionaryInfo = adapter.getItem(position);
    
    if (position > 0 && application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename)) {
      final MenuItem moveToTopMenuItem = menu.add(R.string.moveToTop);
      moveToTopMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          application.moveDictionaryToTop(dictionaryInfo);
          setListAdapter(adapter = new Adapter());
          return true;
        }
      });
    }

    final MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
    deleteMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        application.deleteDictionary(dictionaryInfo);
        setListAdapter(adapter = new Adapter());
        return true;
      }
    });

    final DictionaryInfo downloadable = application.getDownloadable(dictionaryInfo.uncompressedFilename);
    if (downloadable != null) {
      final MenuItem downloadMenuItem = menu.add(getString(R.string.downloadButton, downloadable.zipBytes/1024.0/1024.0));
      downloadMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          final Intent intent = getDownloadIntent(downloadable);
          startActivity(intent);
          setListAdapter(adapter = new Adapter());
          return true;
        }
      });
    }

  }

  private Intent getDownloadIntent(final DictionaryInfo downloadable) {
    final Intent intent = DownloadActivity.getLaunchIntent(downloadable.downloadUrl,
        application.getPath(downloadable.uncompressedFilename).getPath() + ".zip",
        downloadable.dictInfo);
    return intent;
  }
  
  private void onFilterTextChanged() {
    setListAdapter(adapter = new Adapter());

  }

  private void onShowLocalChanged() {
    setListAdapter(adapter = new Adapter());
    Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
    prefs.putBoolean(C.SHOW_LOCAL, showLocal.isChecked());
    prefs.commit();
  }
  
  private void onClick(int index) {
    final DictionaryInfo dictionaryInfo = adapter.getItem(index);
    final DictionaryInfo downloadable = application.getDownloadable(dictionaryInfo.uncompressedFilename);
    if (!application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename) && downloadable != null) {
      final Intent intent = getDownloadIntent(downloadable);
      startActivity(intent);
    } else {
      final Intent intent = DictionaryActivity.getLaunchIntent(application.getPath(dictionaryInfo.uncompressedFilename), 0, "");
      startActivity(intent);
    }
  }
  
  class Adapter extends BaseAdapter {
    
    final List<DictionaryInfo> dictionaryInfos = new ArrayList<DictionaryInfo>();
    
    Adapter() {
      final String filter = filterText.getText().toString().trim().toLowerCase();
      for (final DictionaryInfo dictionaryInfo : application.getAllDictionaries()) {
        boolean canShow = true;
        if (showLocal.isChecked() && !application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename)) {
          canShow = false;
        }
        if (canShow && filter.length() > 0) {
          if (!application.getDictionaryName(dictionaryInfo.uncompressedFilename).toLowerCase().contains(filter)) {
            canShow = false;
          }
        }
        if (canShow) {
          dictionaryInfos.add(dictionaryInfo);
          
        }
      }
    }

    @Override
    public int getCount() {
      return dictionaryInfos.size();
    }

    @Override
    public DictionaryInfo getItem(int position) {
      return dictionaryInfos.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
      final LinearLayout result;
      // Android 4.0.3 leaks memory like crazy if we don't do this.
      if (convertView instanceof LinearLayout) {
        result = (LinearLayout) convertView;
        result.removeAllViews();
      } else {
        result = new LinearLayout(parent.getContext());
      }
      
      final DictionaryInfo dictionaryInfo = getItem(position);
      result.setOrientation(LinearLayout.VERTICAL);

      final LinearLayout row = new LinearLayout(parent.getContext());
      row.setOrientation(LinearLayout.HORIZONTAL);
      result.addView(row);

      {
      final TextView textView = new TextView(parent.getContext());
      final String name = application.getDictionaryName(dictionaryInfo.uncompressedFilename);
      textView.setText(name);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      row.addView(textView);
      LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
      layoutParams.weight = 1.0f;
      textView.setLayoutParams(layoutParams);
      }
      
      final boolean updateAvailable = application.updateAvailable(dictionaryInfo);
      final DictionaryInfo downloadable = application.getDownloadable(dictionaryInfo.uncompressedFilename); 
      if ((!application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename) || updateAvailable) && downloadable != null) {
        final Button downloadButton = new Button(parent.getContext());
        downloadButton.setText(getString(updateAvailable ? R.string.updateButton : R.string.downloadButton, downloadable.zipBytes / 1024.0 / 1024.0));
        final Intent intent = getDownloadIntent(downloadable);
        downloadButton.setOnClickListener(new IntentLauncher(parent.getContext(), intent));
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        downloadButton.setLayoutParams(layoutParams);
        row.addView(downloadButton);
      } else {
        final ImageView checkMark = new ImageView(parent.getContext());
        checkMark.setImageResource(R.drawable.btn_check_buttonless_on);
        row.addView(checkMark);
      }

      // Add the information about each index.
      final LinearLayout row2 = new LinearLayout(parent.getContext());
      row2.setOrientation(LinearLayout.HORIZONTAL);
      final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      row2.setLayoutParams(layoutParams);
      result.addView(row2);
      final StringBuilder builder = new StringBuilder();
      for (final IndexInfo indexInfo : dictionaryInfo.indexInfos) {
        if (builder.length() > 0) {
          builder.append(" | ");
        }
        builder.append(getString(R.string.indexInfo, indexInfo.shortName, indexInfo.mainTokenCount));
      }
      final TextView indexView = new TextView(parent.getContext());
      indexView.setText(builder.toString());
      row2.addView(indexView);
      
      
      // Because we have a Button inside a ListView row:
      // http://groups.google.com/group/android-developers/browse_thread/thread/3d96af1530a7d62a?pli=1
      result.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
      result.setClickable(true);
      result.setFocusable(true);
      result.setLongClickable(true);
      result.setBackgroundResource(android.R.drawable.menuitem_background);
      result.setOnClickListener(new TextView.OnClickListener() {
        @Override
        public void onClick(View v) {
          DictionaryManagerActivity.this.onClick(position);
        }
      });
      
      return result;
    }
  }

}
