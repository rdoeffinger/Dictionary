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

import android.app.AlertDialog;
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
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.view.Menu;
import android.widget.ListAdapter;
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

// Right-click:
//  Delete, move to top.

public class DictionaryManagerActivity extends ActionBarActivity {

    static final String LOG = "QuickDic";
    static boolean blockAutoLaunch = false;

    private ListView listView;
    private ListView getListView() {
        if (listView == null) {
            listView = (ListView)findViewById(android.R.id.list);
        }
        return listView;
    }
    private void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }
    private ListAdapter getListAdapter() {
        return getListView().getAdapter();
    }

    DictionaryApplication application;

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
                    setMyListAdapater();
                }
            });
        }
    };

    final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
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

                final String dest = cursor
                        .getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                final int status = cursor
                        .getInt(cursor
                                .getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (DownloadManager.STATUS_SUCCESSFUL != status) {
                    Log.w(LOG,
                            "Download failed: status=" + status +
                                    ", reason=" + cursor.getString(cursor
                                            .getColumnIndex(DownloadManager.COLUMN_REASON)));
                    Toast.makeText(context, getString(R.string.downloadFailed, dest),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Log.w(LOG, "Download finished: " + dest);
                Toast.makeText(context, getString(R.string.unzippingDictionary, dest),
                        Toast.LENGTH_LONG).show();
                
                
                final File localZipFile = new File(Uri.parse(dest).getPath());
                try {
                    ZipFile zipFile = new ZipFile(localZipFile);
                    final ZipEntry zipEntry = zipFile.entries().nextElement();
                    Log.d(LOG, "Unzipping entry: " + zipEntry.getName());
                    final InputStream zipIn = zipFile.getInputStream(zipEntry);
                    File targetFile = new File(application.getDictDir(), zipEntry.getName());
                    if (targetFile.exists()) {
                        targetFile.renameTo(new File(targetFile.getAbsolutePath().replace(".quickdic", ".bak.quickdic")));
                        targetFile = new File(application.getDictDir(), zipEntry.getName());
                    }
                    final OutputStream zipOut = new FileOutputStream(targetFile);
                    copyStream(zipIn, zipOut);
                    zipFile.close();
                    application.backgroundUpdateDictionaries(dictionaryUpdater);
                    Toast.makeText(context, getString(R.string.installationFinished, dest),
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(context, getString(R.string.unzippingFailed, dest),
                            Toast.LENGTH_LONG).show();
                    Log.e(LOG, "Failed to unzip.", e);
                } finally {
                    localZipFile.delete();
                }
            }
        }
    };

    public static Intent getLaunchIntent() {
        final Intent intent = new Intent();
        intent.setClassName(DictionaryManagerActivity.class.getPackage().getName(),
                DictionaryManagerActivity.class.getName());
        intent.putExtra(C.CAN_AUTO_LAUNCH_DICT, false);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // This must be first, otherwise the actiona bar doesn't get
        // styled properly.
        setTheme(((DictionaryApplication) getApplication()).getSelectedTheme().themeId);

        super.onCreate(savedInstanceState);
        Log.d(LOG, "onCreate:" + this);

        application = (DictionaryApplication) getApplication();

        blockAutoLaunch = false;

        // UI init.
        setContentView(R.layout.dictionary_manager_activity);

        dictionariesOnDeviceHeaderRow = (LinearLayout) LayoutInflater.from(
                getListView().getContext()).inflate(
                R.layout.dictionary_manager_header_row_on_device, getListView(), false);

        downloadableDictionariesHeaderRow = (LinearLayout) LayoutInflater.from(
                getListView().getContext()).inflate(
                R.layout.dictionary_manager_header_row_downloadable, getListView(), false);

        showDownloadable = (ToggleButton) downloadableDictionariesHeaderRow
                .findViewById(R.id.hideDownloadable);
        showDownloadable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onShowDownloadableChanged();
            }
        });

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String thanksForUpdatingLatestVersion = getString(R.string.thanksForUpdatingVersion);
        if (!prefs.getString(C.THANKS_FOR_UPDATING_VERSION, "").equals(
                thanksForUpdatingLatestVersion)) {
            blockAutoLaunch = true;
            startActivity(HtmlDisplayActivity.getWhatsNewLaunchIntent());
            prefs.edit().putString(C.THANKS_FOR_UPDATING_VERSION, thanksForUpdatingLatestVersion)
                    .commit();
        }

        registerReceiver(broadcastReceiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        setMyListAdapater();
        registerForContextMenu(getListView());

        final File dictDir = application.getDictDir();
        if (!dictDir.canRead() || !dictDir.canExecute()) {
            blockAutoLaunch = true;

            AlertDialog.Builder builder = new AlertDialog.Builder(getListView().getContext());
            builder.setTitle(getString(R.string.error));
            builder.setMessage(getString(
                    R.string.unableToReadDictionaryDir,
                    dictDir.getAbsolutePath(),
                    Environment.getExternalStorageDirectory()));
            builder.create().show();
        }

        onCreateSetupActionBar();
    }

    private void onCreateSetupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);

        filterSearchView = new SearchView(getSupportActionBar().getThemedContext());
        filterSearchView.setIconifiedByDefault(false);
        // filterSearchView.setIconified(false); // puts the magnifying glass in
        // the
        // wrong place.
        filterSearchView.setQueryHint(getString(R.string.searchText));
        filterSearchView.setSubmitButtonEnabled(false);
        final int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300,
                getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        filterSearchView.setLayoutParams(lp);
        filterSearchView.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH |
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                        EditorInfo.IME_FLAG_NO_ENTER_ACTION |
                        // EditorInfo.IME_FLAG_NO_FULLSCREEN | // Requires API
                        // 11
                        EditorInfo.IME_MASK_ACTION |
                        EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        filterSearchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String filterText) {
                setMyListAdapater();
                return true;
            }
        });
        filterSearchView.setFocusable(true);

        actionBar.setCustomView(filterSearchView);
        actionBar.setDisplayShowCustomEnabled(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
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
        showDownloadable.setChecked(prefs.getBoolean(C.SHOW_DOWNLOADABLE, true));

        if (!blockAutoLaunch &&
                getIntent().getBooleanExtra(C.CAN_AUTO_LAUNCH_DICT, true) &&
                prefs.contains(C.DICT_FILE) &&
                prefs.contains(C.INDEX_SHORT_NAME)) {
            Log.d(LOG, "Skipping DictionaryManager, going straight to dictionary.");
            startActivity(DictionaryActivity.getLaunchIntent(
                    new File(prefs.getString(C.DICT_FILE, "")),
                    prefs.getString(C.INDEX_SHORT_NAME, ""),
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

        setMyListAdapater();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        application.onCreateGlobalOptionsMenu(this, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View view,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        Log.d(LOG, "onCreateContextMenu, " + menuInfo);

        final AdapterContextMenuInfo adapterContextMenuInfo =
                (AdapterContextMenuInfo) menuInfo;
        final int position = adapterContextMenuInfo.position;
        final MyListAdapter.Row row = (MyListAdapter.Row) getListAdapter().getItem(position);

        if (row.dictionaryInfo == null) {
            return;
        }

        if (position > 0 && row.onDevice) {
            final android.view.MenuItem moveToTopMenuItem =
                    menu.add(R.string.moveToTop);
            moveToTopMenuItem.setOnMenuItemClickListener(new
                    android.view.MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(android.view.MenuItem item) {
                            application.moveDictionaryToTop(row.dictionaryInfo);
                            setMyListAdapater();
                            return true;
                        }
                    });
        }

        if (row.onDevice) {
            final android.view.MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
            deleteMenuItem
                    .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(android.view.MenuItem item) {
                            application.deleteDictionary(row.dictionaryInfo);
                            setMyListAdapater();
                            return true;
                        }
                    });
        }
    }

    private void onShowDownloadableChanged() {
        setMyListAdapater();
        Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
        prefs.putBoolean(C.SHOW_DOWNLOADABLE, showDownloadable.isChecked());
        prefs.commit();
    }

    class MyListAdapter extends BaseAdapter {

        List<DictionaryInfo> dictionariesOnDevice;
        List<DictionaryInfo> downloadableDictionaries;

        class Row {
            DictionaryInfo dictionaryInfo;
            boolean onDevice;

            private Row(DictionaryInfo dictinoaryInfo, boolean onDevice) {
                this.dictionaryInfo = dictinoaryInfo;
                this.onDevice = onDevice;
            }
        }

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
        public Row getItem(int position) {
            if (position == 0) {
                return new Row(null, true);
            }
            position -= 1;

            if (position < dictionariesOnDevice.size()) {
                return new Row(dictionariesOnDevice.get(position), true);
            }
            position -= dictionariesOnDevice.size();

            if (position == 0) {
                return new Row(null, false);
            }
            position -= 1;

            assert position < downloadableDictionaries.size();
            return new Row(downloadableDictionaries.get(position), false);
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
                /*
                 * This is done to try to avoid leaking memory that used to
                 * happen on Android 4.0.3
                 */
                ((LinearLayout) convertView).removeAllViews();
            }

            final Row row = getItem(position);

            if (row.onDevice) {
                if (row.dictionaryInfo == null) {
                    return dictionariesOnDeviceHeaderRow;
                }
                return createDictionaryRow(row.dictionaryInfo, parent, true);
            }

            if (row.dictionaryInfo == null) {
                return downloadableDictionariesHeaderRow;
            }
            return createDictionaryRow(row.dictionaryInfo, parent, false);
        }

    }

    private void setMyListAdapater() {
        final String filter = filterSearchView == null ? "" : filterSearchView.getQuery()
                .toString();
        final String[] filters = filter.trim().toLowerCase().split("(\\s|-)+");
        setListAdapter(new MyListAdapter(filters));
    }

    private View createDictionaryRow(final DictionaryInfo dictionaryInfo,
            final ViewGroup parent, final boolean canLaunch) {

        View row = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.dictionary_manager_row, parent, false);
        final TextView name = (TextView) row.findViewById(R.id.dictionaryName);
        final TextView details = (TextView) row.findViewById(R.id.dictionaryDetails);
        name.setText(application.getDictionaryName(dictionaryInfo.uncompressedFilename));

        final boolean updateAvailable = application.updateAvailable(dictionaryInfo);
        final Button downloadButton = (Button) row.findViewById(R.id.downloadButton);
        if (!canLaunch || updateAvailable) {
            final DictionaryInfo downloadable = application.getDownloadable(dictionaryInfo.uncompressedFilename);
            downloadButton
                    .setText(getString(
                            R.string.downloadButton,
                            downloadable.zipBytes / 1024.0 / 1024.0));
            downloadButton.setMinWidth(application.languageButtonPixels * 3 / 2);
            downloadButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    downloadDictionary(downloadable.downloadUrl);
                }
            });
        } else {
            downloadButton.setVisibility(View.INVISIBLE);
        }

        LinearLayout buttons = (LinearLayout) row.findViewById(R.id.dictionaryLauncherButtons);
        final List<IndexInfo> sortedIndexInfos = application
                .sortedIndexInfos(dictionaryInfo.indexInfos);
        final StringBuilder builder = new StringBuilder();
        if (updateAvailable) {
            builder.append(getString(R.string.updateAvailable));
        }
        for (IndexInfo indexInfo : sortedIndexInfos) {
            final View button = application.createButton(buttons.getContext(), dictionaryInfo,
                    indexInfo);
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
            builder.append(getString(R.string.indexInfo, indexInfo.shortName,
                    indexInfo.mainTokenCount));
        }
        details.setText(builder.toString());

        if (canLaunch) {
            row.setClickable(true);
            row.setOnClickListener(new IntentLauncher(parent.getContext(),
                    DictionaryActivity.getLaunchIntent(
                            application.getPath(dictionaryInfo.uncompressedFilename),
                            dictionaryInfo.indexInfos.get(0).shortName, "")));
            row.setFocusable(true);
            row.setLongClickable(true);
        }
        row.setBackgroundResource(android.R.drawable.menuitem_background);

        return row;
    }

    private void downloadDictionary(final String downloadUrl) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Request request = new Request(
                Uri.parse(downloadUrl));
        try {
            final String destFile = new File(new URL(downloadUrl).getFile())
                    .getName();
            Log.d(LOG, "Downloading to: " + destFile);

            request.setDestinationUri(Uri.fromFile(new File(Environment
                    .getExternalStorageDirectory(), destFile)));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        downloadManager.enqueue(request);
    }

}
