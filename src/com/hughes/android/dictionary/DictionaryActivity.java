// Copyright 2011 Google Inc. All Rights Reserved.
// Some Parts Copyright 2013 Dominik Köppl
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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.EntrySource;
import com.hughes.android.dictionary.engine.HtmlEntry;
import com.hughes.android.dictionary.engine.Index;
import com.hughes.android.dictionary.engine.Index.IndexEntry;
import com.hughes.android.dictionary.engine.PairEntry;
import com.hughes.android.dictionary.engine.PairEntry.Pair;
import com.hughes.android.dictionary.engine.RowBase;
import com.hughes.android.dictionary.engine.TokenRow;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.IntentLauncher;
import com.hughes.android.util.NonLinkClickableSpan;
import com.hughes.util.StringUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DictionaryActivity extends AppCompatActivity {

    private static final String LOG = "QuickDic";

    private DictionaryApplication application;

    private DocumentFile dictFile = null;
    private FileChannel dictRaf = null;
    private String dictFileTitleName = null;

    private Dictionary dictionary = null;

    private int indexIndex = 0;

    private Index index = null;

    private List<RowBase> rowsToShow = null; // if not null, just show these rows.

    private final Random rand = new Random();

    private final Handler uiHandler = new Handler();

    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "searchExecutor");
        }
    });

    private SearchOperation currentSearchOperation = null;
    private final int MAX_SEARCH_HISTORY = 100;
    private final int DEFAULT_SEARCH_HISTORY = 10;
    private int searchHistoryLimit;
    private final ArrayList<String> searchHistory = new ArrayList<>(DEFAULT_SEARCH_HISTORY);
    private MatrixCursor searchHistoryCursor = new MatrixCursor(new String[] {"_id", "search"});

    private TextToSpeech textToSpeech;
    private volatile boolean ttsReady;

    private Typeface typeface;
    private DictionaryApplication.Theme theme = DictionaryApplication.Theme.LIGHT;
    private int textColorFg = Color.BLACK;
    private int fontSizeSp;

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

    private SearchView searchView;
    private AutoCompleteTextView searchTextView;
    private ImageButton languageButton;
    private SearchView.OnQueryTextListener onQueryTextListener;

    private MenuItem nextWordMenuItem;
    private MenuItem previousWordMenuItem;

    // Never null.
    private DocumentFile wordList = null;
    private boolean saveOnlyFirstSubentry = false;
    private boolean clickOpensContextMenu = false;

    // Visible for testing.
    private ListAdapter indexAdapter = null;

    /**
     * For some languages, loading the transliterators used in this search takes
     * a long time, so we fire it up on a different thread, and don't invoke it
     * from the main thread until it's already finished once.
     */
    private volatile boolean indexPrepFinished = false;

    public DictionaryActivity() {
    }

    public static Intent getLaunchIntent(Context c, final String dictFile, final String indexShortName,
                                         final String searchToken) {
        final Intent intent = new Intent(c, DictionaryActivity.class);
        intent.putExtra(C.DICT_FILE, dictFile);
        intent.putExtra(C.INDEX_SHORT_NAME, indexShortName);
        intent.putExtra(C.SEARCH_TOKEN, searchToken);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOG, "onSaveInstanceState: " + searchView.getQuery().toString());
        outState.putString(C.INDEX_SHORT_NAME, index.shortName);
        outState.putString(C.SEARCH_TOKEN, searchView.getQuery().toString());
        outState.putStringArrayList(C.SEARCH_HISTORY, searchHistory);
    }

    private int getMatchLen(String search, Index.IndexEntry e) {
        if (e == null) return 0;
        for (int i = 0; i < search.length(); ++i) {
            String a = search.substring(0, i + 1);
            String b = e.token.substring(0, i + 1);
            if (!a.equalsIgnoreCase(b))
                return i;
        }
        return search.length();
    }

    private void dictionaryOpenFail(Exception e) {
        Log.e(LOG, "Unable to load dictionary.", e);
        if (dictRaf != null) {
            indexAdapter = null;
            setListAdapter(null);
            try {
                dictRaf.close();
            } catch (IOException e1) {
                Log.e(LOG, "Unable to close dictRaf.", e1);
            }
            dictRaf = null;
        }
        if (!isFinishing())
            Toast.makeText(this, getString(R.string.invalidDictionary, "", e.getMessage()),
                           Toast.LENGTH_LONG).show();
        startActivity(DictionaryManagerActivity.getLaunchIntent(getApplicationContext()));
        finish();
    }

    private void saveSearchHistory() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < searchHistory.size(); i++) {
            ed.putString("history" + i, searchHistory.get(i));
        }
        for (int i = searchHistory.size(); i <= MAX_SEARCH_HISTORY; i++) {
            ed.remove("history" + i);
        }
        ed.apply();
    }

    private void addToSearchHistory() {
        addToSearchHistory(searchView.getQuery().toString());
    }

    private void addToSearchHistory(String text) {
        if (text == null || text.isEmpty() || searchHistoryLimit == 0) return;
        int exists = searchHistory.indexOf(text);
        if (exists >= 0) searchHistory.remove(exists);
        else if (searchHistory.size() >= searchHistoryLimit) searchHistory.remove(searchHistory.size() - 1);
        searchHistory.add(0, text);
        searchHistoryCursor = new MatrixCursor(new String[] {"_id", "search"});
        for (int i = 0; i < searchHistory.size(); i++) {
            final Object[] row = {i, searchHistory.get(i)};
            searchHistoryCursor.addRow(row);
        }
        if (searchView.getSuggestionsAdapter().getCursor() != null) {
            searchView.getSuggestionsAdapter().swapCursor(searchHistoryCursor);
            searchView.getSuggestionsAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // when called via special search intents avoid focusing the search field
        // and thus popping up the keyboard
        boolean focusSearchView = true;
        DictionaryApplication.INSTANCE.init(getApplicationContext());
        application = DictionaryApplication.INSTANCE;
        // This needs to be before super.onCreate, otherwise ActionbarSherlock
        // doesn't makes the background of the actionbar white when you're
        // in the dark theme.
        setTheme(application.getSelectedTheme().themeId);

        Log.d(LOG, "onCreate:" + this);
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Don't auto-launch if this fails.
        prefs.edit().remove(C.DICT_FILE).remove(C.INDEX_SHORT_NAME).commit();

        setContentView(R.layout.dictionary_activity);

        theme = application.getSelectedTheme();
        textColorFg = getResources().getColor(theme.tokenRowFgColor);

        if (dictRaf != null) {
            try {
                dictRaf.close();
            } catch (IOException e) {
                Log.e(LOG, "Failed to close dictionary", e);
            }
            dictRaf = null;
        }

        final Intent intent = getIntent();
        String intentAction = intent.getAction();
        /*
          @author Dominik Köppl Querying the Intent
         *         com.hughes.action.ACTION_SEARCH_DICT is the advanced query
         *         Arguments: SearchManager.QUERY -> the phrase to search from
         *         -> language in which the phrase is written to -> to which
         *         language shall be translated
         */
        if ("com.hughes.action.ACTION_SEARCH_DICT".equals(intentAction)) {
            focusSearchView = false;
            String query = intent.getStringExtra(SearchManager.QUERY);
            String from = intent.getStringExtra("from");
            if (from != null)
                from = from.toLowerCase(Locale.US);
            String to = intent.getStringExtra("to");
            if (to != null)
                to = to.toLowerCase(Locale.US);
            if (query != null) {
                getIntent().putExtra(C.SEARCH_TOKEN, query);
            }
            if (intent.getStringExtra(C.DICT_FILE) == null && (from != null || to != null)) {
                Log.d(LOG, "DictSearch: from: " + from + " to " + to);
                List<DictionaryInfo> dicts = application.getDictionariesOnDevice(null);
                for (DictionaryInfo info : dicts) {
                    boolean hasFrom = from == null;
                    boolean hasTo = to == null;
                    for (IndexInfo index : info.indexInfos) {
                        if (!hasFrom && index.shortName.toLowerCase(Locale.US).equals(from))
                            hasFrom = true;
                        if (!hasTo && index.shortName.toLowerCase(Locale.US).equals(to))
                            hasTo = true;
                    }
                    if (hasFrom && hasTo) {
                        if (from != null) {
                            int which_index = 0;
                            for (; which_index < info.indexInfos.size(); ++which_index) {
                                if (info.indexInfos.get(which_index).shortName.toLowerCase(
                                            Locale.US).equals(from))
                                    break;
                            }
                            intent.putExtra(C.INDEX_SHORT_NAME,
                                            info.indexInfos.get(which_index).shortName);

                        }
                        intent.putExtra(C.DICT_FILE, application.getPath(info.uncompressedFilename)
                                        .toString());
                        break;
                    }
                }

            }
        }
        /*
          @author Dominik Köppl Querying the Intent Intent.ACTION_SEARCH is a
         *         simple query Arguments follow from android standard (see
         *         documentation)
         */
        if (intentAction != null && intentAction.equals(Intent.ACTION_SEARCH)) {
            focusSearchView = false;
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null)
                getIntent().putExtra(C.SEARCH_TOKEN, query);
        }
        if (intentAction != null && intentAction.equals(Intent.ACTION_SEND)) {
            focusSearchView = false;
            String query = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (query != null)
                getIntent().putExtra(C.SEARCH_TOKEN, query);
        }
        /*
         * This processes text on M+ devices where QuickDic shows up in the context menu.
         */
        if (intentAction != null && intentAction.equals(Intent.ACTION_PROCESS_TEXT)) {
            focusSearchView = false;
            String query = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
            if (query != null) {
                getIntent().putExtra(C.SEARCH_TOKEN, query);
            }
        }
        // Support opening dictionary file directly
        if (intentAction != null && intentAction.equals(Intent.ACTION_VIEW)) {
            Uri uri = intent.getData();
            intent.putExtra(C.DICT_FILE, uri.toString());
            dictFileTitleName = uri.getLastPathSegment();
            try {
                dictRaf = getContentResolver().openAssetFileDescriptor(uri, "r").createInputStream().getChannel();
            } catch (Exception e) {
                dictionaryOpenFail(e);
                return;
            }
        }
        /*
          @author Dominik Köppl If no dictionary is chosen, use the default
         *         dictionary specified in the preferences If this step does
         *         fail (no default dictionary specified), show a toast and
         *         abort.
         */
        if (intent.getStringExtra(C.DICT_FILE) == null) {
            String dictfile = prefs.getString(getString(R.string.defaultDicKey), null);
            if (dictfile != null)
                intent.putExtra(C.DICT_FILE, application.getPath(dictfile).toString());
        }
        String dictFilename = intent.getStringExtra(C.DICT_FILE);
        if (dictFilename == null && intent.getStringExtra(C.SEARCH_TOKEN) != null) {
            final List<DictionaryInfo> dics = application.getDictionariesOnDevice(null);
            final String search = intent.getStringExtra(C.SEARCH_TOKEN);
            String bestFname = null;
            String bestIndex = null;
            int bestMatchLen = 2; // ignore shorter matches
            AtomicBoolean dummy = new AtomicBoolean();
            for (int i = 0; dictFilename == null && i < dics.size(); ++i) {
                try {
                    Log.d(LOG, "Checking dictionary " + dics.get(i).uncompressedFilename);
                    final DocumentFile dictfile = application.getPath(dics.get(i).uncompressedFilename);
                    FileChannel c = getContentResolver().openAssetFileDescriptor(dictfile.getUri(), "r").createInputStream().getChannel();
                    Dictionary dic = new Dictionary(c);
                    for (int j = 0; j < dic.indices.size(); ++j) {
                        Index idx = dic.indices.get(j);
                        Log.d(LOG, "Checking index " + idx.shortName);
                        if (idx.findExact(search) != null) {
                            Log.d(LOG, "Found exact match");
                            dictFilename = dictfile.toString();
                            intent.putExtra(C.INDEX_SHORT_NAME, idx.shortName);
                            break;
                        }
                        int matchLen = getMatchLen(search, idx.findInsertionPoint(search, dummy));
                        Log.d(LOG, "Found partial match length " + matchLen);
                        if (matchLen > bestMatchLen) {
                            bestFname = dictfile.toString();
                            bestIndex = idx.shortName;
                            bestMatchLen = matchLen;
                        }
                    }
                } catch (Exception e) {}
            }
            if (dictFilename == null && bestFname != null) {
                dictFilename = bestFname;
                intent.putExtra(C.INDEX_SHORT_NAME, bestIndex);
            }
        }

        if (dictFilename == null) {
            if (!isFinishing())
                Toast.makeText(this, getString(R.string.no_dict_file), Toast.LENGTH_LONG).show();
            startActivity(DictionaryManagerActivity.getLaunchIntent(getApplicationContext()));
            finish();
            return;
        }
        if (dictRaf == null) {
            Uri u = Uri.parse(dictFilename);
            dictFile = "content".equals(u.getScheme()) ? DocumentFile.fromSingleUri(getApplicationContext(), u) : DocumentFile.fromFile(new File(u.getPath()));
        }

        ttsReady = false;
        textToSpeech = new TextToSpeech(getApplicationContext(), new OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = true;
                updateTTSLanguage(indexIndex);
            }
        });

        try {
            if (dictRaf == null) {
                dictFileTitleName = application.getDictionaryName(dictFile.getName());
                dictRaf = getContentResolver().openAssetFileDescriptor(dictFile.getUri(), "r").createInputStream().getChannel();
            }
            this.setTitle("QuickDic: " + dictFileTitleName);
            dictionary = new Dictionary(dictRaf);
        } catch (Exception e) {
            dictionaryOpenFail(e);
            return;
        }
        String targetIndex = intent.getStringExtra(C.INDEX_SHORT_NAME);
        if (savedInstanceState != null && savedInstanceState.getString(C.INDEX_SHORT_NAME) != null) {
            targetIndex = savedInstanceState.getString(C.INDEX_SHORT_NAME);
        }
        indexIndex = 0;
        for (int i = 0; i < dictionary.indices.size(); ++i) {
            if (dictionary.indices.get(i).shortName.equals(targetIndex)) {
                indexIndex = i;
                break;
            }
        }
        Log.d(LOG, "Loading index " + indexIndex);
        index = dictionary.indices.get(indexIndex);
        getListView().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        getListView().setEmptyView(findViewById(android.R.id.empty));
        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int row, long id) {
                onListItemClick(getListView(), view, row, id);
            }
        });

        setListAdapter(new IndexAdapter(index));

        // Pre-load the Transliterator (will spawn its own thread)
        TransliteratorManager.init(new TransliteratorManager.Callback() {
            @Override
            public void onTransliteratorReady() {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onSearchTextChange(searchView.getQuery().toString());
                    }
                });
            }
        }, DictionaryApplication.threadBackground);

        // Pre-load the collators.
        new Thread(new Runnable() {
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE);
                final long startMillis = System.currentTimeMillis();
                try {
                    for (final Index index : dictionary.indices) {
                        final String searchToken = index.sortedIndexEntries.get(0).token;
                        final IndexEntry entry = index.findExact(searchToken);
                        if (entry == null || !searchToken.equals(entry.token)) {
                            Log.e(LOG, "Couldn't find token: " + searchToken + ", " + (entry == null ? "null" : entry.token));
                        }
                    }
                    indexPrepFinished = true;
                } catch (Exception e) {
                    Log.w(LOG,
                          "Exception while prepping.  This can happen if dictionary is closed while search is happening.");
                }
                Log.d(LOG, "Prepping indices took:" + (System.currentTimeMillis() - startMillis));
            }
        }).start();

        String fontName = prefs.getString(getString(R.string.fontKey), "FreeSerif.otf.jpg");
        switch (fontName) {
            case "SYSTEM":
                typeface = Typeface.DEFAULT;
                break;
            case "SERIF":
                typeface = Typeface.SERIF;
                break;
            case "SANS_SERIF":
                typeface = Typeface.SANS_SERIF;
                break;
            case "MONOSPACE":
                typeface = Typeface.MONOSPACE;
                break;
            default:
                if ("FreeSerif.ttf.jpg".equals(fontName)) {
                    fontName = "FreeSerif.otf.jpg";
                }
                try {
                    typeface = Typeface.createFromAsset(getAssets(), fontName);
                } catch (Exception e) {
                    Log.w(LOG, "Exception trying to use typeface, using default.", e);
                    if (!isFinishing())
                        Toast.makeText(this, getString(R.string.fontFailure, e.getLocalizedMessage()),
                                Toast.LENGTH_LONG).show();
                }
                break;
        }
        if (typeface == null) {
            Log.w(LOG, "Unable to create typeface, using default.");
            typeface = Typeface.DEFAULT;
        }
        final String fontSize = prefs.getString(getString(R.string.fontSizeKey), "14");
        try {
            fontSizeSp = Integer.parseInt(fontSize.trim());
        } catch (NumberFormatException e) {
            fontSizeSp = 14;
        }

        final String searchHistoryLimitStr = prefs.getString(getString(R.string.historySizeKey), "" + DEFAULT_SEARCH_HISTORY);
        try {
            searchHistoryLimit = Math.min(Integer.parseInt(searchHistoryLimitStr.trim()), MAX_SEARCH_HISTORY);
        } catch (NumberFormatException e) {
            searchHistoryLimit = DEFAULT_SEARCH_HISTORY;
        }

        // ContextMenu.
        registerForContextMenu(getListView());

        // Cache some prefs.
        wordList = application.getWordListFile();
        saveOnlyFirstSubentry = prefs.getBoolean(getString(R.string.saveOnlyFirstSubentryKey),
                                false);
        clickOpensContextMenu = prefs.getBoolean(getString(R.string.clickOpensContextMenuKey),
                                !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));
        Log.d(LOG, "wordList=" + wordList + ", saveOnlyFirstSubentry=" + saveOnlyFirstSubentry);

        onCreateSetupActionBarAndSearchView();

        View floatSwapButton = findViewById(R.id.floatSwapButton);
        floatSwapButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onLanguageButtonLongClick(v.getContext());
                return true;
            }
        });

        // Set the search text from the intent, then the saved state.
        String text = getIntent().getStringExtra(C.SEARCH_TOKEN);
        if (savedInstanceState != null) {
            text = savedInstanceState.getString(C.SEARCH_TOKEN);
        }
        if (text == null) {
            text = "";
        }

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                String h = searchHistory.get(position);
                addToSearchHistory(h);
                setSearchText(h, true);
                return true;
            }
        });
        searchView.setSuggestionsAdapter(new CursorAdapter(this, text.isEmpty() ? searchHistoryCursor : null, 0) {
            @Override
            public View newView(Context context, Cursor c, ViewGroup p) {
                TextView v = new TextView(context);
                v.setTextColor(textColorFg);
                v.setTypeface(typeface);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 4 * fontSizeSp / 3);
                return v;
            }
            @Override
            public void bindView(View v, Context context, Cursor c) {
                TextView t = (TextView)v;
                t.setText(c.getString(1));
            }
        });

        // Set up search history
        ArrayList<String> savedHistory = null;
        if (savedInstanceState != null) savedHistory = savedInstanceState.getStringArrayList(C.SEARCH_HISTORY);
        if (savedHistory != null && !savedHistory.isEmpty()) {
        } else {
            savedHistory = new ArrayList<>();
            for (int i = 0; i < searchHistoryLimit; i++) {
                String h = prefs.getString("history" + i, null);
                if (h == null) break;
                savedHistory.add(h);
            }
        }
        for (int i = savedHistory.size() - 1; i >= 0; i--) {
            addToSearchHistory(savedHistory.get(i));
        }
        addToSearchHistory(text);

        setSearchText(text, true);
        Log.d(LOG, "Trying to restore searchText=" + text);

        setDictionaryPrefs(this, dictFile, index.shortName);

        updateLangButton();
        if (focusSearchView) searchView.requestFocus();

        // http://stackoverflow.com/questions/2833057/background-listview-becomes-black-when-scrolling
//        getListView().setCacheColorHint(0);
    }

    private void onCreateSetupActionBarAndSearchView() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);

        final LinearLayout customSearchView = new LinearLayout(getSupportActionBar().getThemedContext());

        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        customSearchView.setLayoutParams(layoutParams);

        languageButton = new ImageButton(customSearchView.getContext());
        languageButton.setId(R.id.languageButton);
        languageButton.setScaleType(ScaleType.FIT_CENTER);
        languageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onLanguageButtonLongClick(v.getContext());
            }
        });
        languageButton.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(application.languageButtonPixels, LinearLayout.LayoutParams.MATCH_PARENT);
        customSearchView.addView(languageButton, lpb);

        searchView = new SearchView(getSupportActionBar().getThemedContext());
        searchView.setId(R.id.searchView);

        // Get rid of search icon, it takes up too much space.
        // There is still text saying "search" in the search field.
        searchView.setIconifiedByDefault(true);
        searchView.setIconified(false);

        searchView.setQueryHint(getString(R.string.searchText));
        searchView.setSubmitButtonEnabled(false);
        searchView.setInputType(InputType.TYPE_CLASS_TEXT);
        searchView.setImeOptions(
            EditorInfo.IME_ACTION_DONE |
            EditorInfo.IME_FLAG_NO_EXTRACT_UI |
            // EditorInfo.IME_FLAG_NO_FULLSCREEN | // Requires API
            // 11
            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        onQueryTextListener = new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(LOG, "OnQueryTextListener: onQueryTextSubmit: " + searchView.getQuery());
                addToSearchHistory();
                hideKeyboard();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(LOG, "OnQueryTextListener: onQueryTextChange: " + searchView.getQuery());
                onSearchTextChange(searchView.getQuery().toString());
                return true;
            }
        };
        searchView.setOnQueryTextListener(onQueryTextListener);
        searchView.setFocusable(true);
        searchTextView = searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                FrameLayout.LayoutParams.WRAP_CONTENT, 1);
        customSearchView.addView(searchView, lp);

        actionBar.setCustomView(customSearchView);
        actionBar.setDisplayShowCustomEnabled(true);

        // Avoid wasting space on large left inset
        Toolbar tb = (Toolbar)customSearchView.getParent();
        tb.setContentInsetsRelative(0, 0);

        getListView().setNextFocusLeftId(R.id.searchView);
        findViewById(R.id.floatSwapButton).setNextFocusRightId(R.id.languageButton);
        languageButton.setNextFocusLeftId(R.id.floatSwapButton);
    }

    @Override
    protected void onResume() {
        Log.d(LOG, "onResume");
        super.onResume();
        if (PreferenceActivity.prefsMightHaveChanged) {
            PreferenceActivity.prefsMightHaveChanged = false;
            finish();
            startActivity(getIntent());
        }
        showKeyboard();
    }

    /**
     * Invoked when MyWebView returns, since the user might have clicked some
     * hypertext in the MyWebView.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (result != null && result.hasExtra(C.SEARCH_TOKEN)) {
            Log.d(LOG, "onActivityResult: " + result.getStringExtra(C.SEARCH_TOKEN));
            jumpToTextFromHyperLink(result.getStringExtra(C.SEARCH_TOKEN), indexIndex);
        }
    }

    private static void setDictionaryPrefs(final Context context, final DocumentFile dictFile,
                                           final String indexShortName) {
        final SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(
                context).edit();
        if (dictFile != null) {
            prefs.putString(C.DICT_FILE, dictFile.getUri().toString());
            prefs.putString(C.INDEX_SHORT_NAME, indexShortName);
        }
        prefs.remove(C.SEARCH_TOKEN); // Don't need to save search token.
        prefs.commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        addToSearchHistory();
        saveSearchHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dictRaf == null) {
            return;
        }

        final SearchOperation searchOperation = currentSearchOperation;
        currentSearchOperation = null;

        // Before we close the RAF, we have to wind the current search down.
        if (searchOperation != null) {
            Log.d(LOG, "Interrupting search to shut down.");
            currentSearchOperation = null;
            searchOperation.interrupted.set(true);
        }
        searchExecutor.shutdownNow();
        textToSpeech.shutdown();
        textToSpeech = null;

        indexAdapter = null;
        setListAdapter(null);

        try {
            Log.d(LOG, "Closing RAF.");
            dictRaf.close();
        } catch (IOException e) {
            Log.e(LOG, "Failed to close dictionary", e);
        }
        dictRaf = null;
    }

    // --------------------------------------------------------------------------
    // Buttons
    // --------------------------------------------------------------------------

    private void showKeyboard() {
        // For some reason, this doesn't always work the first time.
        // One way to replicate the problem:
        // Press the "task switch" button repeatedly to pause and resume
        for (int delay = 1; delay <= 101; delay += 100) {
            searchView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG, "Trying to show soft keyboard.");
                    final boolean searchTextHadFocus = searchView.hasFocus();
                    searchView.requestFocusFromTouch();
                    searchTextView.requestFocus();
                    final InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    manager.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT);
                    manager.showSoftInput(searchTextView, InputMethodManager.SHOW_IMPLICIT);
                    if (!searchTextHadFocus) {
                        defocusSearchText();
                    }
                }
            }, delay);
        }
        searchView.post(new Runnable() {
            @Override
            public void run() {
                searchTextView.setThreshold(0);
                try {
                    searchTextView.showDropDown();
                // ignore any errors, in particular BadTokenException happens a lot
                } catch (Exception e) {}
            }
        });
    }

    private void hideKeyboard() {
        Log.d(LOG, "Hide soft keyboard.");
        searchView.clearFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
    }

    private void updateLangButton() {
        final int flagId = IsoUtils.INSTANCE.getFlagIdForIsoCode(index.shortName);
        if (flagId != 0) {
            languageButton.setImageResource(flagId);
        } else {
            if (indexIndex % 2 == 0) {
                languageButton.setImageResource(android.R.drawable.ic_media_next);
            } else {
                languageButton.setImageResource(android.R.drawable.ic_media_previous);
            }
        }
        updateTTSLanguage(indexIndex);
    }

    @SuppressWarnings("deprecation")
    private void speak(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void updateTTSLanguage(int i) {
        if (!ttsReady || index == null || textToSpeech == null) {
            Log.d(LOG, "Can't updateTTSLanguage.");
            return;
        }
        final Locale locale = new Locale(dictionary.indices.get(i).sortLanguage.getIsoCode());
        Log.d(LOG, "Setting TTS locale to: " + locale);
        try {
            final int ttsResult = textToSpeech.setLanguage(locale);
            if (ttsResult != TextToSpeech.LANG_AVAILABLE &&
                    ttsResult != TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                Log.e(LOG, "TTS not available in this language: ttsResult=" + ttsResult);
            }
        } catch (Exception e) {
            if (!isFinishing())
                Toast.makeText(this, getString(R.string.TTSbroken), Toast.LENGTH_LONG).show();
        }
    }

    public void onSearchButtonClick(View dummy) {
        if (!searchView.hasFocus()) {
            searchView.requestFocus();
        }
        if (searchView.getQuery().toString().length() > 0) {
            addToSearchHistory();
            searchView.setQuery("", false);
        }
        showKeyboard();
        searchView.setIconified(false);
    }

    public void onLanguageButtonClick(View dummy) {
        if (dictionary.indices.size() == 1) {
            // No need to work to switch indices.
            return;
        }
        if (currentSearchOperation != null) {
            currentSearchOperation.interrupted.set(true);
            currentSearchOperation = null;
        }
        setIndexAndSearchText((indexIndex + 1) % dictionary.indices.size(),
                              searchView.getQuery().toString(), false);
    }

    private void onLanguageButtonLongClick(final Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.select_dictionary_dialog);
        dialog.setTitle(R.string.selectDictionary);

        final List<DictionaryInfo> installedDicts = application.getDictionariesOnDevice(null);

        ListView listView = dialog.findViewById(android.R.id.list);
        final Button button = new Button(listView.getContext());
        final String name = getString(R.string.dictionaryManager);
        button.setText(name);
        final IntentLauncher intentLauncher = new IntentLauncher(listView.getContext(),
        DictionaryManagerActivity.getLaunchIntent(getApplicationContext())) {
            @Override
            protected void onGo() {
                dialog.dismiss();
                DictionaryActivity.this.finish();
            }
        };
        button.setOnClickListener(intentLauncher);
        listView.addHeaderView(button);

        listView.setItemsCanFocus(true);
        listView.setAdapter(new BaseAdapter() {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final DictionaryInfo dictionaryInfo = getItem(position);

                final LinearLayout result = new LinearLayout(parent.getContext());

                for (int i = 0; i < dictionaryInfo.indexInfos.size(); ++i) {
                    final IndexInfo indexInfo = dictionaryInfo.indexInfos.get(i);
                    final View button = IsoUtils.INSTANCE.createButton(parent.getContext(),
                            indexInfo, application.languageButtonPixels);
                    final IntentLauncher intentLauncher = new IntentLauncher(parent.getContext(),
                            getLaunchIntent(getApplicationContext(),
                                            application.getPath(dictionaryInfo.uncompressedFilename).getUri().toString(),
                    indexInfo.shortName, searchView.getQuery().toString())) {
                        @Override
                        protected void onGo() {
                            dialog.dismiss();
                            DictionaryActivity.this.finish();
                        }
                    };
                    button.setOnClickListener(intentLauncher);
                    if (i == indexIndex && dictFile != null &&
                            dictFile.getName().equals(dictionaryInfo.uncompressedFilename)) {
                        button.setPressed(true);
                    }
                    result.addView(button);
                }

                final TextView nameView = new TextView(parent.getContext());
                final String name = application
                                    .getDictionaryName(dictionaryInfo.uncompressedFilename);
                nameView.setText(name);
                final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.width = 0;
                layoutParams.weight = 1.0f;
                nameView.setLayoutParams(layoutParams);
                nameView.setGravity(Gravity.CENTER_VERTICAL);
                result.addView(nameView);
                return result;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public DictionaryInfo getItem(int position) {
                return installedDicts.get(position);
            }

            @Override
            public int getCount() {
                return installedDicts.size();
            }
        });
        dialog.show();
    }

    private void onUpDownButton(final boolean up) {
        if (isFiltered()) {
            return;
        }
        final int firstVisibleRow = getListView().getFirstVisiblePosition();
        final RowBase row = index.rows.get(firstVisibleRow);
        final TokenRow tokenRow = row.getTokenRow(true);
        final int destIndexEntry;
        if (up) {
            if (row != tokenRow) {
                destIndexEntry = tokenRow.referenceIndex;
            } else {
                destIndexEntry = Math.max(tokenRow.referenceIndex - 1, 0);
            }
        } else {
            // Down
            destIndexEntry = Math.min(tokenRow.referenceIndex + 1, index.sortedIndexEntries.size() - 1);
        }
        final Index.IndexEntry dest = index.sortedIndexEntries.get(destIndexEntry);
        Log.d(LOG, "onUpDownButton, destIndexEntry=" + dest.token);
        setSearchText(dest.token, false);
        jumpToRow(index.sortedIndexEntries.get(destIndexEntry).startRow);
        defocusSearchText();
    }

    private void onRandomWordButton() {
        int destIndexEntry = rand.nextInt(index.sortedIndexEntries.size());
        final Index.IndexEntry dest = index.sortedIndexEntries.get(destIndexEntry);
        setSearchText(dest.token, false);
        jumpToRow(index.sortedIndexEntries.get(destIndexEntry).startRow);
        defocusSearchText();
    }

    // --------------------------------------------------------------------------
    // Options Menu
    // --------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {

        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.showPrevNextButtonsKey), true)) {
            // Next word.
            nextWordMenuItem = menu.add(getString(R.string.nextWord))
                               .setIcon(R.drawable.arrow_down_float);
            MenuItemCompat.setShowAsAction(nextWordMenuItem, MenuItem.SHOW_AS_ACTION_IF_ROOM);
            nextWordMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    onUpDownButton(false);
                    return true;
                }
            });

            // Previous word.
            previousWordMenuItem = menu.add(getString(R.string.previousWord))
                                   .setIcon(R.drawable.arrow_up_float);
            MenuItemCompat.setShowAsAction(previousWordMenuItem, MenuItem.SHOW_AS_ACTION_IF_ROOM);
            previousWordMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    onUpDownButton(true);
                    return true;
                }
            });
        }

        final MenuItem randomWordMenuItem = menu.add(getString(R.string.randomWord));
        randomWordMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onRandomWordButton();
                return true;
            }
        });

        {
            final MenuItem dictionaryManager = menu.add(getString(R.string.dictionaryManager));
            MenuItemCompat.setShowAsAction(dictionaryManager, MenuItem.SHOW_AS_ACTION_NEVER);
            dictionaryManager.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(final MenuItem menuItem) {
                    startActivity(DictionaryManagerActivity.getLaunchIntent(getApplicationContext()));
                    finish();
                    return false;
                }
            });
        }

        {
            final MenuItem aboutDictionary = menu.add(getString(R.string.aboutDictionary));
            MenuItemCompat.setShowAsAction(aboutDictionary, MenuItem.SHOW_AS_ACTION_NEVER);
            aboutDictionary.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(final MenuItem menuItem) {
                    final Context context = getListView().getContext();
                    final Dialog dialog = new Dialog(context);
                    dialog.setContentView(R.layout.about_dictionary_dialog);
                    final TextView textView = dialog.findViewById(R.id.text);

                    dialog.setTitle(dictFileTitleName);

                    final StringBuilder builder = new StringBuilder();
                    final DictionaryInfo dictionaryInfo = dictionary.getDictionaryInfo();
                    if (dictionaryInfo != null) {
                        try {
                            dictionaryInfo.uncompressedBytes = dictRaf.size();
                        } catch (IOException e) {
                        }
                        builder.append(dictionaryInfo.dictInfo).append("\n\n");
                        if (dictFile != null) {
                            builder.append(getString(R.string.dictionaryPath, dictFile.getUri().toString()))
                            .append("\n");
                        }
                        builder.append(
                            getString(R.string.dictionarySize, dictionaryInfo.uncompressedBytes))
                        .append("\n");
                        builder.append(
                            getString(R.string.dictionaryCreationTime,
                                      dictionaryInfo.creationMillis)).append("\n");
                        for (final IndexInfo indexInfo : dictionaryInfo.indexInfos) {
                            builder.append("\n");
                            builder.append(getString(R.string.indexName, indexInfo.shortName))
                            .append("\n");
                            builder.append(
                                getString(R.string.mainTokenCount, indexInfo.mainTokenCount))
                            .append("\n");
                        }
                        builder.append("\n");
                        builder.append(getString(R.string.sources)).append("\n");
                        for (final EntrySource source : dictionary.sources) {
                            builder.append(
                                getString(R.string.sourceInfo, source.getName(),
                                          source.getNumEntries())).append("\n");
                        }
                    }
                    textView.setText(builder.toString());

                    dialog.show();
                    final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                    layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                    layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
                    dialog.getWindow().setAttributes(layoutParams);
                    return false;
                }
            });
        }

        DictionaryApplication.onCreateGlobalOptionsMenu(this, menu);

        return true;
    }

    // --------------------------------------------------------------------------
    // Context Menu + clicks
    // --------------------------------------------------------------------------

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        final AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final RowBase row = (RowBase) getListAdapter().getItem(adapterContextMenuInfo.position);

        if (clickOpensContextMenu && (row instanceof HtmlEntry.Row ||
            (row instanceof TokenRow && ((TokenRow)row).getIndexEntry().htmlEntries.size() > 0))) {
            final List<HtmlEntry> html = row instanceof TokenRow ? ((TokenRow)row).getIndexEntry().htmlEntries : Collections.singletonList(((HtmlEntry.Row)row).getEntry());
            final String highlight = row instanceof HtmlEntry.Row ? row.getTokenRow(true).getToken() : null;
            final MenuItem open = menu.add("Open");
            open.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    showHtml(html, highlight);
                    return false;
                }
            });
        }

        final android.view.MenuItem addToWordlist = menu.add(getString(R.string.addToWordList,
                wordList.getName()));
        addToWordlist
        .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                onAppendToWordList(row);
                return false;
            }
        });

        final android.view.MenuItem share = menu.add("Share");
        share.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, row.getTokenRow(true)
                                     .getToken());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                                     row.getRawText(saveOnlyFirstSubentry));
                startActivity(shareIntent);
                return false;
            }
        });

        final android.view.MenuItem copy = menu.add(android.R.string.copy);
        copy.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                onCopy(row);
                return false;
            }
        });

        if (selectedSpannableText != null) {
            final String selectedText = selectedSpannableText;
            final android.view.MenuItem searchForSelection = menu.add(getString(
                        R.string.searchForSelection,
                        selectedSpannableText));
            searchForSelection
            .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem item) {
                    jumpToTextFromHyperLink(selectedText, selectedSpannableIndex);
                    return false;
                }
            });
            // Rats, this won't be shown:
            //searchForSelection.setIcon(R.drawable.abs__ic_search);
        }

        if ((row instanceof TokenRow || selectedSpannableText != null) && ttsReady) {
            final android.view.MenuItem speak = menu.add(R.string.speak);
            final String textToSpeak = row instanceof TokenRow ? ((TokenRow) row).getToken() : selectedSpannableText;
            updateTTSLanguage(row instanceof TokenRow ? indexIndex : selectedSpannableIndex);
            speak.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(android.view.MenuItem item) {
                    speak(textToSpeak);
                    return false;
                }
            });
        }
        if (row instanceof PairEntry.Row && ttsReady) {
            final List<Pair> pairs = ((PairEntry.Row)row).getEntry().pairs;
            final MenuItem speakLeft = menu.add(R.string.speak_left);
            speakLeft.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(android.view.MenuItem item) {
                    int idx = index.swapPairEntries ? 1 : 0;
                    updateTTSLanguage(idx);
                    String text = "";
                    for (Pair p : pairs) text += p.get(idx);
                    text = text.replaceAll("\\{[^{}]*\\}", "").replace("{", "").replace("}", "");
                    speak(text);
                    return false;
                }
            });
            final MenuItem speakRight = menu.add(R.string.speak_right);
            speakRight.setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(android.view.MenuItem item) {
                    int idx = index.swapPairEntries ? 0 : 1;
                    updateTTSLanguage(idx);
                    String text = "";
                    for (Pair p : pairs) text += p.get(idx);
                    text = text.replaceAll("\\{[^{}]*\\}", "").replace("{", "").replace("}", "");
                    speak(text);
                    return false;
                }
            });
        }
    }

    private void jumpToTextFromHyperLink(
        final String selectedText, final int defaultIndexToUse) {
        int indexToUse = -1;
        int numFound = 0;
        for (int i = 0; i < dictionary.indices.size(); ++i) {
            final Index index = dictionary.indices.get(i);
            if (indexPrepFinished) {
                System.out.println("Doing index lookup: on " + selectedText);
                final IndexEntry indexEntry = index.findExact(selectedText);
                if (indexEntry != null) {
                    final TokenRow tokenRow = index.rows.get(indexEntry.startRow)
                                              .getTokenRow(false);
                    if (tokenRow != null && tokenRow.hasMainEntry) {
                        indexToUse = i;
                        ++numFound;
                    }
                }
            } else {
                Log.w(LOG, "Skipping findExact on index " + index.shortName);
            }
        }
        if (numFound != 1) {
            indexToUse = defaultIndexToUse;
        }
        // Without this extra delay, the call to jumpToRow that this
        // invokes doesn't always actually have any effect.
        final int actualIndexToUse = indexToUse;
        getListView().postDelayed(new Runnable() {
            @Override
            public void run() {
                setIndexAndSearchText(actualIndexToUse, selectedText, true);
                addToSearchHistory(selectedText);
            }
        }, 100);
    }

    /**
     * Called when user clicks outside of search text, so that they can start
     * typing again immediately.
     */
    private void defocusSearchText() {
        // Log.d(LOG, "defocusSearchText");
        // Request focus so that if we start typing again, it clears the text
        // input.
        getListView().requestFocus();

        // Visual indication that a new keystroke will clear the search text.
        // Doesn't seem to work unless searchText has focus.
        // searchView.selectAll();
    }

    private void onListItemClick(ListView l, View v, int rowIdx, long id) {
        defocusSearchText();
        if (clickOpensContextMenu && dictRaf != null) {
            openContextMenu(v);
        } else {
            final RowBase row = (RowBase)getListAdapter().getItem(rowIdx);
            if (!(row instanceof PairEntry.Row)) {
                v.performClick();
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void onAppendToWordList(final RowBase row) {
        defocusSearchText();

        final StringBuilder rawText = new StringBuilder();
        rawText.append(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date())).append("\t");
        rawText.append(index.longName).append("\t");
        rawText.append(row.getTokenRow(true).getToken()).append("\t");
        rawText.append(row.getRawText(saveOnlyFirstSubentry));
        Log.d(LOG, "Writing : " + rawText);

        try {
            final PrintStream out = new PrintStream(getContentResolver().openOutputStream(wordList.getUri(), "wa"));
            out.println(rawText);
            out.close();
        } catch (Exception e) {
            Log.e(LOG, "Unable to append to " + wordList.getUri().getPath(), e);
            if (!isFinishing())
                Toast.makeText(this,
                               getString(R.string.failedAddingToWordList, wordList.getUri().getPath()),
                               Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void onCopy(final RowBase row) {
        defocusSearchText();

        Log.d(LOG, "Copy, row=" + row);
        final StringBuilder result = new StringBuilder();
        result.append(row.getRawText(false));
        final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setText(result.toString());
        Log.d(LOG, "Copied: " + result);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (event.getUnicodeChar() != 0) {
            if (!searchView.hasFocus()) {
                setSearchText("" + (char) event.getUnicodeChar(), true);
                searchView.requestFocus();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Log.d(LOG, "Clearing dictionary prefs.");
            // Pretend that we just autolaunched so that we won't do it again.
            // DictionaryManagerActivity.lastAutoLaunchMillis =
            // System.currentTimeMillis();
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            Log.d(LOG, "Trying to hide soft keyboard.");
            final InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (inputManager != null && focus != null) {
                inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                                                     InputMethodManager.HIDE_NOT_ALWAYS);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setIndexAndSearchText(int newIndex, String newSearchText, boolean hideKeyboard) {
        Log.d(LOG, "Changing index to: " + newIndex);
        if (newIndex == -1) {
            Log.e(LOG, "Invalid index.");
            newIndex = 0;
        }
        if (newIndex != indexIndex) {
            indexIndex = newIndex;
            index = dictionary.indices.get(indexIndex);
            indexAdapter = new IndexAdapter(index);
            setListAdapter(indexAdapter);
            Log.d(LOG, "changingIndex, newLang=" + index.longName);
            setDictionaryPrefs(this, dictFile, index.shortName);
            updateLangButton();
        }
        setSearchText(newSearchText, true, hideKeyboard);
    }

    private void setSearchText(final String text, final boolean triggerSearch, boolean hideKeyboard) {
        Log.d(LOG, "setSearchText, text=" + text + ", triggerSearch=" + triggerSearch);
        // Disable the listener, because sometimes it doesn't work.
        searchView.setOnQueryTextListener(null);
        searchView.setQuery(text, false);
        searchView.setOnQueryTextListener(onQueryTextListener);

        if (triggerSearch) {
            onSearchTextChange(text);
        }

        // We don't want to show virtual keyboard when we're changing searchView text programatically:
        if (hideKeyboard) {
            hideKeyboard();
        }
    }

    private void setSearchText(final String text, final boolean triggerSearch) {
        setSearchText(text, triggerSearch, true);
    }

    // --------------------------------------------------------------------------
    // SearchOperation
    // --------------------------------------------------------------------------

    private void searchFinished(final SearchOperation searchOperation) {
        if (searchOperation.interrupted.get()) {
            Log.d(LOG, "Search operation was interrupted: " + searchOperation);
            return;
        }
        if (searchOperation != this.currentSearchOperation) {
            Log.d(LOG, "Stale searchOperation finished: " + searchOperation);
            return;
        }

        final Index.IndexEntry searchResult = searchOperation.searchResult;
        Log.d(LOG, "searchFinished: " + searchOperation + ", searchResult=" + searchResult);

        currentSearchOperation = null;
        // Note: it's important to post to the ListView, otherwise
        // the jumpToRow will randomly not work.
        getListView().post(new Runnable() {
            @Override
            public void run() {
                if (currentSearchOperation == null) {
                    if (searchResult != null) {
                        if (isFiltered()) {
                            clearFiltered();
                        }
                        jumpToRow(searchResult.startRow);
                    } else if (searchOperation.multiWordSearchResult != null) {
                        // Multi-row search....
                        setFiltered(searchOperation);
                    } else {
                        throw new IllegalStateException("This should never happen.");
                    }
                } else {
                    Log.d(LOG, "More coming, waiting for currentSearchOperation.");
                }
            }
        });
    }

    private void jumpToRow(final int row) {
        Log.d(LOG, "jumpToRow: " + row + ", refocusSearchText=" + false);
        // getListView().requestFocusFromTouch();
        getListView().setSelectionFromTop(row, 0);
        getListView().setSelected(true);
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    final class SearchOperation implements Runnable {

        final AtomicBoolean interrupted = new AtomicBoolean(false);

        final String searchText;

        List<String> searchTokens; // filled in for multiWord.

        final Index index;

        long searchStartMillis;

        Index.IndexEntry searchResult;

        List<RowBase> multiWordSearchResult;

        boolean done = false;

        SearchOperation(final String searchText, final Index index) {
            this.searchText = StringUtil.normalizeWhitespace(searchText);
            this.index = index;
        }

        public String toString() {
            return String.format("SearchOperation(%s,%s)", searchText, interrupted);
        }

        @Override
        public void run() {
            try {
                searchStartMillis = System.currentTimeMillis();
                final String[] searchTokenArray = WHITESPACE.split(searchText);
                if (searchTokenArray.length == 1) {
                    searchResult = index.findInsertionPoint(searchText, interrupted);
                } else {
                    searchTokens = Arrays.asList(searchTokenArray);
                    multiWordSearchResult = index.multiWordSearch(searchText, searchTokens,
                                            interrupted);
                }
                Log.d(LOG,
                      "searchText=" + searchText + ", searchDuration="
                      + (System.currentTimeMillis() - searchStartMillis)
                      + ", interrupted=" + interrupted.get());
                if (!interrupted.get()) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            searchFinished(SearchOperation.this);
                        }
                    });
                } else {
                    Log.d(LOG, "interrupted, skipping searchFinished.");
                }
            } catch (Exception e) {
                Log.e(LOG, "Failure during search (can happen during Activity close): " + e.getMessage());
            } finally {
                synchronized (this) {
                    done = true;
                    this.notifyAll();
                }
            }
        }
    }

    // --------------------------------------------------------------------------
    // IndexAdapter
    // --------------------------------------------------------------------------

    private void showHtml(final List<HtmlEntry> htmlEntries, final String htmlTextToHighlight) {
        String html = HtmlEntry.htmlBody(htmlEntries, index.shortName);
        String style = "";
        if (typeface == Typeface.SERIF) { style = "font-family: serif;"; }
        else if (typeface == Typeface.SANS_SERIF) { style = "font-family: sans-serif;"; }
        else if (typeface == Typeface.MONOSPACE) { style = "font-family: monospace;"; }
        if (application.getSelectedTheme() == DictionaryApplication.Theme.DEFAULT)
            style += "body { background-color: black; color: white; } a { color: #00aaff; }";
        // Log.d(LOG, "html=" + html);
        startActivityForResult(
            HtmlDisplayActivity.getHtmlIntent(getApplicationContext(), String.format(
                    "<html><head><meta name=\"viewport\" content=\"width=device-width\"><style type=\"text/css\">%s</style></head><body>%s</body></html>", style, html),
                                              htmlTextToHighlight, false),
            0);
    }

    final class IndexAdapter extends BaseAdapter {

        private static final float PADDING_DEFAULT_DP = 8;

        private static final float PADDING_LARGE_DP = 16;

        final Index index;

        final List<RowBase> rows;

        final Set<String> toHighlight;

        private int mPaddingDefault;

        private int mPaddingLarge;

        IndexAdapter(final Index index) {
            this.index = index;
            rows = index.rows;
            this.toHighlight = null;
            getMetrics();
        }

        IndexAdapter(final Index index, final List<RowBase> rows, final List<String> toHighlight) {
            this.index = index;
            this.rows = rows;
            this.toHighlight = new LinkedHashSet<>(toHighlight);
            getMetrics();
        }

        private void getMetrics() {
            float scale = 1;
            // Get the screen's density scale
            // The previous method getResources().getDisplayMetrics()
            // used to occasionally trigger a null pointer exception,
            // so try this instead.
            // As it still crashes, add a fallback
            try {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                scale = dm.density;
            } catch (NullPointerException ignored)
            {}
            // Convert the dps to pixels, based on density scale
            mPaddingDefault = (int) (PADDING_DEFAULT_DP * scale + 0.5f);
            mPaddingLarge = (int) (PADDING_LARGE_DP * scale + 0.5f);
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public RowBase getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).index();
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public int getItemViewType(int position) {
            final RowBase row = getItem(position);
            if (row instanceof PairEntry.Row) {
                final PairEntry entry = ((PairEntry.Row)row).getEntry();
                final int rowCount = entry.pairs.size();
                return rowCount > 1 ? 1 : 0;
            } else if (row instanceof TokenRow) {
                final IndexEntry indexEntry = ((TokenRow)row).getIndexEntry();
                return indexEntry.htmlEntries.isEmpty() ? 2 : 3;
            } else if (row instanceof HtmlEntry.Row) {
                return 4;
            } else {
                throw new IllegalArgumentException("Unsupported Row type: " + row.getClass());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final RowBase row = getItem(position);
            if (row instanceof PairEntry.Row) {
                return getView(position, (PairEntry.Row) row, parent, (TableLayout)convertView);
            } else if (row instanceof TokenRow) {
                return getView((TokenRow) row, parent, (TextView)convertView);
            } else if (row instanceof HtmlEntry.Row) {
                return getView((HtmlEntry.Row) row, parent, (TextView)convertView);
            } else {
                throw new IllegalArgumentException("Unsupported Row type: " + row.getClass());
            }
        }

        private void addBoldSpans(String token, String col1Text, Spannable col1Spannable) {
            int startPos = 0;
            while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
                col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos, startPos
                                      + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                startPos += token.length();
            }
        }

        private TableLayout getView(final int position, PairEntry.Row row, ViewGroup parent,
                                    TableLayout result) {
            final Context context = parent.getContext();
            final PairEntry entry = row.getEntry();
            final int rowCount = entry.pairs.size();
            if (result == null) {
                result = new TableLayout(context);
                result.setStretchAllColumns(true);
                // Because we have a Button inside a ListView row:
                // http://groups.google.com/group/android-developers/browse_thread/thread/3d96af1530a7d62a?pli=1
                result.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                result.setClickable(true);
                result.setFocusable(false);
                result.setLongClickable(true);
//                result.setBackgroundResource(android.R.drawable.menuitem_background);

                result.setBackgroundResource(theme.normalRowBg);
            } else if (result.getChildCount() > rowCount) {
                result.removeViews(rowCount, result.getChildCount() - rowCount);
            }

            for (int r = result.getChildCount(); r < rowCount; ++r) {
                final TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.leftMargin = mPaddingLarge;

                final TableRow tableRow = new TableRow(result.getContext());

                final TextView col1 = new TextView(tableRow.getContext());
                final TextView col2 = new TextView(tableRow.getContext());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    col1.setTextIsSelectable(true);
                    col2.setTextIsSelectable(true);
                }
                col1.setTextColor(textColorFg);
                col2.setTextColor(textColorFg);

                col1.setWidth(1);
                col2.setWidth(1);

                col1.setTypeface(typeface);
                col2.setTypeface(typeface);
                col1.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
                col2.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
                // col2.setBackgroundResource(theme.otherLangBg);

                if (index.swapPairEntries) {
                    col2.setOnLongClickListener(textViewLongClickListenerIndex0);
                    col1.setOnLongClickListener(textViewLongClickListenerIndex1);
                } else {
                    col1.setOnLongClickListener(textViewLongClickListenerIndex0);
                    col2.setOnLongClickListener(textViewLongClickListenerIndex1);
                }

                // Set the columns in the table.
                if (r == 0) {
                    tableRow.addView(col1, layoutParams);
                    tableRow.addView(col2, layoutParams);
                } else {
                    for (int i = 0; i < 2; i++) {
                        final TextView bullet = new TextView(tableRow.getContext());
                        bullet.setText(" • ");
                        LinearLayout wrapped = new LinearLayout(context);
                        wrapped.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0);
                        wrapped.addView(bullet, p1);
                        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                        wrapped.addView(i == 0 ? col1 : col2, p2);
                        tableRow.addView(wrapped, layoutParams);
                    }
                }

                result.addView(tableRow);
            }

            for (int r = 0; r < rowCount; ++r) {
                final TableRow tableRow = (TableRow)result.getChildAt(r);
                View left = tableRow.getChildAt(0);
                View right = tableRow.getChildAt(1);
                if (r > 0) {
                    left = ((ViewGroup)left).getChildAt(1);
                    right = ((ViewGroup)right).getChildAt(1);
                }
                final TextView col1 = (TextView)left;
                final TextView col2 = (TextView)right;

                // Set what's in the columns.
                final Pair pair = entry.pairs.get(r);
                final String col1Text = index.swapPairEntries ? pair.lang2 : pair.lang1;
                final String col2Text = index.swapPairEntries ? pair.lang1 : pair.lang2;
                final Spannable col1Spannable = new SpannableString(col1Text);
                final Spannable col2Spannable = new SpannableString(col2Text);

                // Bold the token instances in col1.
                if (toHighlight != null) {
                    for (final String token : toHighlight) {
                        addBoldSpans(token, col1Text, col1Spannable);
                    }
                } else
                    addBoldSpans(row.getTokenRow(true).getToken(), col1Text, col1Spannable);

                createTokenLinkSpans(col1, col1Spannable, col1Text);
                createTokenLinkSpans(col2, col2Spannable, col2Text);

                col1.setText(col1Spannable);
                col2.setText(col2Spannable);
            }

            result.setOnClickListener(new TextView.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DictionaryActivity.this.onListItemClick(getListView(), v, position, position);
                }
            });

            return result;
        }

        private TextView getPossibleLinkToHtmlEntryView(final boolean isTokenRow,
                final String text, final boolean hasMainEntry, final List<HtmlEntry> htmlEntries,
                final String htmlTextToHighlight, ViewGroup parent, TextView textView) {
            final Context context = parent.getContext();
            if (textView == null) {
                textView = new TextView(context);
                // set up things invariant across one ItemViewType
                // ItemViewTypes handled here are:
                // 2: isTokenRow == true, htmlEntries.isEmpty() == true
                // 3: isTokenRow == true, htmlEntries.isEmpty() == false
                // 4: isTokenRow == false, htmlEntries.isEmpty() == false
                textView.setPadding(isTokenRow ? mPaddingDefault : mPaddingLarge, mPaddingDefault, mPaddingDefault, 0);
                textView.setOnLongClickListener(indexIndex > 0 ? textViewLongClickListenerIndex1 : textViewLongClickListenerIndex0);
                textView.setLongClickable(true);

                textView.setTypeface(typeface);
                if (isTokenRow) {
                    textView.setTextAppearance(context, theme.tokenRowFg);
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 4 * fontSizeSp / 3);
                } else {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
                }
                textView.setTextColor(textColorFg);
                if (!htmlEntries.isEmpty()) {
                    textView.setClickable(true);
                    textView.setMovementMethod(LinkMovementMethod.getInstance());
                }
            }

            textView.setBackgroundResource(hasMainEntry ? theme.tokenRowMainBg
                                           : theme.tokenRowOtherBg);

            // Make it so we can long-click on these token rows, too:
            final Spannable textSpannable = new SpannableString(text);
            createTokenLinkSpans(textView, textSpannable, text);

            if (!htmlEntries.isEmpty()) {
                final ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                    }
                };
                textSpannable.setSpan(clickableSpan, 0, text.length(),
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                textView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showHtml(htmlEntries, htmlTextToHighlight);
                    }
                });
            }
            textView.setText(textSpannable);
            return textView;
        }

        private TextView getView(TokenRow row, ViewGroup parent, final TextView result) {
            final IndexEntry indexEntry = row.getIndexEntry();
            return getPossibleLinkToHtmlEntryView(true, indexEntry.token, row.hasMainEntry,
                                                  indexEntry.htmlEntries, null, parent, result);
        }

        private TextView getView(HtmlEntry.Row row, ViewGroup parent, final TextView result) {
            final HtmlEntry htmlEntry = row.getEntry();
            final TokenRow tokenRow = row.getTokenRow(true);
            return getPossibleLinkToHtmlEntryView(false,
                                                  getString(R.string.seeAlso, htmlEntry.title, htmlEntry.entrySource.getName()),
                                                  false, Collections.singletonList(htmlEntry), tokenRow.getToken(), parent,
                                                  result);
        }

    }

    private static final Pattern CHAR_DASH = Pattern.compile("['\\p{L}\\p{M}\\p{N}]+");

    private void createTokenLinkSpans(final TextView textView, final Spannable spannable,
                                      final String text) {
        // Saw from the source code that LinkMovementMethod sets the selection!
        // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.1_r1/android/text/method/LinkMovementMethod.java#LinkMovementMethod
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        final Matcher matcher = CHAR_DASH.matcher(text);
        while (matcher.find()) {
            spannable.setSpan(new NonLinkClickableSpan(), matcher.start(),
                              matcher.end(),
                              Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
    }

    private String selectedSpannableText = null;

    private int selectedSpannableIndex = -1;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        selectedSpannableText = null;
        selectedSpannableIndex = -1;
        return super.onTouchEvent(event);
    }

    private class TextViewLongClickListener implements OnLongClickListener {
        final int index;

        private TextViewLongClickListener(final int index) {
            this.index = index;
        }

        @Override
        public boolean onLongClick(final View v) {
            final TextView textView = (TextView) v;
            final int start = textView.getSelectionStart();
            final int end = textView.getSelectionEnd();
            if (start >= 0 && end >= 0) {
                selectedSpannableText = textView.getText().subSequence(start, end).toString();
                selectedSpannableIndex = index;
            }
            return false;
        }
    }

    private final TextViewLongClickListener textViewLongClickListenerIndex0 = new TextViewLongClickListener(
        0);

    private final TextViewLongClickListener textViewLongClickListenerIndex1 = new TextViewLongClickListener(
        1);

    // --------------------------------------------------------------------------
    // SearchText
    // --------------------------------------------------------------------------

    private void onSearchTextChange(final String text) {
        if ("thadolina".equals(text)) {
            final Dialog dialog = new Dialog(getListView().getContext());
            dialog.setContentView(R.layout.thadolina_dialog);
            dialog.setTitle("Ti amo, amore mio!");
            final ImageView imageView = dialog.findViewById(R.id.thadolina_image);
            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://sites.google.com/site/cfoxroxvday/vday2012"));
                    startActivity(intent);
                }
            });
            dialog.show();
        }
        if (dictRaf == null) {
            Log.d(LOG, "searchText changed during shutdown, doing nothing.");
            return;
        }

        // if (!searchView.hasFocus()) {
        // Log.d(LOG, "searchText changed without focus, doing nothing.");
        // return;
        // }
        Log.d(LOG, "onSearchTextChange: " + text);
        if (currentSearchOperation != null) {
            Log.d(LOG, "Interrupting currentSearchOperation.");
            currentSearchOperation.interrupted.set(true);
        }
        currentSearchOperation = new SearchOperation(text, index);
        searchExecutor.execute(currentSearchOperation);
        ((FloatingActionButton)findViewById(R.id.floatSearchButton)).setImageResource(text.length() > 0 ? R.drawable.ic_clear_black_24dp : R.drawable.ic_search_black_24dp);
        searchView.getSuggestionsAdapter().swapCursor(text.isEmpty() ? searchHistoryCursor : null);
        searchView.getSuggestionsAdapter().notifyDataSetChanged();
    }

    // --------------------------------------------------------------------------
    // Filtered results.
    // --------------------------------------------------------------------------

    private boolean isFiltered() {
        return rowsToShow != null;
    }

    private void setFiltered(final SearchOperation searchOperation) {
        if (nextWordMenuItem != null) {
            nextWordMenuItem.setEnabled(false);
            previousWordMenuItem.setEnabled(false);
        }
        rowsToShow = searchOperation.multiWordSearchResult;
        setListAdapter(new IndexAdapter(index, rowsToShow, searchOperation.searchTokens));
    }

    private void clearFiltered() {
        if (nextWordMenuItem != null) {
            nextWordMenuItem.setEnabled(true);
            previousWordMenuItem.setEnabled(true);
        }
        setListAdapter(new IndexAdapter(index));
        rowsToShow = null;
    }

}
