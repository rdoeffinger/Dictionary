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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.BufferType;
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

public class DictionaryActivity extends ListActivity {

    static final String LOG = "QuickDic";

    DictionaryApplication application;

    File dictFile = null;

    RandomAccessFile dictRaf = null;

    Dictionary dictionary = null;

    int indexIndex = 0;

    Index index = null;

    List<RowBase> rowsToShow = null; // if not null, just show these rows.

    // package for test.
    final Handler uiHandler = new Handler();

    TextToSpeech textToSpeech;
    volatile boolean ttsReady;
    
    int textColorFg = Color.BLACK;
    

    private final Executor searchExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "searchExecutor");
        }
    });

    private SearchOperation currentSearchOperation = null;

    C.Theme theme = C.Theme.LIGHT;

    Typeface typeface;

    int fontSizeSp;

    EditText searchText;

    Button langButton;

    // Never null.
    private File wordList = null;

    private boolean saveOnlyFirstSubentry = false;

    private boolean clickOpensContextMenu = false;

    // Visible for testing.
    ListAdapter indexAdapter = null;

    final SearchTextWatcher searchTextWatcher = new SearchTextWatcher();

    /**
     * For some languages, loading the transliterators used in this search takes
     * a long time, so we fire it up on a different thread, and don't invoke it
     * from the main thread until it's already finished once.
     */
    private volatile boolean indexPrepFinished = false;

    public DictionaryActivity() {
    }

    public static Intent getLaunchIntent(final File dictFile, final int indexIndex,
            final String searchToken) {
        final Intent intent = new Intent();
        intent.setClassName(DictionaryActivity.class.getPackage().getName(),
                DictionaryActivity.class.getName());
        intent.putExtra(C.DICT_FILE, dictFile.getPath());
        intent.putExtra(C.INDEX_INDEX, indexIndex);
        intent.putExtra(C.SEARCH_TOKEN, searchToken);
        return intent;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOG, "onSaveInstanceState: " + searchText.getText().toString());
        outState.putInt(C.INDEX_INDEX, indexIndex);
        outState.putString(C.SEARCH_TOKEN, searchText.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(final Bundle outState) {
        super.onRestoreInstanceState(outState);
        Log.d(LOG, "onRestoreInstanceState: " + outState.getString(C.SEARCH_TOKEN));
        onCreate(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().remove(C.INDEX_INDEX).commit(); // Don't auto-launch if
                                                     // this fails.

        setTheme(((DictionaryApplication) getApplication()).getSelectedTheme().themeId);

        Log.d(LOG, "onCreate:" + this);
        super.onCreate(savedInstanceState);

        application = (DictionaryApplication) getApplication();
        theme = application.getSelectedTheme();
        textColorFg = getResources().getColor(theme.tokenRowFgColor);

        

        final Intent intent = getIntent();
        String intentAction = intent.getAction();
        /**
         * @author Dominik Köppl
         * Querying the Intent
         * com.hughes.action.ACTION_SEARCH_DICT is the advanced query
         * Arguments:
         * SearchManager.QUERY -> the phrase to search
         * from -> language in which the phrase is written
         * to -> to which language shall be translated
         */
        if(intentAction != null && intentAction.equals("com.hughes.action.ACTION_SEARCH_DICT")) 
        {
        	String query = intent.getStringExtra(SearchManager.QUERY);
        	String from = intent.getStringExtra("from");
        	if(from != null) from = from.toLowerCase(Locale.US);
        	String to = intent.getStringExtra("to");
        	if(to != null) to = to.toLowerCase(Locale.US);
        	if(query != null)
        	{
        		getIntent().putExtra(C.SEARCH_TOKEN, query);
        	}
        	if(intent.getStringExtra(C.DICT_FILE) == null && (from != null || to != null))
        	{
        		 Log.d(LOG, "DictSearch: from: " + from + " to " + to);
        		List<DictionaryInfo> dicts = application.getUsableDicts();
        		for(DictionaryInfo info : dicts)
        		{
        			boolean hasFrom = from == null;
        			boolean hasTo = to == null;
        			for(IndexInfo index : info.indexInfos)
        			{
        				if(!hasFrom && index.shortName.toLowerCase(Locale.US).equals(from)) hasFrom = true;
        				if(!hasTo && index.shortName.toLowerCase(Locale.US).equals(to)) hasTo = true;
        			}
        			if(hasFrom && hasTo)
        			{
        				if(from != null)
        				{
        					int which_index = 0;
                			for(;which_index < info.indexInfos.size(); ++which_index)
                			{
                				if(info.indexInfos.get(which_index).shortName.toLowerCase(Locale.US).equals(from))
                					break;
                			}
                			intent.putExtra(C.INDEX_INDEX, which_index);
                			
        				}
        				intent.putExtra(C.DICT_FILE, application.getPath(info.uncompressedFilename).toString());
        				break;
        			}
        		}
        		
        	}
        }
        /**
         * @author Dominik Köppl
         * Querying the Intent
         * Intent.ACTION_SEARCH is a simple query
         * Arguments follow from android standard (see documentation)
         */
        if (intentAction != null && intentAction.equals(Intent.ACTION_SEARCH)) 
        {
            String query = intent.getStringExtra(SearchManager.QUERY);
    		if(query != null) getIntent().putExtra(C.SEARCH_TOKEN,query);
        }
        /**
         * @author Dominik Köppl
         * If no dictionary is chosen, use the default dictionary specified in the preferences
         * If this step does fail (no default directory specified), show a toast and abort.
         */
        if(intent.getStringExtra(C.DICT_FILE) == null)
        {
        	String dictfile = prefs.getString(getString(R.string.defaultDicKey), null);
        	if(dictfile != null) intent.putExtra(C.DICT_FILE, application.getPath(dictfile).toString());
        }
        String dictFilename = intent.getStringExtra(C.DICT_FILE);
        
        if(dictFilename == null)
        {
            Toast.makeText(this, getString(R.string.no_dict_file), Toast.LENGTH_LONG).show();
            startActivity(DictionaryManagerActivity.getLaunchIntent());
            finish();
            return;
        }
        if(dictFilename != null) dictFile = new File(dictFilename);

        ttsReady = false;
        textToSpeech = new TextToSpeech(getApplicationContext(), new OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = true;
                updateTTSLanguage();
            }
        });
        
        try {
            final String name = application.getDictionaryName(dictFile.getName());
            this.setTitle("QuickDic: " + name);
            dictRaf = new RandomAccessFile(dictFile, "r");
            dictionary = new Dictionary(dictRaf);
        } catch (Exception e) {
            Log.e(LOG, "Unable to load dictionary.", e);
            if (dictRaf != null) {
                try {
                    dictRaf.close();
                } catch (IOException e1) {
                    Log.e(LOG, "Unable to close dictRaf.", e1);
                }
                dictRaf = null;
            }
            Toast.makeText(this, getString(R.string.invalidDictionary, "", e.getMessage()),
                    Toast.LENGTH_LONG).show();
            startActivity(DictionaryManagerActivity.getLaunchIntent());
            finish();
            return;
        }
        indexIndex = intent.getIntExtra(C.INDEX_INDEX, 0);
        if (savedInstanceState != null) {
            indexIndex = savedInstanceState.getInt(C.INDEX_INDEX, indexIndex);
        }
        indexIndex %= dictionary.indices.size();
        Log.d(LOG, "Loading index " + indexIndex);
        index = dictionary.indices.get(indexIndex);
        setListAdapter(new IndexAdapter(index));

        // Pre-load the collators.
        new Thread(new Runnable() {
            public void run() {
                final long startMillis = System.currentTimeMillis();
                try {
                    TransliteratorManager.init(new TransliteratorManager.Callback() {
                        @Override
                        public void onTransliteratorReady() {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    onSearchTextChange(searchText.getText().toString());
                                }
                            });
                        }
                    });

                    for (final Index index : dictionary.indices) {
                        final String searchToken = index.sortedIndexEntries.get(0).token;
                        final IndexEntry entry = index.findExact(searchToken);
                        if (!searchToken.equals(entry.token)) {
                            Log.e(LOG, "Couldn't find token: " + searchToken + ", " + entry.token);
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

        String fontName = prefs.getString(getString(R.string.fontKey), "FreeSerif.ttf.jpg");
        if ("SYSTEM".equals(fontName)) {
            typeface = Typeface.DEFAULT;
        } else {
            try {
                typeface = Typeface.createFromAsset(getAssets(), fontName);
            } catch (Exception e) {
                Log.w(LOG, "Exception trying to use typeface, using default.", e);
                Toast.makeText(this, getString(R.string.fontFailure, e.getLocalizedMessage()),
                        Toast.LENGTH_LONG).show();
            }
        }
        // if (!"SYSTEM".equals(fontName)) {
        // throw new RuntimeException("Test force using system font: " +
        // fontName);
        // }
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

        setContentView(R.layout.dictionary_activity);
        searchText = (EditText) findViewById(R.id.SearchText);
        searchText.requestFocus();
        searchText.addTextChangedListener(searchTextWatcher);
        searchText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Log.d(LOG, "searchText onFocusChange hasFocus=" + hasFocus);
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
        setSearchText(text, true);
        Log.d(LOG, "Trying to restore searchText=" + text);

        final View clearSearchTextButton = findViewById(R.id.ClearSearchTextButton);
        clearSearchTextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onClearSearchTextButton();
            }
        });
        clearSearchTextButton.setVisibility(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.showClearSearchTextButtonKey), true) ? View.VISIBLE
                : View.GONE);

        langButton = (Button) findViewById(R.id.LangButton);
        langButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onLanguageButton();
            }
        });
        langButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onLanguageButtonLongClick(v.getContext());
                return true;
            }
        });
        updateLangButton();

        final View upButton = findViewById(R.id.UpButton);
        upButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onUpDownButton(true);
            }
        });
        final View downButton = findViewById(R.id.DownButton);
        downButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onUpDownButton(false);
            }
        });
        upButton.setVisibility(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.showPrevNextButtonsKey), true) ? View.VISIBLE
                : View.GONE);
        downButton.setVisibility(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.showPrevNextButtonsKey), true) ? View.VISIBLE
                : View.GONE);

//        getListView().setOnItemSelectedListener(new ListView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View arg1, final int position,
//                    long id) {
//                if (!searchText.isFocused()) {
//                    if (!isFiltered()) {
//                        final RowBase row = (RowBase) getListAdapter().getItem(position);
//                        Log.d(LOG, "onItemSelected: " + row.index());
//                        final TokenRow tokenRow = row.getTokenRow(true);
//                        searchText.setText(tokenRow.getToken());
//                    }
//                }
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> arg0) {
//            }
//        });
//
        // ContextMenu.
        registerForContextMenu(getListView());

        // Prefs.
        wordList = new File(prefs.getString(getString(R.string.wordListFileKey),
                getString(R.string.wordListFileDefault)));
        saveOnlyFirstSubentry = prefs.getBoolean(getString(R.string.saveOnlyFirstSubentryKey),
                false);
        clickOpensContextMenu = prefs.getBoolean(getString(R.string.clickOpensContextMenuKey),
                false);
        // if (prefs.getBoolean(getString(R.string.vibrateOnFailedSearchKey),
        // true)) {
        // vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // }
        Log.d(LOG, "wordList=" + wordList + ", saveOnlyFirstSubentry=" + saveOnlyFirstSubentry);

        setDictionaryPrefs(this, dictFile, indexIndex, searchText.getText().toString());
        

        
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

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (result != null && result.hasExtra(C.SEARCH_TOKEN)) {
            Log.d(LOG, "onActivityResult: " + result.getStringExtra(C.SEARCH_TOKEN));
            jumpToTextFromHyperLink(result.getStringExtra(C.SEARCH_TOKEN), indexIndex);
        }
    }

    private static void setDictionaryPrefs(final Context context, final File dictFile,
            final int indexIndex, final String searchToken) {
        final SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(
                context).edit();
        prefs.putString(C.DICT_FILE, dictFile.getPath());
        prefs.putInt(C.INDEX_INDEX, indexIndex);
        prefs.putString(C.SEARCH_TOKEN, ""); // Don't need to save search token.
        prefs.commit();
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

    private void onClearSearchTextButton() {
        setSearchText("", true);
        showKeyboard();
    }

    private void showKeyboard() {
        searchText.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG, "Trying to show soft keyboard.");
                final boolean searchTextHadFocus = searchText.hasFocus();
                searchText.requestFocus();
                final InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                manager.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
                if (!searchTextHadFocus) {
                    defocusSearchText();
                }
            }}, 100);
    }

    void updateLangButton() {
        // final LanguageResources languageResources =
        // Language.isoCodeToResources.get(index.shortName);
        // if (languageResources != null && languageResources.flagId != 0) {
        // langButton.setCompoundDrawablesWithIntrinsicBounds(0, 0,
        // languageResources.flagId, 0);
        // } else {
        // langButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        langButton.setText(index.shortName);
        // }
        updateTTSLanguage();
    }

    private void updateTTSLanguage() {
        if (!ttsReady || index == null || textToSpeech == null) {
            Log.d(LOG, "Can't updateTTSLanguage.");
            return;
        }
        final Locale locale = new Locale(index.sortLanguage.getIsoCode());
        Log.d(LOG, "Setting TTS locale to: " + locale);
        final int ttsResult = textToSpeech.setLanguage(locale);
        if (ttsResult != TextToSpeech.LANG_AVAILABLE || 
            ttsResult != TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            Log.e(LOG, "TTS not available in this language: ttsResult=" + ttsResult);
        }
    }

    void onLanguageButton() {
        if (currentSearchOperation != null) {
            currentSearchOperation.interrupted.set(true);
            currentSearchOperation = null;
        }
        changeIndexAndResearch((indexIndex + 1) % dictionary.indices.size());
    }

    void onLanguageButtonLongClick(final Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.select_dictionary_dialog);
        dialog.setTitle(R.string.selectDictionary);

        final List<DictionaryInfo> installedDicts = ((DictionaryApplication) getApplication())
                .getUsableDicts();

        ListView listView = (ListView) dialog.findViewById(android.R.id.list);

        // final LinearLayout.LayoutParams layoutParams = new
        // LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        // ViewGroup.LayoutParams.WRAP_CONTENT);
        // layoutParams.width = 0;
        // layoutParams.weight = 1.0f;

        final Button button = new Button(listView.getContext());
        final String name = getString(R.string.dictionaryManager);
        button.setText(name);
        final IntentLauncher intentLauncher = new IntentLauncher(listView.getContext(),
                DictionaryManagerActivity.getLaunchIntent()) {
            @Override
            protected void onGo() {
                dialog.dismiss();
                DictionaryActivity.this.finish();
            };
        };
        button.setOnClickListener(intentLauncher);
        // button.setLayoutParams(layoutParams);
        listView.addHeaderView(button);
        // listView.setHeaderDividersEnabled(true);

        listView.setAdapter(new BaseAdapter() {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final DictionaryInfo dictionaryInfo = getItem(position);

                final LinearLayout result = new LinearLayout(parent.getContext());

                for (int i = 0; i < dictionaryInfo.indexInfos.size(); ++i) {
                    if (i > 0) {
                        final TextView dash = new TextView(parent.getContext());
                        dash.setText("-");
                        result.addView(dash);
                    }

                    final IndexInfo indexInfo = dictionaryInfo.indexInfos.get(i);
                    final Button button = new Button(parent.getContext());
                    button.setText(indexInfo.shortName);
                    final IntentLauncher intentLauncher = new IntentLauncher(parent.getContext(),
                            getLaunchIntent(
                                    application.getPath(dictionaryInfo.uncompressedFilename),
                                    i, searchText.getText().toString())) {
                        @Override
                        protected void onGo() {
                            dialog.dismiss();
                            DictionaryActivity.this.finish();
                        };
                    };
                    button.setOnClickListener(intentLauncher);
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

    private void changeIndexAndResearch(int newIndex) {
        Log.d(LOG, "Changing index to: " + newIndex);
        if (newIndex == -1) {
            Log.e(LOG, "Invalid index.");
            newIndex = 0;
        }
        indexIndex = newIndex;
        index = dictionary.indices.get(indexIndex);
        indexAdapter = new IndexAdapter(index);
        Log.d(LOG, "changingIndex, newLang=" + index.longName);
        setDictionaryPrefs(this, dictFile, indexIndex, searchText.getText().toString());
        setListAdapter(indexAdapter);
        updateLangButton();
        setSearchText(searchText.getText().toString(), true);
    }

    void onUpDownButton(final boolean up) {
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
            destIndexEntry = Math.min(tokenRow.referenceIndex + 1, index.sortedIndexEntries.size());
        }
        final Index.IndexEntry dest = index.sortedIndexEntries.get(destIndexEntry);
        Log.d(LOG, "onUpDownButton, destIndexEntry=" + dest.token);
        setSearchText(dest.token, false);
        jumpToRow(index.sortedIndexEntries.get(destIndexEntry).startRow);
        defocusSearchText();
    }

    // --------------------------------------------------------------------------
    // Options Menu
    // --------------------------------------------------------------------------

    final Random random = new Random();

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        application.onCreateGlobalOptionsMenu(this, menu);

//        {
//            final MenuItem randomWord = menu.add(getString(R.string.randomWord));
//            randomWord.setOnMenuItemClickListener(new OnMenuItemClickListener() {
//                public boolean onMenuItemClick(final MenuItem menuItem) {
//                    final String word = index.sortedIndexEntries.get(random
//                            .nextInt(index.sortedIndexEntries.size())).token;
//                    setSearchText(word, true);
//                    return false;
//                }
//            });
//        }

        {
            final MenuItem dictionaryList = menu.add(getString(R.string.dictionaryManager));
            dictionaryList.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(final MenuItem menuItem) {
                    startActivity(DictionaryManagerActivity.getLaunchIntent());
                    finish();
                    return false;
                }
            });
        }

        {
            final MenuItem aboutDictionary = menu.add(getString(R.string.aboutDictionary));
            aboutDictionary.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(final MenuItem menuItem) {
                    final Context context = getListView().getContext();
                    final Dialog dialog = new Dialog(context);
                    dialog.setContentView(R.layout.about_dictionary_dialog);
                    final TextView textView = (TextView) dialog.findViewById(R.id.text);

                    final String name = application.getDictionaryName(dictFile.getName());
                    dialog.setTitle(name);

                    final StringBuilder builder = new StringBuilder();
                    final DictionaryInfo dictionaryInfo = dictionary.getDictionaryInfo();
                    dictionaryInfo.uncompressedBytes = dictFile.length();
                    if (dictionaryInfo != null) {
                        builder.append(dictionaryInfo.dictInfo).append("\n\n");
                        builder.append(getString(R.string.dictionaryPath, dictFile.getPath()))
                                .append("\n");
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
                    // } else {
                    // builder.append(getString(R.string.invalidDictionary));
                    // }
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

        return true;
    }

    // --------------------------------------------------------------------------
    // Context Menu + clicks
    // --------------------------------------------------------------------------

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final RowBase row = (RowBase) getListAdapter().getItem(adapterContextMenuInfo.position);

        final MenuItem addToWordlist = menu.add(getString(R.string.addToWordList,
                wordList.getName()));
        addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                onAppendToWordList(row);
                return false;
            }
        });

        final MenuItem share = menu.add("Share");
        share.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, row.getTokenRow(true).getToken());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, row.getRawText(saveOnlyFirstSubentry));
                startActivity(shareIntent);
                return false;
            }
        });

        final MenuItem copy = menu.add(android.R.string.copy);
        copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                onCopy(row);
                return false;
            }
        });

        if (selectedSpannableText != null) {
            final String selectedText = selectedSpannableText;
            final MenuItem searchForSelection = menu.add(getString(R.string.searchForSelection,
                    selectedSpannableText));
            searchForSelection.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    jumpToTextFromHyperLink(selectedText, selectedSpannableIndex);
                    return false;
                }
            });
        }

        if (row instanceof TokenRow && ttsReady) {
            final MenuItem speak = menu.add(R.string.speak);
            speak.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    textToSpeech.speak(((TokenRow) row).getToken(), TextToSpeech.QUEUE_FLUSH,
                            new HashMap<String, String>());
                    return false;
                }
            });
        }
    }

    private void jumpToTextFromHyperLink(final String selectedText, final int defaultIndexToUse) {
        int indexToUse = -1;
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
                        break;
                    }
                }
            } else {
                Log.w(LOG, "Skipping findExact on index " + index.shortName);
            }
        }
        if (indexToUse == -1) {
            indexToUse = defaultIndexToUse;
        }
        final boolean changeIndex = indexIndex != indexToUse;
        if (changeIndex) {
            setSearchText(selectedText, false);
            changeIndexAndResearch(indexToUse);
        } else {
            setSearchText(selectedText, true);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int row, long id) {
        defocusSearchText();
        if (clickOpensContextMenu && dictRaf != null) {
            openContextMenu(v);
        }
    }

    void onAppendToWordList(final RowBase row) {
        defocusSearchText();

        final StringBuilder rawText = new StringBuilder();
        rawText.append(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date())).append("\t");
        rawText.append(index.longName).append("\t");
        rawText.append(row.getTokenRow(true).getToken()).append("\t");
        rawText.append(row.getRawText(saveOnlyFirstSubentry));
        Log.d(LOG, "Writing : " + rawText);

        try {
            wordList.getParentFile().mkdirs();
            final PrintWriter out = new PrintWriter(new FileWriter(wordList, true));
            out.println(rawText.toString());
            out.close();
        } catch (IOException e) {
            Log.e(LOG, "Unable to append to " + wordList.getAbsolutePath(), e);
            Toast.makeText(this,
                    getString(R.string.failedAddingToWordList, wordList.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        }
        return;
    }

    /**
     * Called when user clicks outside of search text, so that they can start
     * typing again immediately.
     */
    void defocusSearchText() {
        // Log.d(LOG, "defocusSearchText");
        // Request focus so that if we start typing again, it clears the text
        // input.
        getListView().requestFocus();

        // Visual indication that a new keystroke will clear the search text.
        // Doesn't seem to work unless earchText has focus.
        // searchText.selectAll();
    }

    @SuppressWarnings("deprecation")
    void onCopy(final RowBase row) {
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
            if (!searchText.hasFocus()) {
                setSearchText("" + (char) event.getUnicodeChar(), true);
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
            inputManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setSearchText(final String text, final boolean triggerSearch) {
        if (!triggerSearch) {
            getListView().requestFocus();
        }
        searchText.setText(text);
        searchText.requestFocus();
        moveCursorToRight();
        if (triggerSearch) {
            onSearchTextChange(text);
        }
    }

    private long cursorDelayMillis = 100;

    private void moveCursorToRight() {
        if (searchText.getLayout() != null) {
            cursorDelayMillis = 100;
            // Surprising, but this can crash when you rotate...
            Selection.moveToRightEdge(searchText.getText(), searchText.getLayout());
        } else {
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    moveCursorToRight();
                }
            }, cursorDelayMillis);
            cursorDelayMillis = Math.min(10 * 1000, 2 * cursorDelayMillis);
        }
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
        uiHandler.postDelayed(new Runnable() {
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
        }, 20);
    }

    private final void jumpToRow(final int row) {
        final boolean refocusSearchText = searchText.hasFocus();
        Log.d(LOG, "jumpToRow: " + row + ", refocusSearchText=" + refocusSearchText);
        getListView().requestFocusFromTouch();
        getListView().setSelectionFromTop(row, 0);
        getListView().setSelected(true);
        if (refocusSearchText) {            
            searchText.requestFocus();
        }
        //Log.d(LOG, "getSelectedItemPosition():" + getSelectedItemPosition());
    }

    static final Pattern WHITESPACE = Pattern.compile("\\s+");

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
            return String.format("SearchOperation(%s,%s)", searchText, interrupted.toString());
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
                    multiWordSearchResult = index.multiWordSearch(searchText, searchTokens, interrupted);
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
                Log.e(LOG, "Failure during search (can happen during Activity close.");
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

    static ViewGroup.LayoutParams WEIGHT_1 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);

    static ViewGroup.LayoutParams WEIGHT_0 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 0.0f);

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
            this.toHighlight = new LinkedHashSet<String>(toHighlight);
            getMetrics();
        }

        private void getMetrics() {
            // Get the screen's density scale
            final float scale = getResources().getDisplayMetrics().density;
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
        public TableLayout getView(int position, View convertView, ViewGroup parent) {
            final TableLayout result;
            if (convertView instanceof TableLayout) {
                result = (TableLayout) convertView;
                result.removeAllViews();
            } else {
                result = new TableLayout(parent.getContext());
            }
            final RowBase row = getItem(position);
            if (row instanceof PairEntry.Row) {
                return getView(position, (PairEntry.Row) row, parent, result);
            } else if (row instanceof TokenRow) {
                return getView((TokenRow) row, parent, result);
            } else if (row instanceof HtmlEntry.Row) {
                return getView((HtmlEntry.Row) row, parent, result);
            } else {
                throw new IllegalArgumentException("Unsupported Row type: " + row.getClass());
            }
        }

        private TableLayout getView(final int position, PairEntry.Row row, ViewGroup parent,
                final TableLayout result) {
            final PairEntry entry = row.getEntry();
            final int rowCount = entry.pairs.size();

            final TableRow.LayoutParams layoutParams = new TableRow.LayoutParams();
            layoutParams.weight = 0.5f;
            layoutParams.leftMargin = mPaddingLarge;

            for (int r = 0; r < rowCount; ++r) {
                final TableRow tableRow = new TableRow(result.getContext());

                final TextView col1 = new TextView(tableRow.getContext());
                final TextView col2 = new TextView(tableRow.getContext());

                // Set the columns in the table.
                if (r > 0) {
                    final TextView bullet = new TextView(tableRow.getContext());
                    bullet.setText(" • ");
                    tableRow.addView(bullet);
                }
                tableRow.addView(col1, layoutParams);
                final TextView margin = new TextView(tableRow.getContext());
                margin.setText(" ");
                tableRow.addView(margin);
                if (r > 0) {
                    final TextView bullet = new TextView(tableRow.getContext());
                    bullet.setText(" • ");
                    tableRow.addView(bullet);
                }
                tableRow.addView(col2, layoutParams);
                col1.setWidth(1);
                col2.setWidth(1);

                // Set what's in the columns.

                final Pair pair = entry.pairs.get(r);
                final String col1Text = index.swapPairEntries ? pair.lang2 : pair.lang1;
                final String col2Text = index.swapPairEntries ? pair.lang1 : pair.lang2;

                col1.setText(col1Text, TextView.BufferType.SPANNABLE);
                col2.setText(col2Text, TextView.BufferType.SPANNABLE);

                // Bold the token instances in col1.
                final Set<String> toBold = toHighlight != null ? this.toHighlight : Collections
                        .singleton(row.getTokenRow(true).getToken());
                final Spannable col1Spannable = (Spannable) col1.getText();
                for (final String token : toBold) {
                    int startPos = 0;
                    while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
                        col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos, startPos
                                + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                        startPos += token.length();
                    }
                }

                createTokenLinkSpans(col1, col1Spannable, col1Text);
                createTokenLinkSpans(col2, (Spannable) col2.getText(), col2Text);

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

                result.addView(tableRow);
            }

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
                    DictionaryActivity.this.onListItemClick(getListView(), v, position, position);
                }
            });

            return result;
        }

        private TableLayout getPossibleLinkToHtmlEntryView(final boolean isTokenRow,
                final String text, final boolean hasMainEntry, final List<HtmlEntry> htmlEntries,
                final String htmlTextToHighlight, ViewGroup parent, final TableLayout result) {
            final Context context = parent.getContext();

            final TableRow tableRow = new TableRow(result.getContext());
            tableRow.setBackgroundResource(hasMainEntry ? theme.tokenRowMainBg
                    : theme.tokenRowOtherBg);
            if (isTokenRow) {
                tableRow.setPadding(mPaddingDefault, mPaddingDefault, mPaddingDefault, 0);
            } else {
                tableRow.setPadding(mPaddingLarge, mPaddingDefault, mPaddingDefault, 0);
            }
            result.addView(tableRow);

            // Make it so we can long-click on these token rows, too:
            final TextView textView = new TextView(context);
            textView.setText(text, BufferType.SPANNABLE);
            createTokenLinkSpans(textView, (Spannable) textView.getText(), text);
            final TextViewLongClickListener textViewLongClickListenerIndex0 = new TextViewLongClickListener(
                    0);
            textView.setOnLongClickListener(textViewLongClickListenerIndex0);
            result.setLongClickable(true);
            
            // Doesn't work:
            // textView.setTextColor(android.R.color.secondary_text_light);
            textView.setTypeface(typeface);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(0);
            if (isTokenRow) {
                textView.setTextAppearance(context, theme.tokenRowFg);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 4 * fontSizeSp / 3);
            } else {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
            }
            lp.weight = 1.0f;

            textView.setLayoutParams(lp);
            tableRow.addView(textView);

            if (!htmlEntries.isEmpty()) {
                final ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                    }
                };
                ((Spannable) textView.getText()).setSpan(clickableSpan, 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                result.setClickable(true);
                textView.setClickable(true);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String html = HtmlEntry.htmlBody(htmlEntries, index.shortName);
                        //Log.d(LOG, "html=" + html);
                        startActivityForResult(
                                HtmlDisplayActivity.getHtmlIntent(String.format(
                                        "<html><head></head><body>%s</body></html>", html),
                                        htmlTextToHighlight, false),
                                0);
                    }
                });
            }
            return result;
        }

        private TableLayout getView(TokenRow row, ViewGroup parent, final TableLayout result) {
            final IndexEntry indexEntry = row.getIndexEntry();
            return getPossibleLinkToHtmlEntryView(true, indexEntry.token, row.hasMainEntry,
                    indexEntry.htmlEntries, null, parent, result);
        }

        private TableLayout getView(HtmlEntry.Row row, ViewGroup parent, final TableLayout result) {
            final HtmlEntry htmlEntry = row.getEntry();
            final TokenRow tokenRow = row.getTokenRow(true);
            return getPossibleLinkToHtmlEntryView(false,
                    getString(R.string.seeAlso, htmlEntry.title, htmlEntry.entrySource.getName()),
                    false, Collections.singletonList(htmlEntry), tokenRow.getToken(), parent,
                    result);
        }

    }

    static final Pattern CHAR_DASH = Pattern.compile("['\\p{L}\\p{M}\\p{N}]+");
    
    private void createTokenLinkSpans(final TextView textView, final Spannable spannable,
            final String text) {
        // Saw from the source code that LinkMovementMethod sets the selection!
        // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.1_r1/android/text/method/LinkMovementMethod.java#LinkMovementMethod
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        final Matcher matcher = CHAR_DASH.matcher(text);
        while (matcher.find()) {
            spannable.setSpan(new NonLinkClickableSpan(textColorFg), matcher.start(), matcher.end(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
    }

    String selectedSpannableText = null;

    int selectedSpannableIndex = -1;

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

    final TextViewLongClickListener textViewLongClickListenerIndex0 = new TextViewLongClickListener(
            0);

    final TextViewLongClickListener textViewLongClickListenerIndex1 = new TextViewLongClickListener(
            1);

    // --------------------------------------------------------------------------
    // SearchText
    // --------------------------------------------------------------------------

    void onSearchTextChange(final String text) {
        if ("thadolina".equals(text)) {
            final Dialog dialog = new Dialog(getListView().getContext());
            dialog.setContentView(R.layout.thadolina_dialog);
            dialog.setTitle("Ti amo, amore mio!");
            final ImageView imageView = (ImageView) dialog.findViewById(R.id.thadolina_image);
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
        if (!searchText.isFocused()) {
            Log.d(LOG, "searchText changed without focus, doing nothing.");
            return;
        }
        Log.d(LOG, "onSearchTextChange: " + text);
        if (currentSearchOperation != null) {
            Log.d(LOG, "Interrupting currentSearchOperation.");
            currentSearchOperation.interrupted.set(true);
        }
        currentSearchOperation = new SearchOperation(text, index);
        searchExecutor.execute(currentSearchOperation);
    }

    private class SearchTextWatcher implements TextWatcher {
        public void afterTextChanged(final Editable searchTextEditable) {
            if (searchText.hasFocus()) {
                Log.d(LOG, "SearchTextWatcher: Search text changed with focus: " + searchText.getText());
                // If they were typing to cause the change, update the UI.
                onSearchTextChange(searchText.getText().toString());
            }
        }

        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        }

        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        }
    }

    // --------------------------------------------------------------------------
    // Filtered results.
    // --------------------------------------------------------------------------

    boolean isFiltered() {
        return rowsToShow != null;
    }

    void setFiltered(final SearchOperation searchOperation) {
        ((ImageButton) findViewById(R.id.UpButton)).setEnabled(false);
        ((ImageButton) findViewById(R.id.DownButton)).setEnabled(false);
        rowsToShow = searchOperation.multiWordSearchResult;
        setListAdapter(new IndexAdapter(index, rowsToShow, searchOperation.searchTokens));
    }

    void clearFiltered() {
        ((ImageButton) findViewById(R.id.UpButton)).setEnabled(true);
        ((ImageButton) findViewById(R.id.DownButton)).setEnabled(true);
        setListAdapter(new IndexAdapter(index));
        rowsToShow = null;
    }

}
