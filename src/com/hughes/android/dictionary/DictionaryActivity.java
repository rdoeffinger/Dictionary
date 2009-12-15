package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.hughes.android.dictionary.Dictionary.IndexEntry;
import com.hughes.android.dictionary.Dictionary.LanguageData;
import com.hughes.android.dictionary.Dictionary.Row;
import com.ibm.icu.text.Collator;

public class DictionaryActivity extends ListActivity {
  
  // TODO:
  // * Download latest dicts.
  //   * http://ftp.tu-chemnitz.de/pub/Local/urz/ding/de-en-devel/
  //   * http://www1.dict.cc/translation_file_request.php?l=e
  // * Compress all the strings everywhere, put compression table in file.
  // Done:
  // * Only one way to way for current search to end. (won't do).

  static final String LOG = "QuickDic";
  static final String PREF_DICT_ACTIVE_LANG = "DICT_DIR_PREF";
  static final String PREF_ACTIVE_SEARCH_TEXT = "ACTIVE_WORD_PREF";

  // package for test.
  final Handler uiHandler = new Handler();
  private final Executor searchExecutor = Executors.newSingleThreadExecutor();

  EditText searchText;
  Button langButton;
  int lastSelectedRow = 0;  // TODO: I'm evil.

  private boolean prefsMightHaveChanged = true;

  // Never null.
  private File wordList;
  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;
  private boolean saveOnlyFirstSubentry = false;

  // Visible for testing.
  LanguageListAdapter languageList = null;
  private SearchOperation searchOperation = null;
  
  public DictionaryActivity() {

    searchExecutor.execute(new Runnable() {
      public void run() {
        final long startMillis = System.currentTimeMillis();
        for (final String lang : Arrays.asList("EN", "DE")) {
          Language.lookup(lang).getFindCollator(); 
          final Collator c = Language.lookup(lang).getSortCollator(); 
          if (c.compare("pre-print", "preppy") >= 0) {
            Log.e(LOG, c.getClass() + " is buggy, lookups may not work properly.");
          }
        }
        Log.d(LOG, "Loading collators took:" + (System.currentTimeMillis() - startMillis));
      }
    });

  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);

    try {
      initDictionaryAndPrefs();
    } catch (Exception e) {
      return;
    }

    // UI init.

    setContentView(R.layout.main);
    searchText = (EditText) findViewById(R.id.SearchText);
    langButton = (Button) findViewById(R.id.LangButton);
    
    Log.d(LOG, "adding text changed listener");
    searchText.addTextChangedListener(new SearchTextWatcher());
    
    getListView().setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> arg0, View arg1, int row,
          long arg3) {
        setSelectedRow(row);
      }
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });
    
    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int row,
          long arg3) {
        setSelectedRow(row);
        return false;
      }
    });
    
    final Button clearSearchTextButton = (Button) findViewById(R.id.ClearSearchTextButton);
    clearSearchTextButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        clearSearchTextButton.requestFocus();
        searchText.setText("");
        searchText.requestFocus();
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
    
    final Button upButton = (Button) findViewById(R.id.UpButton);
    upButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onUpButton();
      }
    });
    final Button downButton = (Button) findViewById(R.id.DownButton);
    downButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onDownButton();
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());

    updateLangButton();
  }
  
  private void initDictionaryAndPrefs() throws Exception {
    if (!prefsMightHaveChanged) {
      return;
    }
    closeCurrentDictionary();
    
    final SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    wordList = new File(prefs.getString(getString(R.string.wordListFileKey),
        getString(R.string.wordListFileDefault)));
    Log.d(LOG, "wordList=" + wordList);
    
    saveOnlyFirstSubentry = prefs.getBoolean(getString(R.string.saveOnlyFirstSubentryKey), false);

    final File dictFile = new File(prefs.getString(getString(R.string.dictFileKey),
        getString(R.string.dictFileDefault)));
    Log.d(LOG, "dictFile=" + dictFile);
    
    try {
      if (!dictFile.canRead()) {
        throw new IOException("Unable to read dictionary file.");
      }
      
      dictRaf = new RandomAccessFile(dictFile, "r");
      final long startMillis = System.currentTimeMillis();
      dictionary = new Dictionary(dictRaf);
      Log.d(LOG, "Read dictionary millis: " + (System.currentTimeMillis() - startMillis));
    } catch (IOException e) {
      Log.e(LOG, "Couldn't open dictionary.", e);
      this.startActivity(new Intent(this, NoDictionaryActivity.class));
      finish();
      throw new Exception(e);
    }
    
    final byte lang = prefs.getInt(PREF_DICT_ACTIVE_LANG, Entry.LANG1) == Entry.LANG1 ? Entry.LANG1
        : Entry.LANG2;
    
    languageList = new LanguageListAdapter(dictionary.languageDatas[lang]);
    setListAdapter(languageList);
    prefsMightHaveChanged = false;
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(LOG, "onResume:" + this);

    try {
      initDictionaryAndPrefs();
    } catch (Exception e) {
      return;
    }
    
    final SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    final String searchTextString = prefs
        .getString(PREF_ACTIVE_SEARCH_TEXT, "");
    searchText.setText(searchTextString);
    getListView().requestFocus();
    onSearchTextChange(searchTextString);
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(LOG, "onPause:" + this);
    final Editor prefs = PreferenceManager.getDefaultSharedPreferences(this)
        .edit();
    prefs.putInt(PREF_DICT_ACTIVE_LANG, languageList.languageData.lang);
    prefs.putString(PREF_ACTIVE_SEARCH_TEXT, searchText.getText().toString());
    prefs.commit();
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.d(LOG, "onStop:" + this);
    if (isFinishing()) {
      Log.i(LOG, "isFinishing()==true, closing dictionary.");
      closeCurrentDictionary();
    }
  }

  private void closeCurrentDictionary() {
    Log.i(LOG, "closeCurrentDictionary");
    if (dictionary == null) {
      return;
    }
    waitForSearchEnd();
    languageList = null;
    setListAdapter(null);
    Log.d(LOG, "setListAdapter finished.");
    dictionary = null;
    try {
      if (dictRaf != null) {
        dictRaf.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    dictRaf = null;
  }

  public String getSelectedRowRawText(final boolean onlyFirstSubentry) {
    final Row row = languageList.languageData.rows.get(getSelectedRow());
    return languageList.languageData.rowToString(row, onlyFirstSubentry);
  }

  // ----------------------------------------------------------------
  // OptionsMenu
  // ----------------------------------------------------------------

  private MenuItem switchLanguageMenuItem = null;

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    switchLanguageMenuItem = menu.add(getString(R.string.switchToLanguage));
    switchLanguageMenuItem
        .setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            onLanguageButton();
            return false;
          }
        });

    final MenuItem preferences = menu.add(getString(R.string.preferences));
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        prefsMightHaveChanged = true;
        startActivity(new Intent(DictionaryActivity.this,
            PreferenceActivity.class));
        return false;
      }
    });

    final MenuItem about = menu.add(getString(R.string.about));
    about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class
            .getPackage().getName(), AboutActivity.class.getCanonicalName());
        final String currentDictInfo;
        if (dictionary == null) {
          currentDictInfo = getString(R.string.noDictLoaded);
        } else {
          final LanguageData lang0 = dictionary.languageDatas[0];
          final LanguageData lang1 = dictionary.languageDatas[1];
          currentDictInfo = getString(R.string.aboutText, dictionary.dictionaryInfo, dictionary.entries.size(), 
              lang0.language.symbol, lang0.sortedIndex.size(), lang0.rows.size(),
              lang1.language.symbol, lang1.sortedIndex.size(), lang1.rows.size());
        }
        intent.putExtra(AboutActivity.CURRENT_DICT_INFO, currentDictInfo
            .toString());
        startActivity(intent);
        return false;
      }
    });

    final MenuItem download = menu.add(getString(R.string.downloadDictionary));
    download.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        prefsMightHaveChanged = true;
        startDownloadDictActivity(DictionaryActivity.this);
        return false;
      }
    });

    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    switchLanguageMenuItem.setTitle(getString(R.string.switchToLanguage,
        dictionary.languageDatas[Entry
            .otherLang(languageList.languageData.lang)].language.symbol));
    return super.onPrepareOptionsMenu(menu);
  }
  
  void updateLangButton() {
    langButton.setText(languageList.languageData.language.symbol);
  }

  // ----------------------------------------------------------------
  // Event handlers.
  // ----------------------------------------------------------------
  
  void onLanguageButton() {
    waitForSearchEnd();
    languageList = new LanguageListAdapter(
        dictionary.languageDatas[(languageList.languageData == dictionary.languageDatas[0]) ? 1
            : 0]);
    Log.d(LOG, "onLanguageButton, newLang=" + languageList.languageData.language.symbol);
    setListAdapter(languageList);
    updateLangButton();
    onSearchTextChange(searchText.getText().toString());
  }

  void onUpButton() {
    final int destRowIndex = languageList.languageData.getPrevTokenRow(getSelectedRow());
    Log.d(LOG, "onUpButton, destRowIndex=" + destRowIndex);
    jumpToRow(languageList, destRowIndex);
  }

  void onDownButton() {
    final int destRowIndex = languageList.languageData.getNextTokenRow(getSelectedRow());
    Log.d(LOG, "onDownButton, destRowIndex=" + destRowIndex);
    jumpToRow(languageList, destRowIndex);
  }

  void onAppendToWordList() {
    final int row = getSelectedRow();
    if (row < 0) {
      return;
    }
    final StringBuilder rawText = new StringBuilder();
    final String word = languageList.languageData.getIndexEntryForRow(row).word;
    rawText.append(
        new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()))
        .append("\t");
    rawText.append(word).append("\t");
    rawText.append(getSelectedRowRawText(saveOnlyFirstSubentry));
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

  void onCopy() {
    final int row = getSelectedRow();
    if (row < 0) {
      return;
    }
    Log.d(LOG, "Copy." + DictionaryActivity.this.getSelectedRow());
    final StringBuilder result = new StringBuilder();
    result.append(getSelectedRowRawText(false));
    final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    clipboardManager.setText(result.toString());
    Log.d(LOG, "Copied: " + result);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (event.getUnicodeChar() != 0) {
      if (!searchText.hasFocus()) {
        searchText.setText("" + (char) event.getUnicodeChar());
        onSearchTextChange(searchText.getText().toString());
        searchText.requestFocus();
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int row, long id) {
    setSelectedRow(row);
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d(LOG, "onSearchTextChange: " + searchText);
    synchronized (this) {
      searchOperation = new SearchOperation(languageList, searchText, searchOperation);
      searchExecutor.execute(searchOperation);
    }
  }

  

  // ----------------------------------------------------------------
  // ContextMenu
  // ----------------------------------------------------------------

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    final int row = getSelectedRow();
    if (row < 0) {
      return;
    }

    final MenuItem addToWordlist = menu.add(getString(R.string.addToWordList, wordList.getName()));
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        onAppendToWordList();
        return false;
      }
    });

    final MenuItem copy = menu.add(android.R.string.copy);
    copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        onCopy();
        return false;
      }
    });

  }

  private void jumpToRow(final LanguageListAdapter dictionaryListAdapter,
      final int rowIndex) {
    Log.d(LOG, "jumpToRow: " + rowIndex);
    if (dictionaryListAdapter != this.languageList) {
      Log.w(LOG, "skipping jumpToRow for old list adapter: " + rowIndex);
      return;
    }
    setSelection(rowIndex);
    setSelectedRow(rowIndex);
    getListView().setSelected(true);
  }
  
  // TODO: delete me somehow.
  private int getSelectedRow() {
    return lastSelectedRow;
  }
  private void setSelectedRow(final int row) {
    lastSelectedRow = row;
    Log.d(LOG, "Selected: " + getSelectedRowRawText(true));
    updateSearchText();
  }

  private void updateSearchText() {
    Log.d(LOG, "updateSearchText");
    final int selectedRowIndex = getSelectedRow();
    if (!searchText.hasFocus()) {
      if (selectedRowIndex >= 0) {
        final String word = languageList.languageData
            .getIndexEntryForRow(selectedRowIndex).word;
        if (!word.equals(searchText.getText().toString())) {
          Log.d(LOG, "updateSearchText: setText: " + word);
          searchText.setText(word);
        }
      } else {
        Log.w(LOG, "updateSearchText: nothing selected.");
      }
    }
  }

  static void startDownloadDictActivity(final Context context) {
    final Intent intent = new Intent(context, DownloadActivity.class);
    final SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(context);
    final String dictFetchUrl = prefs.getString(context
        .getString(R.string.dictFetchUrlKey), context
        .getString(R.string.dictFetchUrlDefault));
    final String dictFileName = prefs.getString(context
        .getString(R.string.dictFileKey), context
        .getString(R.string.dictFileDefault));
    intent.putExtra(DownloadActivity.SOURCE, dictFetchUrl);
    intent.putExtra(DownloadActivity.DEST, dictFileName);
    context.startActivity(intent);
  }

  class LanguageListAdapter extends BaseAdapter {

    // Visible for testing.
    final LanguageData languageData;

    LanguageListAdapter(final LanguageData languageData) {
      this.languageData = languageData;
    }

    public int getCount() {
      return languageData.rows.size();
    }

    public Dictionary.Row getItem(int rowIndex) {
      assert rowIndex < languageData.rows.size();
      return languageData.rows.get(rowIndex);
    }

    public long getItemId(int rowIndex) {
      return rowIndex;
    }

    public View getView(final int rowIndex, final View convertView,
        final ViewGroup parent) {
      final Row row = getItem(rowIndex);

      // Token row.
      if (row.isToken()) {
        TextView result = null;
        if (convertView instanceof TextView) {
          result = (TextView) convertView;
        } else {
          result = new TextView(parent.getContext());
        }
        if (row == null) {
          return result;
        }
        result.setText(languageData.rowToString(row, false));
        result.setTextAppearance(parent.getContext(),
            android.R.style.TextAppearance_Large);
        result.setClickable(false);
        return result;
      }

      // Entry row(s).
      final TableLayout result = new TableLayout(parent.getContext());

      final Entry entry = dictionary.entries.get(row.getIndex());
      final int rowCount = entry.getRowCount();
      for (int r = 0; r < rowCount; ++r) {
        final TableRow tableRow = new TableRow(result.getContext());

        TextView column1 = new TextView(tableRow.getContext());
        TextView column2 = new TextView(tableRow.getContext());
        final TableRow.LayoutParams layoutParams = new TableRow.LayoutParams();
        layoutParams.weight = 0.5f;

        if (r > 0) {
          final TextView spacer = new TextView(tableRow.getContext());
          spacer.setText(r == 0 ? "• " : " • ");
          tableRow.addView(spacer);
        }
        tableRow.addView(column1, layoutParams);
        if (r > 0) {
          final TextView spacer = new TextView(tableRow.getContext());
          spacer.setText(r == 0 ? "• " : " • ");
          tableRow.addView(spacer);
        }
        tableRow.addView(column2, layoutParams);

        column1.setWidth(1);
        column2.setWidth(1);
        // column1.setTextAppearance(parent.getContext(), android.R.style.Text);

        // TODO: color words by gender
        final String col1Text = entry.getAllText(languageData.lang)[r];
        column1.setText(col1Text, TextView.BufferType.SPANNABLE);
        final Spannable col1Spannable = (Spannable) column1.getText();
        int startPos = 0;
        final String token = languageData.getIndexEntryForRow(rowIndex).word;
        while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
          col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos,
              startPos + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
          startPos += token.length();
        }

        column2.setText(
            entry.getAllText(Entry.otherLang(languageData.lang))[r],
            TextView.BufferType.NORMAL);

        result.addView(tableRow);
      }

      return result;
    }

  } // DictionaryListAdapter

  private final class SearchOperation implements Runnable {
    SearchOperation previousSearchOperation;
    
    final LanguageListAdapter listAdapter;
    final LanguageData languageData;
    final String searchText;
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    boolean searchFinished = false;

    SearchOperation(final LanguageListAdapter listAdapter,
        final String searchText, final SearchOperation previousSearchOperation) {
      this.listAdapter = listAdapter;
      this.languageData = listAdapter.languageData;
      this.searchText = searchText;
      this.previousSearchOperation = previousSearchOperation;
    }

    public void run() {
      if (previousSearchOperation != null) {
        previousSearchOperation.stopAndWait();
      }
      previousSearchOperation = null;
      
      Log.d(LOG, "SearchOperation: " + searchText);
      final int indexLocation = languageData.lookup(searchText, interrupted);
      if (!interrupted.get()) {
        final IndexEntry indexEntry = languageData.sortedIndex.get(indexLocation);
        
        Log.d(LOG, "SearchOperation completed: " + indexEntry.toString());
        uiHandler.post(new Runnable() {
          public void run() {
            // Check is just a performance operation.
            if (!interrupted.get()) {
              // This is safe, because it checks that the listAdapter hasn't changed.
              jumpToRow(listAdapter, indexEntry.startRow);
            }
            synchronized (DictionaryActivity.this) {
              searchOperation = null;
              DictionaryActivity.this.notifyAll();
            }
          }
        });
      }      
      synchronized (this) {
        searchFinished = true;
        this.notifyAll();
      }
    }
    
    private void stopAndWait() {
      interrupted.set(true);
      synchronized (this) {
        while (!searchFinished) {
          Log.d(LOG, "stopAndWait: " + searchText);
          try {
            this.wait();
          } catch (InterruptedException e) {
            Log.e(LOG, "Interrupted", e);
          }
        }
      }
    }
  }  // SearchOperation

  void waitForSearchEnd() {
    synchronized (this) {
      while (searchOperation != null) {
        Log.d(LOG, "waitForSearchEnd");
        try {
          this.wait();
        } catch (InterruptedException e) {
          Log.e(LOG, "Interrupted.", e);
        }
      }
    }
  }

  private class SearchTextWatcher implements TextWatcher {
    public void afterTextChanged(final Editable searchTextEditable) {
      Log.d(LOG, "Search text changed: " + searchText.getText().toString());
      if (searchText.hasFocus()) {
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

}