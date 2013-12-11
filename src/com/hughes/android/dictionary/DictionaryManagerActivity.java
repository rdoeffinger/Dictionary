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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.widget.SearchView;
import com.actionbarsherlock.widget.SearchView.OnQueryTextListener;
import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Language;
import com.hughes.android.dictionary.engine.Language.LanguageResources;
import com.hughes.android.util.IntentLauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DictionaryManagerActivity extends SherlockActivity {

    static final String LOG = "QuickDic";
    static boolean blockAutoLaunch = false;

    DictionaryApplication application;
//    Adapter adapter;

    // EditText filterText;
    SearchView filterSearchView;
    ToggleButton hideDownloadable;

    LinearLayout dictionariesOnDevice;
    LinearLayout downloadableDictionaries;

    Handler uiHandler;

    public static Intent getLaunchIntent() {
        final Intent intent = new Intent();
        intent.setClassName(DictionaryManagerActivity.class.getPackage().getName(),
                DictionaryManagerActivity.class.getName());
        intent.putExtra(C.CAN_AUTO_LAUNCH_DICT, false);
        return intent;
    }

    public void onCreate(Bundle savedInstanceState) {
        setTheme(((DictionaryApplication) getApplication()).getSelectedTheme().themeId);

        super.onCreate(savedInstanceState);
        Log.d(LOG, "onCreate:" + this);

        application = (DictionaryApplication) getApplication();

        // UI init.
        setContentView(R.layout.dictionary_manager_activity);

        dictionariesOnDevice = (LinearLayout) findViewById(R.id.dictionariesOnDeviceGoHere);
        downloadableDictionaries = (LinearLayout) findViewById(R.id.downloadableDictionariesGoHere);

        // filterText = (EditText) findViewById(R.id.filterText);
        //
        // filterText.addTextChangedListener(new TextWatcher() {
        // @Override
        // public void onTextChanged(CharSequence s, int start, int before, int
        // count) {
        // }
        //
        // @Override
        // public void beforeTextChanged(CharSequence s, int start, int count,
        // int after) {
        // }
        //
        // @Override
        // public void afterTextChanged(Editable s) {
        // onFilterTextChanged();
        // }
        // });

        // final ImageButton clearSearchText = (ImageButton)
        // findViewById(R.id.ClearSearchTextButton);
        // clearSearchText.setOnClickListener(new View.OnClickListener() {
        // @Override
        // public void onClick(View arg0) {
        // filterText.setText("");
        // filterText.requestFocus();
        // }
        // });

        hideDownloadable = (ToggleButton) findViewById(R.id.hideDownloadable);
        hideDownloadable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onShowLocalChanged();
            }
        });

        // ContextMenu.
        // registerForContextMenu(getListView());

        blockAutoLaunch = false;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String thanksForUpdatingLatestVersion = getString(R.string.thanksForUpdatingVersion);
        if (!prefs.getString(C.THANKS_FOR_UPDATING_VERSION, "").equals(
                thanksForUpdatingLatestVersion)) {
            blockAutoLaunch = true;
            startActivity(HtmlDisplayActivity.getWhatsNewLaunchIntent());
            prefs.edit().putString(C.THANKS_FOR_UPDATING_VERSION, thanksForUpdatingLatestVersion)
                    .commit();
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
        hideDownloadable.setChecked(prefs.getBoolean(C.SHOW_LOCAL, false));

        if (!blockAutoLaunch &&
                getIntent().getBooleanExtra(C.CAN_AUTO_LAUNCH_DICT, true) &&
                prefs.contains(C.DICT_FILE) &&
                prefs.contains(C.INDEX_SHORT_NAME)) {
            Log.d(LOG, "Skipping Dictionary List, going straight to dictionary.");
            startActivity(DictionaryActivity.getLaunchIntent(
                    new File(prefs.getString(C.DICT_FILE, "")), prefs.getString(C.INDEX_SHORT_NAME, ""),
                    prefs.getString(C.SEARCH_TOKEN, "")));
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
                        populateDictionaryLists("");
                    }
                });
            }
        });

        populateDictionaryLists("");
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.dictionary_manager_options_menu, menu);
        
        filterSearchView = (SearchView) menu.findItem(R.id.filterText).getActionView();
        filterSearchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String filterText) {
                populateDictionaryLists(filterText);
                return true;
            }
        });

        application.onCreateGlobalOptionsMenu(this, menu);
        return true;
    }

        // @Override
        // public void onCreateContextMenu(final ContextMenu menu, final View
        // view,
        // final ContextMenuInfo menuInfo) {
        // super.onCreateContextMenu(menu, view, menuInfo);
        //
        // final AdapterContextMenuInfo adapterContextMenuInfo =
        // (AdapterContextMenuInfo) menuInfo;
        // final int position = adapterContextMenuInfo.position;
        // final DictionaryInfo dictionaryInfo = adapter.getItem(position);
        //
        // if (position > 0 &&
        // application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename))
        // {
        // final android.view.MenuItem moveToTopMenuItem =
        // menu.add(R.string.moveToTop);
        // moveToTopMenuItem.setOnMenuItemClickListener(new
        // android.view.MenuItem.OnMenuItemClickListener() {
        // @Override
        // public boolean onMenuItemClick(android.view.MenuItem item) {
        // application.moveDictionaryToTop(dictionaryInfo);
        // setListAdapter(adapter = new Adapter());
        // return true;
        // }
        // });
        // }
//
//        final android.view.MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
//        deleteMenuItem
//                .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
//                    @Override
//                    public boolean onMenuItemClick(android.view.MenuItem item) {
//                        application.deleteDictionary(dictionaryInfo);
//                        setListAdapter(adapter = new Adapter());
//                        return true;
//                    }
//                });
//
//        final DictionaryInfo downloadable = application
//                .getDownloadable(dictionaryInfo.uncompressedFilename);
//        if (downloadable != null) {
//            final android.view.MenuItem downloadMenuItem = menu.add(getString(
//                    R.string.downloadButton, downloadable.zipBytes / 1024.0 / 1024.0));
//            downloadMenuItem
//                    .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
//                        @Override
//                        public boolean onMenuItemClick(android.view.MenuItem item) {
//                            final Intent intent = getDownloadIntent(downloadable);
//                            startActivity(intent);
//                            setListAdapter(adapter = new Adapter());
//                            return true;
//                        }
//                    });
//        }
//
//    }

    private Intent getDownloadIntent(final DictionaryInfo downloadable) {
        // DownloadManager downloadManager = (DownloadManager)
        // getSystemService(DOWNLOAD_SERVICE);
        // DownloadManager.Request request = new
        // DownloadManager.Request(Uri.parse(""));
        // long id = downloadManager.enqueue(request);
        // DownloadManager.Query query;
        return null;
    }

    private void onShowLocalChanged() {
        downloadableDictionaries.setVisibility(hideDownloadable.isChecked() ? View.GONE
                : View.VISIBLE);
        Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
        prefs.putBoolean(C.SHOW_LOCAL, hideDownloadable.isChecked());
        prefs.commit();
    }

    // private void onClick(int index) {
    // final DictionaryInfo dictionaryInfo = adapter.getItem(index);
    // final DictionaryInfo downloadable =
    // application.getDownloadable(dictionaryInfo.uncompressedFilename);
    // if
    // (!application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename)
    // && downloadable != null) {
    // final Intent intent = getDownloadIntent(downloadable);
    // startActivity(intent);
    // } else {
    // final Intent intent =
    // DictionaryActivity.getLaunchIntent(application.getPath(dictionaryInfo.uncompressedFilename),
    // 0, "");
    // startActivity(intent);
    // }
    // }

    private void populateDictionaryLists(String filterText) {
        // On device.
        dictionariesOnDevice.removeAllViews();
        final List<DictionaryInfo> dictionaryInfos = application.getDictionariesOnDevice();
        for (final DictionaryInfo dictionaryInfo : dictionaryInfos) {
            View row = LayoutInflater.from(dictionariesOnDevice.getContext()).inflate(
                    R.layout.dictionary_on_device_row, dictionariesOnDevice, false);
            final TextView name = (TextView) row.findViewById(R.id.dictionaryName);
            name.setText(application.getDictionaryName(dictionaryInfo.uncompressedFilename));
            
            LinearLayout buttons = (LinearLayout) row.findViewById(R.id.dictionaryLauncherButtons);
            final List<IndexInfo> sortedIndexInfos = application.sortedIndexInfos(dictionaryInfo.indexInfos);
            for (IndexInfo indexInfo : sortedIndexInfos) {
                final View button = application.createButton(buttons.getContext(), dictionaryInfo, indexInfo);
                buttons.addView(button);
            }
            
            dictionariesOnDevice.addView(row);
        }

        // Downloadable.

    }

//    class Adapter extends BaseAdapter {
//
//        final List<DictionaryInfo> dictionaryInfos = new ArrayList<DictionaryInfo>();
//
//        Adapter() {
//            final String filter = filterSearchView.getText().toString().trim().toLowerCase();
//            for (final DictionaryInfo dictionaryInfo : application.getAllDictionaries()) {
//                boolean canShow = true;
//                if (hideDownloadable.isChecked()
//                        && !application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename)) {
//                    canShow = false;
//                }
//                if (canShow && filter.length() > 0) {
//                    if (!application.getDictionaryName(dictionaryInfo.uncompressedFilename)
//                            .toLowerCase().contains(filter)) {
//                        canShow = false;
//                    }
//                }
//                if (canShow) {
//                    dictionaryInfos.add(dictionaryInfo);
//                }
//            }
//        }
//
//        @Override
//        public int getCount() {
//            return dictionaryInfos.size();
//        }
//
//        @Override
//        public DictionaryInfo getItem(int position) {
//            return dictionaryInfos.get(position);
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public View getView(final int position, View convertView, final ViewGroup parent) {
//            if (convertView == null) {
//                convertView = LayoutInflater.from(parent.getContext()).inflate(
//                        R.layout.dictionary_manager_row, parent, false);
//            }
//
//            final DictionaryInfo dictionaryInfo = getItem(position);
//
//            final TextView textView = (TextView) convertView.findViewById(R.id.dictionaryName);
//            final String name = application.getDictionaryName(dictionaryInfo.uncompressedFilename);
//            textView.setText(name);
//
//            final Button downloadButton = (Button) convertView
//                    .findViewById(R.id.dictionaryDownloadButton);
//            final boolean updateAvailable = application.updateAvailable(dictionaryInfo);
//            final DictionaryInfo downloadable = application
//                    .getDownloadable(dictionaryInfo.uncompressedFilename);
//            if (updateAvailable) {
//                downloadButton.setCompoundDrawablesWithIntrinsicBounds(
//                        android.R.drawable.ic_menu_add,
//                        0, 0, 0);
//                downloadButton.setText(getString(R.string.downloadButton,
//                        downloadable.zipBytes / 1024.0 / 1024.0));
//            } else if (!application.isDictionaryOnDevice(dictionaryInfo.uncompressedFilename)) {
//                downloadButton.setCompoundDrawablesWithIntrinsicBounds(
//                        android.R.drawable.ic_menu_add,
//                        0, 0, 0);
//                downloadButton.setText(getString(R.string.downloadButton,
//                        downloadable.zipBytes / 1024.0 / 1024.0));
//            } else {
//                downloadButton.setCompoundDrawablesWithIntrinsicBounds(
//                        android.R.drawable.checkbox_on_background,
//                        0, 0, 0);
//                downloadButton.setText("");
//            }
//            final Intent intent = getDownloadIntent(downloadable);
//            downloadButton.setOnClickListener(new IntentLauncher(parent.getContext(), intent));
//
//            // Add the information about each index.
//            final TextView dictionaryDetails = (TextView) convertView
//                    .findViewById(R.id.dictionaryDetails);
//            final StringBuilder builder = new StringBuilder();
//            for (final IndexInfo indexInfo : dictionaryInfo.indexInfos) {
//                if (builder.length() > 0) {
//                    builder.append(" | ");
//                }
//                builder.append(getString(R.string.indexInfo, indexInfo.shortName,
//                        indexInfo.mainTokenCount));
//            }
//            dictionaryDetails.setText(builder.toString());
//
//            // // Because we have a Button inside a ListView row:
//            // //
//            // http://groups.google.com/group/android-developers/browse_thread/thread/3d96af1530a7d62a?pli=1
//            // convertView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
//            convertView.setClickable(true);
//            convertView.setFocusable(true);
//            convertView.setLongClickable(true);
//            // result.setBackgroundResource(android.R.drawable.menuitem_background);
//            convertView.setOnClickListener(new TextView.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    DictionaryManagerActivity.this.onClick(position);
//                }
//            });
//
//            return convertView;
//        }
//    }

}
