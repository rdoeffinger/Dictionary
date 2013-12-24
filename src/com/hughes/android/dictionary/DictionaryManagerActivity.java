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

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.widget.SearchView;
import com.actionbarsherlock.widget.SearchView.OnQueryTextListener;
import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.util.IntentLauncher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Filters
// Right-click:
//  Delete, move to top.

public class DictionaryManagerActivity extends SherlockListActivity {

    static final String LOG = "QuickDic";
    static boolean blockAutoLaunch = false;

    DictionaryApplication application;
//    Adapter adapter;

    // EditText filterText;
    SearchView filterSearchView;
    ToggleButton showDownloadable;

    LinearLayout dictionariesOnDeviceHeaderRow;
    LinearLayout downloadableDictionariesHeaderRow;

    Handler uiHandler;
    
    Runnable dictionaryUpdater = new Runnable() {
        @Override
        public void run() {
            if (uiHandler == null) {
                return;
            }
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    setListAdapater();
                }
            });
        }
    };

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

        dictionariesOnDeviceHeaderRow = (LinearLayout) LayoutInflater.from(getListView().getContext()).inflate(
                R.layout.dictionaries_on_device_header_row, getListView(), false);

        downloadableDictionariesHeaderRow = (LinearLayout) LayoutInflater.from(getListView().getContext()).inflate(
                R.layout.downloadable_dictionaries_header_row, getListView(), false);

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

        showDownloadable = (ToggleButton) downloadableDictionariesHeaderRow.findViewById(R.id.hideDownloadable);
        showDownloadable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
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
        
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    final long downloadId = intent.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    final DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    final DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    final Cursor cursor = downloadManager.query(query);
                    
                    if (!cursor.moveToFirst()) {
                        Log.e(LOG, "Couldn't find download.");
                        return;
                    }
                    
                    final int status = cursor
                            .getInt(cursor
                                    .getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (DownloadManager.STATUS_SUCCESSFUL != status){
                    Log.w(LOG, "Download failed: status=" + status + ", reason=" + cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)));
                    return;
                }

                final String dest = cursor
                        .getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                Log.w(LOG, "Download finished: " + dest);
                final File destFile = new File(Uri.parse(dest).getPath());
                
                try {
                    ZipFile zipFile = new ZipFile(destFile);
                    final ZipEntry zipEntry = zipFile.entries().nextElement();
                    Log.d(LOG, "Unzipping entry: " + zipEntry.getName());
                    final InputStream zipIn = zipFile.getInputStream(zipEntry);
                    final OutputStream zipOut = new FileOutputStream(new File(application.getDictDir(), zipEntry.getName()));
                    copyStream(zipIn, zipOut);
                    destFile.delete();
                    zipFile.close();
                    application.backgroundUpdateDictionaries(dictionaryUpdater);
                } catch (Exception e) {
                    Log.e(LOG, "Failed to unzip.", e);
                }
              }
            }
        };
 
        registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        
        setListAdapater();
    }
    
    private static int copyStream(final InputStream in, final OutputStream out)
            throws IOException {
        int bytesRead;
        final byte[] bytes = new byte[1024 * 16];
        while ((bytesRead = in.read(bytes)) != -1) {
            out.write(bytes, 0, bytesRead);
        }
        in.close();
        out.close();
        return bytesRead;
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
        showDownloadable.setChecked(prefs.getBoolean(C.SHOW_DOWNLOADABLE, false));

        if (!blockAutoLaunch &&
                getIntent().getBooleanExtra(C.CAN_AUTO_LAUNCH_DICT, true) &&
                prefs.contains(C.DICT_FILE) &&
                prefs.contains(C.INDEX_SHORT_NAME)) {
            Log.d(LOG, "Skipping DictionaryManager, going straight to dictionary.");
            startActivity(DictionaryActivity.getLaunchIntent(
                    new File(prefs.getString(C.DICT_FILE, "")), prefs.getString(C.INDEX_SHORT_NAME, ""),
                    prefs.getString(C.SEARCH_TOKEN, "")));
            finish();
            return;
        }
        
        // Remove the active dictionary from the prefs so we won't autolaunch
        // next time.
        final Editor editor = prefs.edit();
        editor.remove(C.DICT_FILE);
        editor.remove(C.INDEX_SHORT_NAME);
        editor.remove(C.SEARCH_TOKEN);
        editor.commit();

        application.backgroundUpdateDictionaries(dictionaryUpdater);

        setListAdapater();
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
                setListAdapater();
                return true;
            }
        });
        filterSearchView.setIconifiedByDefault(false);

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

    private void onShowLocalChanged() {
//        downloadableDictionaries.setVisibility(showDownloadable.isChecked() ? View.GONE
//                : View.VISIBLE);
        setListAdapater();
        Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
        prefs.putBoolean(C.SHOW_DOWNLOADABLE, showDownloadable.isChecked());
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
    
    class MyListAdapter extends BaseAdapter {
        
        List<DictionaryInfo> dictionariesOnDevice;
        List<DictionaryInfo> downloadableDictionaries;
        
        private MyListAdapter(final String[] filters) {
            dictionariesOnDevice = application.getDictionariesOnDevice(filters);
            if (showDownloadable.isChecked()) {
                downloadableDictionaries = application.getDownloadableDictionaries(filters);
            } else {
                downloadableDictionaries = Collections.emptyList();
            }
        }

        @Override
        public int getCount() {
            return 2 + dictionariesOnDevice.size() + downloadableDictionaries.size();
        }

        @Override
        public Object getItem(int position) {
            return Integer.valueOf(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView instanceof LinearLayout && 
                    convertView != dictionariesOnDeviceHeaderRow && 
                    convertView != downloadableDictionariesHeaderRow) {
                ((LinearLayout)convertView).removeAllViews();
            }
            // Dictionaries on device.
            if (position == 0) {
                return dictionariesOnDeviceHeaderRow;
            }
            --position;
            
            if (position < dictionariesOnDevice.size()) {
                return createDictionaryRow(dictionariesOnDevice.get(position), 
                                parent, R.layout.dictionaries_on_device_row, true);
            }
            position -= dictionariesOnDevice.size();
            
            // Downloadable dictionaries.
            if (position == 0) {
                return downloadableDictionariesHeaderRow;
            }
            --position;
            
            assert position < downloadableDictionaries.size();
            return createDictionaryRow(downloadableDictionaries.get(position), 
                            parent, R.layout.downloadable_dictionary_row, false);
        }
        
    }
    

    private void setListAdapater() {
        final String filter = filterSearchView == null ? "" : filterSearchView.getQuery().toString();
        final String[] filters = filter.trim().toLowerCase().split("(\\s|-)+");
        setListAdapter(new MyListAdapter(filters));
    }

    private View createDictionaryRow(final DictionaryInfo dictionaryInfo, final ViewGroup parent, 
            final int viewResource, final boolean canLaunch) {
        
        View row = LayoutInflater.from(parent.getContext()).inflate(
                viewResource, parent, false);
        final TextView name = (TextView) row.findViewById(R.id.dictionaryName);
        final TextView details = (TextView) row.findViewById(R.id.dictionaryDetails);
        name.setText(application.getDictionaryName(dictionaryInfo.uncompressedFilename));

        if (!canLaunch) {
            final Button downloadButton = (Button) row.findViewById(R.id.downloadButton);
            downloadButton.setText(getString(R.string.downloadButton, dictionaryInfo.zipBytes / 1024.0 / 1024.0));
            downloadButton.setMinWidth(application.languageButtonPixels * 3 / 2);
            downloadButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    Request request = new Request(
                            Uri.parse(dictionaryInfo.downloadUrl));
                    try {
                        final String destFile = new File(new URL(dictionaryInfo.downloadUrl).getFile()).getName(); 
                        Log.d(LOG, "Downloading to: " + destFile);
                        
                        request.setDestinationUri(Uri.fromFile(new File(Environment.getExternalStorageDirectory(), destFile)));
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                    downloadManager.enqueue(request);
                }
            });
        }

        final StringBuilder builder = new StringBuilder();
        LinearLayout buttons = (LinearLayout) row.findViewById(R.id.dictionaryLauncherButtons);
        final List<IndexInfo> sortedIndexInfos = application.sortedIndexInfos(dictionaryInfo.indexInfos);
        for (IndexInfo indexInfo : sortedIndexInfos) {
            final View button = application.createButton(buttons.getContext(), dictionaryInfo, indexInfo);
            buttons.addView(button);
            
            if (canLaunch) {
                button.setOnClickListener(
                        new IntentLauncher(buttons.getContext(), 
                        DictionaryActivity.getLaunchIntent(
                                application.getPath(dictionaryInfo.uncompressedFilename), 
                                indexInfo.shortName, "")));

            } else {
                button.setEnabled(false);
            }
            if (builder.length() != 0) {
                builder.append("; ");
            }
            builder.append(getString(R.string.indexInfo, indexInfo.shortName, indexInfo.mainTokenCount));
        }
        details.setText(builder.toString());
        
        return row;
    }

}
