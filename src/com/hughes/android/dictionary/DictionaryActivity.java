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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
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
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import com.hughes.android.dictionary.engine.Index;
import com.hughes.android.dictionary.engine.PairEntry;
import com.hughes.android.dictionary.engine.Index.IndexEntry;
import com.hughes.android.dictionary.engine.PairEntry.Pair;
import com.hughes.android.dictionary.engine.RowBase;
import com.hughes.android.dictionary.engine.TokenRow;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.IntentLauncher;
import com.hughes.android.util.NonLinkClickableSpan;

public class DictionaryActivity extends ListActivity {

  static final String LOG = "QuickDic";

  private String initialSearchText;

  DictionaryApplication application;
  File dictFile = null;
  RandomAccessFile dictRaf = null;
  Dictionary dictionary = null;
  int indexIndex = 0;
  Index index = null;
  List<RowBase> rowsToShow = null;  // if not null, just show these rows.
  
  // package for test.
  final Handler uiHandler = new Handler();
  private final Executor searchExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "searchExecutor");
    }
  });
  private SearchOperation currentSearchOperation = null;

  C.Theme theme = C.Theme.LIGHT;
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


  public DictionaryActivity() {
  }
  
  public static Intent getLaunchIntent(final File dictFile, final int indexIndex, final String searchToken) {
    final Intent intent = new Intent();
    intent.setClassName(DictionaryActivity.class.getPackage().getName(), DictionaryActivity.class.getName());
    intent.putExtra(C.DICT_FILE, dictFile.getPath());
    intent.putExtra(C.INDEX_INDEX, indexIndex);
    intent.putExtra(C.SEARCH_TOKEN, searchToken);
    return intent;
  }
  
  @Override
  protected void onSaveInstanceState(final Bundle outState) {
    super.onSaveInstanceState(outState);
    Log.d(LOG, "onSaveInstanceState: " + searchText.getText().toString());
    outState.putString(C.SEARCH_TOKEN, searchText.getText().toString());
  }

  @Override
  protected void onRestoreInstanceState(final Bundle outState) {
    super.onRestoreInstanceState(outState);
    Log.d(LOG, "onRestoreInstanceState: " + outState.getString(C.SEARCH_TOKEN));
    initialSearchText = outState.getString(C.SEARCH_TOKEN);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) { 
    setTheme(((DictionaryApplication)getApplication()).getSelectedTheme().themeId);

    Log.d(LOG, "onCreate:" + this);
    super.onCreate(savedInstanceState);

    application = (DictionaryApplication) getApplication();
    theme = application.getSelectedTheme();

    // Clear them so that if something goes wrong, we won't relaunch.
    clearDictionaryPrefs(this);
    
    final Intent intent = getIntent();
    dictFile = new File(intent.getStringExtra(C.DICT_FILE));
    
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
      Toast.makeText(this, getString(R.string.invalidDictionary, "", e.getMessage()), Toast.LENGTH_LONG);
      startActivity(DictionaryManagerActivity.getLaunchIntent());
      finish();
      return;
    }

    indexIndex = intent.getIntExtra(C.INDEX_INDEX, 0) % dictionary.indices.size();
    Log.d(LOG, "Loading index " + indexIndex);
    index = dictionary.indices.get(indexIndex);
    setListAdapter(new IndexAdapter(index));
    
    // Pre-load the collators.
    searchExecutor.execute(new Runnable() {
      public void run() {
        final long startMillis = System.currentTimeMillis();
        
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
          Log.d(LOG, "Starting collator load for lang=" + index.sortLanguage.getIsoCode());
          
          final com.ibm.icu.text.Collator c = index.sortLanguage.getCollator();          
          if (c.compare("pre-print", "preppy") >= 0) {
            Log.e(LOG, c.getClass()
                + " is buggy, lookups may not work properly.");
          }
        }
        Log.d(LOG, "Loading collators took:"
            + (System.currentTimeMillis() - startMillis));
      }
    });
    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    
    final String fontSize = prefs.getString(getString(R.string.fontSizeKey), "14");
    try {
      fontSizeSp = Integer.parseInt(fontSize.trim());
    } catch (NumberFormatException e) {
      fontSizeSp = 12;
    }

    setContentView(R.layout.dictionary_activity);
    searchText = (EditText) findViewById(R.id.SearchText);
    searchText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
    
    langButton = (Button) findViewById(R.id.LangButton);
    
    searchText.requestFocus();
    searchText.addTextChangedListener(searchTextWatcher);
    final String search = prefs.getString(C.SEARCH_TOKEN, "");
    searchText.setText(search);
    searchText.setSelection(0, search.length());
    Log.d(LOG, "Trying to restore searchText=" + search);
    
    final Button clearSearchTextButton = (Button) findViewById(R.id.ClearSearchTextButton);
    clearSearchTextButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onClearSearchTextButton(clearSearchTextButton);
      }
    });
    clearSearchTextButton.setVisibility(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
        getString(R.string.showClearSearchTextButtonKey), true) ? View.VISIBLE
        : View.GONE);
    
    final Button langButton = (Button) findViewById(R.id.LangButton);
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
    
    final Button upButton = (Button) findViewById(R.id.UpButton);
    upButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onUpDownButton(true);
      }
    });
    final Button downButton = (Button) findViewById(R.id.DownButton);
    downButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onUpDownButton(false);
      }
    });

   getListView().setOnItemSelectedListener(new ListView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View arg1, final int position,
          long id) {
        if (!searchText.isFocused()) {
          if (!isFiltered()) {
            final RowBase row = (RowBase) getListAdapter().getItem(position);
            Log.d(LOG, "onItemSelected: " + row.index());
            final TokenRow tokenRow = row.getTokenRow(true);
            searchText.setText(tokenRow.getToken());
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());

    // Prefs.
    wordList = new File(prefs.getString(getString(R.string.wordListFileKey),
        getString(R.string.wordListFileDefault)));
    saveOnlyFirstSubentry = prefs.getBoolean(getString(R.string.saveOnlyFirstSubentryKey), false);
    clickOpensContextMenu = prefs.getBoolean(getString(R.string.clickOpensContextMenuKey), false);
    //if (prefs.getBoolean(getString(R.string.vibrateOnFailedSearchKey), true)) {
      // vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    //}
    Log.d(LOG, "wordList=" + wordList + ", saveOnlyFirstSubentry=" + saveOnlyFirstSubentry);
    
    setDictionaryPrefs(this, dictFile, indexIndex, searchText.getText().toString());
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    if (PreferenceActivity.prefsMightHaveChanged) {
      PreferenceActivity.prefsMightHaveChanged = false;
      finish();
      startActivity(getIntent());
    }
    if (initialSearchText != null) {
      setSearchText(initialSearchText);
    }
  }
  
  @Override
  protected void onPause() {
    super.onPause();
  }
  
  private static void setDictionaryPrefs(final Context context,
      final File dictFile, final int indexIndex, final String searchToken) {
    final SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
    prefs.putString(C.DICT_FILE, dictFile.getPath());
    prefs.putInt(C.INDEX_INDEX, indexIndex);
    prefs.putString(C.SEARCH_TOKEN, searchToken);
    prefs.commit();
  }

  private static void clearDictionaryPrefs(final Context context) {
    final SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(context).edit();
    prefs.remove(C.DICT_FILE);
    prefs.remove(C.INDEX_INDEX);
    prefs.remove(C.SEARCH_TOKEN);
    prefs.commit();
  }


  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (dictRaf == null) {
      return;
    }
    
    // Before we close the RAF, we have to wind the current search down.
    if (currentSearchOperation != null) {
      Log.d(LOG, "Interrupting search to shut down.");
      final SearchOperation searchOperation = currentSearchOperation;
      currentSearchOperation = null;
      searchOperation.interrupted.set(true);
      synchronized (searchOperation) {
        while (!searchOperation.done) {
          try {
            searchOperation.wait();
          } catch (InterruptedException e) {
            Log.d(LOG, "Interrupted.", e);
          }
        }
      }
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

  private void onClearSearchTextButton(final Button clearSearchTextButton) {
    clearSearchTextButton.requestFocus();
    searchText.setText("");
    searchText.requestFocus();
    Log.d(LOG, "Trying to show soft keyboard.");
    final InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    manager.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
  }
  
  void updateLangButton() {
//    final LanguageResources languageResources = Language.isoCodeToResources.get(index.shortName);
//    if (languageResources != null && languageResources.flagId != 0) {
//      langButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, languageResources.flagId, 0);
//    } else {
//      langButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
      langButton.setText(index.shortName);
//    }
  }

  void onLanguageButton() {
    if (currentSearchOperation != null) {
      currentSearchOperation.interrupted.set(true);
      currentSearchOperation = null;
    }
    changeIndex((indexIndex + 1)% dictionary.indices.size());
  }
  
  void onLanguageButtonLongClick(final Context context) {
    final Dialog dialog = new Dialog(context);
    dialog.setContentView(R.layout.select_dictionary_dialog);
    dialog.setTitle(R.string.selectDictionary);

    final List<DictionaryInfo> installedDicts = ((DictionaryApplication)getApplication()).getUsableDicts();
    ListView listView = (ListView) dialog.findViewById(android.R.id.list);
    listView.setAdapter(new BaseAdapter() {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        final LinearLayout result = new LinearLayout(parent.getContext());
        final DictionaryInfo dictionaryInfo = getItem(position);
          final Button button = new Button(parent.getContext());
          final String name = application.getDictionaryName(dictionaryInfo.uncompressedFilename);
          button.setText(name);
          final IntentLauncher intentLauncher = new IntentLauncher(parent.getContext(), getLaunchIntent(application.getPath(dictionaryInfo.uncompressedFilename), 0, "")) {
            @Override
            protected void onGo() {
              dialog.dismiss();
              DictionaryActivity.this.finish();
            };
          };
          button.setOnClickListener(intentLauncher);
          
          final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          layoutParams.width = 0;
          layoutParams.weight = 1.0f;
          button.setLayoutParams(layoutParams);

          result.addView(button);
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


  private void changeIndex(final int newIndex) {
    indexIndex = newIndex;
    index = dictionary.indices.get(indexIndex);
    indexAdapter = new IndexAdapter(index);
    Log.d(LOG, "changingIndex, newLang=" + index.longName);
    setListAdapter(indexAdapter);
    updateLangButton();
    searchText.requestFocus();  // Otherwise, nothing may happen.
    onSearchTextChange(searchText.getText().toString());
    setDictionaryPrefs(this, dictFile, indexIndex, searchText.getText().toString());
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
    searchText.removeTextChangedListener(searchTextWatcher);
    searchText.setText(dest.token);
    Selection.moveToRightEdge(searchText.getText(), searchText.getLayout());
    jumpToRow(index.sortedIndexEntries.get(destIndexEntry).startRow);
    searchText.addTextChangedListener(searchTextWatcher);
  }

  // --------------------------------------------------------------------------
  // Options Menu
  // --------------------------------------------------------------------------
  
  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    application.onCreateGlobalOptionsMenu(this, menu);

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
            builder.append(getString(R.string.dictionaryPath, dictFile.getPath())).append("\n");
            builder.append(getString(R.string.dictionarySize, dictionaryInfo.uncompressedBytes)).append("\n");
            builder.append(getString(R.string.dictionaryCreationTime, dictionaryInfo.creationMillis)).append("\n");
            for (final IndexInfo indexInfo : dictionaryInfo.indexInfos) {
              builder.append("\n");
              builder.append(getString(R.string.indexName, indexInfo.shortName)).append("\n");
              builder.append(getString(R.string.mainTokenCount, indexInfo.mainTokenCount)).append("\n");
            }
            builder.append("\n");
            builder.append(getString(R.string.sources)).append("\n");
            for (final EntrySource source : dictionary.sources) {
              builder.append(getString(R.string.sourceInfo, source.getName(), source.getNumEntries())).append("\n");
            }
          } else {
            builder.append(getString(R.string.invalidDictionary));
          }
          textView.setText(builder.toString());
          
          dialog.show();
          final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
          layoutParams.width = WindowManager.LayoutParams.FILL_PARENT;
          layoutParams.height = WindowManager.LayoutParams.FILL_PARENT;
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
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
    final RowBase row = (RowBase) getListAdapter().getItem(adapterContextMenuInfo.position);

    final MenuItem addToWordlist = menu.add(getString(R.string.addToWordList, wordList.getName()));
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        onAppendToWordList(row);
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
      final MenuItem searchForSelection = menu.add(getString(R.string.searchForSelection, selectedSpannableText));
      searchForSelection.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
          int indexToUse = -1;
          for (int i = 0; i < dictionary.indices.size(); ++i) {
            final Index index = dictionary.indices.get(i);
            final IndexEntry indexEntry = index.findExact(selectedText); 
            final TokenRow tokenRow = index.rows.get(indexEntry.startRow).getTokenRow(false);
            if (tokenRow != null && tokenRow.hasMainEntry) {
              indexToUse = i;
              break;
            }
          }
          if (indexToUse == -1) {
            indexToUse = selectedSpannableIndex;
          }
          if (indexIndex != indexToUse) {
            changeIndex(indexToUse);
          }
          setSearchText(selectedText);
          return false;
        }
      });
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
    rawText.append(
        new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()))
        .append("\t");
    rawText.append(index.longName).append("\t");
    rawText.append(row.getTokenRow(true).getToken()).append("\t");
    rawText.append(row.getRawText(saveOnlyFirstSubentry));
    Log.d(LOG, "Writing : " + rawText);

    try {
      wordList.getParentFile().mkdirs();
      final PrintWriter out = new PrintWriter(
          new FileWriter(wordList, true));
      out.println(rawText.toString());
      out.close();
    } catch (IOException e) {
      Log.e(LOG, "Unable to append to " + wordList.getAbsolutePath(), e);
      Toast.makeText(this, getString(R.string.failedAddingToWordList, wordList.getAbsolutePath()), Toast.LENGTH_LONG);
    }
    return;
  }
  
  /**
   * Called when user clicks outside of search text, so that they can start
   * typing again immediately.
   */
  void defocusSearchText() {
    //Log.d(LOG, "defocusSearchText");
    // Request focus so that if we start typing again, it clears the text input.
    getListView().requestFocus();
    
    // Visual indication that a new keystroke will clear the search text.
    searchText.selectAll();
  }

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
        setSearchText("" + (char) event.getUnicodeChar());
      }
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      Log.d(LOG, "Clearing dictionary prefs.");
      DictionaryActivity.clearDictionaryPrefs(this);
    }
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      Log.d(LOG, "Trying to hide soft keyboard.");
      final InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      inputManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  private void setSearchText(final String text) {
    searchText.setText(text);
    searchText.requestFocus();
    onSearchTextChange(searchText.getText().toString());
    if (searchText.getLayout() != null) {
      // Surprising, but this can crash when you rotate...
      Selection.moveToRightEdge(searchText.getText(), searchText.getLayout());
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
    setSelection(row);
    getListView().setSelected(true);
  }

  static final Pattern WHITESPACE = Pattern.compile("\\s+");
  final class SearchOperation implements Runnable {
    
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    final String searchText;
    List<String> searchTokens;  // filled in for multiWord.
    final Index index;
    
    long searchStartMillis;

    Index.IndexEntry searchResult;
    List<RowBase> multiWordSearchResult;
    
    boolean done = false;
    
    SearchOperation(final String searchText, final Index index) {
      this.searchText = searchText.trim();
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
          multiWordSearchResult = index.multiWordSearch(searchTokens, interrupted);
        }
        Log.d(LOG, "searchText=" + searchText + ", searchDuration="
            + (System.currentTimeMillis() - searchStartMillis) + ", interrupted="
            + interrupted.get());
        if (!interrupted.get()) {
          uiHandler.post(new Runnable() {
            @Override
            public void run() {            
              searchFinished(SearchOperation.this);
            }
          });
        }
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

  final class IndexAdapter extends BaseAdapter {
    
    final Index index;
    final List<RowBase> rows;
    final Set<String> toHighlight;

    IndexAdapter(final Index index) {
      this.index = index;
      rows = index.rows;
      this.toHighlight = null;
    }

    IndexAdapter(final Index index, final List<RowBase> rows, final List<String> toHighlight) {
      this.index = index;
      this.rows = rows;
      this.toHighlight = new LinkedHashSet<String>(toHighlight);
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
    public View getView(int position, final View convertView, ViewGroup parent) {
      final RowBase row = getItem(position);
      if (row instanceof PairEntry.Row) {
        return getView(position, (PairEntry.Row) row, parent, convertView);
      } else if (row instanceof TokenRow) {
        return getView((TokenRow) row, parent, convertView);
      } else {
        throw new IllegalArgumentException("Unsupported Row type: " + row.getClass());
      }
    }

    private View getView(final int position, PairEntry.Row row, ViewGroup parent, final View convertView) {
      final TableLayout result = new TableLayout(parent.getContext());
      final PairEntry entry = row.getEntry();
      final int rowCount = entry.pairs.size();
      for (int r = 0; r < rowCount; ++r) {
        final TableRow tableRow = new TableRow(result.getContext());

        final TextView col1 = new TextView(tableRow.getContext());
        final TextView col2 = new TextView(tableRow.getContext());
        final TableRow.LayoutParams layoutParams = new TableRow.LayoutParams();
        layoutParams.weight = 0.5f;

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

        // TODO: color words by gender
        final Pair pair = entry.pairs.get(r);
        final String col1Text = index.swapPairEntries ? pair.lang2 : pair.lang1;
        final String col2Text = index.swapPairEntries ? pair.lang1 : pair.lang2;
        
        col1.setText(col1Text, TextView.BufferType.SPANNABLE);
        col2.setText(col2Text, TextView.BufferType.SPANNABLE);
        
        // Bold the token instances in col1.
        final Set<String> toBold = toHighlight != null ? this.toHighlight : Collections.singleton(row.getTokenRow(true).getToken());
        final Spannable col1Spannable = (Spannable) col1.getText();
        for (final String token : toBold) {
          int startPos = 0;
          while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
            col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos,
                startPos + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            startPos += token.length();
          }
        }
        
        createTokenLinkSpans(col1, col1Spannable, col1Text);
        createTokenLinkSpans(col2, (Spannable) col2.getText(), col2Text);
        
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
            DictionaryActivity.this.onListItemClick(null, v, position, position);
          }
        });
        
        result.addView(tableRow);
      }

      return result;

      
//      final WebView result = (WebView) (convertView instanceof WebView ? convertView : new WebView(parent.getContext()));
//        
//      final PairEntry entry = row.getEntry();
//      final int rowCount = entry.pairs.size();
//      final StringBuilder html = new StringBuilder();
//      html.append("<html><body><table width=\"100%\">");
//      for (int r = 0; r < rowCount; ++r) {
//        html.append("<tr>");
//
//        final Pair pair = entry.pairs.get(r);
//        // TODO: escape both the token and the text.
//        final String token = row.getTokenRow(true).getToken();
//        final String col1Text = index.swapPairEntries ? pair.lang2 : pair.lang1;
//        final String col2Text = index.swapPairEntries ? pair.lang1 : pair.lang2;
//        
//        col1Text.replaceAll(token, String.format("<b>%s</b>", token));
//
//        // Column1
//        html.append("<td width=\"50%\">");
//        if (r > 0) {
//          html.append("<li>");
//        }
//        html.append(col1Text);
//        html.append("</td>");
//
//        // Column2
//        html.append("<td width=\"50%\">");
//        if (r > 0) {
//          html.append("<li>");
//        }
//        html.append(col2Text);
//        html.append("</td>");
//
////        column1.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
////        column2.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
//
//        html.append("</tr>");
//      }
//      html.append("</table></body></html>");
//      
//      Log.i(LOG, html.toString());
//      
//      result.getSettings().setRenderPriority(RenderPriority.HIGH);
//      result.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
//      
//      result.loadData("<html><body><table><tr><td>line (connected series of public conveyances, and hence, an established arrangement for forwarding merchandise, etc.) (noun)</td><td>verbinding</td></tr></table></body></html>", "text/html", "utf-8");
//
//      return result;
    }

    private View getView(TokenRow row, ViewGroup parent, final View convertView) {
      final Context context = parent.getContext();
      final TextView textView = new TextView(context);
      textView.setText(row.getToken());
      textView.setBackgroundResource(row.hasMainEntry ? theme.tokenRowMainBg : theme.tokenRowOtherBg);
      // Doesn't work:
      //textView.setTextColor(android.R.color.secondary_text_light);
      textView.setTextAppearance(context, theme.tokenRowFg);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 5 * fontSizeSp / 4);
      return textView;
    }
    
  }

  static final Pattern CHAR_DASH = Pattern.compile("['\\p{L}0-9]+");

  private void createTokenLinkSpans(final TextView textView, final Spannable spannable, final String text) {
    // Saw from the source code that LinkMovementMethod sets the selection!
    // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.1_r1/android/text/method/LinkMovementMethod.java#LinkMovementMethod
    textView.setMovementMethod(LinkMovementMethod.getInstance());
    final Matcher matcher = CHAR_DASH.matcher(text);
    while (matcher.find()) {
      spannable.setSpan(new NonLinkClickableSpan(), matcher.start(), matcher.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
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
      if (start >= 0 &&  end >= 0) {
        selectedSpannableText = textView.getText().subSequence(start, end).toString();
        selectedSpannableIndex = index;
      }
      return false;
    }
  }
  final TextViewLongClickListener textViewLongClickListenerIndex0 = new TextViewLongClickListener(0);
  final TextViewLongClickListener textViewLongClickListenerIndex1 = new TextViewLongClickListener(1);
  

  // --------------------------------------------------------------------------
  // SearchText
  // --------------------------------------------------------------------------

  void onSearchTextChange(final String text) {
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
        Log.d(LOG, "Search text changed with focus: " + searchText.getText());
        // If they were typing to cause the change, update the UI.
        onSearchTextChange(searchText.getText().toString());
      }
    }

    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
        int arg3) {
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
    ((Button) findViewById(R.id.UpButton)).setEnabled(false);
    ((Button) findViewById(R.id.DownButton)).setEnabled(false);
    rowsToShow = searchOperation.multiWordSearchResult;
    setListAdapter(new IndexAdapter(index, rowsToShow, searchOperation.searchTokens));
  }

  void clearFiltered() {
    ((Button) findViewById(R.id.UpButton)).setEnabled(true);
    ((Button) findViewById(R.id.DownButton)).setEnabled(true);
    setListAdapter(new IndexAdapter(index));
    rowsToShow = null;
  }

}
