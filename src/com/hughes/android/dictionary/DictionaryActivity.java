package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.AlertDialog;
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
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.hughes.android.dictionary.Dictionary.IndexEntry;
import com.hughes.android.dictionary.Dictionary.LanguageData;
import com.hughes.android.dictionary.Dictionary.Row;

public class DictionaryActivity extends ListActivity {
  
  // TODO:
  // * Only have one live SearchActivity, and a way to wait for it to die.
  // * Don't destroy dict unless we're really shutting down (not on screen rotate).
  // * Move (re-)init code to a method, set a flag if prefs might have changed, invoke re-init in onResume, which clears flag and reloads prefs.
  // * Compress all the strings everywhere, put compression table in file.

  static final String LOG = "QuickDic";
  static final String PREF_DICT_ACTIVE_LANG = "DICT_DIR_PREF";
  static final String PREF_ACTIVE_SEARCH_TEXT = "ACTIVE_WORD_PREF";

  // package for test.
  final Handler uiHandler = new Handler();

  EditText searchText;

  private final Executor searchExecutor = Executors.newSingleThreadExecutor();

  // Never null.
  private boolean prefsMightHaveChanged = true;
  private File wordList;

  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;

  // Visible for testing.
  LanguageListAdapter languageList = null;

  private SearchOperation searchOperation = null;

  private int selectedRowIndex;
  private int selectedTokenRowIndex;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate");

    if (Language.EN.sortCollator.compare("pre-print", "preppy") >= 0) {
      Log
          .e(LOG,
              "Android java.text.Collator is buggy, lookups may not work properly.");
    }

    initDictionaryAndPrefs();
    if (dictRaf == null) {
      return;
    }

    // UI init.

    setContentView(R.layout.main);
    searchText = (EditText) findViewById(R.id.SearchText);

    Log.d(LOG, "adding text changed listener");
    searchText.addTextChangedListener(new SearchTextWatcher());

    // Language button.
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

  private void initDictionaryAndPrefs() {
    if (!prefsMightHaveChanged) {
      return;
    }
    closeCurrentDictionary();
    
    final SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    wordList = new File(prefs.getString(getString(R.string.wordListFileKey),
        getString(R.string.wordListFileDefault)));
    Log.d(LOG, "wordList=" + wordList);

    final File dictFile = new File(prefs.getString(getString(R.string.dictFileKey),
        getString(R.string.dictFileDefault)));
    Log.d(LOG, "dictFile=" + dictFile);
    if (!dictFile.canRead()) {
      Log.w(LOG, "Unable to read dictionary file.");
      this.startActivity(new Intent(this, NoDictionaryActivity.class));
      finish();
    }

    try {
      dictRaf = new RandomAccessFile(dictFile, "r");
      dictionary = new Dictionary(dictRaf);
    } catch (Exception e) {
      throw new RuntimeException(e);
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
    
    if (prefsMightHaveChanged) {
      
    }
    
    final SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    final String searchTextString = prefs
        .getString(PREF_ACTIVE_SEARCH_TEXT, "");
    searchText.setText(searchTextString);
    onSearchTextChange(searchTextString);
  }

  @Override
  public void onPause() {
    super.onPause();
    final Editor prefs = PreferenceManager.getDefaultSharedPreferences(this)
        .edit();
    prefs.putInt(PREF_DICT_ACTIVE_LANG, languageList.languageData.lang);
    prefs.putString(PREF_ACTIVE_SEARCH_TEXT, searchText.getText().toString());
    prefs.commit();
  }

  @Override
  public void onStop() {
    super.onStop();
    if (isFinishing()) {
      closeCurrentDictionary();
    }
  }

  private void closeCurrentDictionary() {
    searchOperation.stopAndWait();
    languageList = null;
    setListAdapter(null);
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

  public String getSelectedRowRawText() {
    final int row = getSelectedItemPosition();
    return row < 0 ? "" : languageList.languageData
        .rowToString(languageList.languageData.rows.get(row));
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
        final StringBuilder currentDictInfo = new StringBuilder();
        if (dictionary == null) {
          currentDictInfo.append(getString(R.string.noDictLoaded));
        } else {
          currentDictInfo.append(dictionary.dictionaryInfo).append("\n\n");
          currentDictInfo.append("Entry count: " + dictionary.entries.size())
              .append("\n");
          for (int i = 0; i < 2; ++i) {
            final LanguageData languageData = dictionary.languageDatas[i];
            currentDictInfo.append(languageData.language.symbol).append(":\n");
            currentDictInfo.append(
                "  Unique token count: " + languageData.sortedIndex.size())
                .append("\n");
            currentDictInfo.append("  Row count: " + languageData.rows.size())
                .append("\n");
          }
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
        startDownloadDictActivity(DictionaryActivity.this);
        return false;
      }
    });

    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    switchLanguageMenuItem.setTitle(String.format(
        getString(R.string.switchToLanguage), dictionary.languageDatas[Entry
            .otherLang(languageList.languageData.lang)].language.symbol));
    return super.onPrepareOptionsMenu(menu);
  }
  
  void updateLangButton() {
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setText(languageList.languageData.language.symbol);
  }

  // ----------------------------------------------------------------
  // Event handlers.
  // ----------------------------------------------------------------
  
  void onLanguageButton() {
    languageList = new LanguageListAdapter(
        dictionary.languageDatas[(languageList.languageData == dictionary.languageDatas[0]) ? 1
            : 0]);
    setListAdapter(languageList);
    updateLangButton();
    onSearchTextChange(searchText.getText().toString());
  }

  void onUpButton() {
    final int destRowIndex;
    final Row tokenRow = languageList.languageData.rows
        .get(selectedTokenRowIndex);
    assert tokenRow.isToken();
    final int prevTokenIndex = tokenRow.getIndex() - 1;
    if (selectedRowIndex == selectedTokenRowIndex && selectedRowIndex > 0) {
      destRowIndex = languageList.languageData.sortedIndex
          .get(prevTokenIndex).startRow;
    } else {
      destRowIndex = selectedTokenRowIndex;
    }
    jumpToRow(languageList, destRowIndex);
  }

  void onDownButton() {
    final Row tokenRow = languageList.languageData.rows
        .get(selectedTokenRowIndex);
    assert tokenRow.isToken();
    final int nextTokenIndex = tokenRow.getIndex() + 1;
    final int destRowIndex;
    if (nextTokenIndex < languageList.languageData.sortedIndex.size()) {
      destRowIndex = languageList.languageData.sortedIndex
          .get(nextTokenIndex).startRow;
    } else {
      destRowIndex = languageList.languageData.rows.size() - 1;
    }
    jumpToRow(languageList, destRowIndex);
  }

  void onAppendToWordList() {
    final int row = getSelectedItemPosition();
    if (row < 0) {
      return;
    }
    final StringBuilder rawText = new StringBuilder();
    final String word = languageList.languageData.getIndexEntryForRow(row).word;
    rawText.append(
        new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()))
        .append("\t");
    rawText.append(word).append("\t");
    rawText.append(getSelectedRowRawText());
    Log.d(LOG, "Writing : " + rawText);
    try {
      wordList.getParentFile().mkdirs();
      final PrintWriter out = new PrintWriter(
          new FileWriter(wordList, true));
      out.println(rawText.toString());
      out.close();
    } catch (IOException e) {
      Log.e(LOG, "Unable to append to " + wordList.getAbsolutePath(), e);
      final AlertDialog alert = new AlertDialog.Builder(
          DictionaryActivity.this).create();
      alert.setMessage("Failed to append to file: "
          + wordList.getAbsolutePath());
      alert.show();
    }
    return;
  }

  void onCopy() {
    final int row = getSelectedItemPosition();
    if (row < 0) {
      return;
    }
    Log.d(LOG, "Copy." + DictionaryActivity.this.getSelectedItemPosition());
    final StringBuilder result = new StringBuilder();
    result.append(getSelectedRowRawText());
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
    Log.d(LOG, "Clicked: " + getSelectedRowRawText());
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d(LOG, "onSearchTextChange: " + searchText);
    searchOperation = new SearchOperation(languageList, searchText, searchOperation);
    searchExecutor.execute(searchOperation);
  }

  // ----------------------------------------------------------------
  // ContextMenu
  // ----------------------------------------------------------------

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    final int row = getSelectedItemPosition();
    if (row < 0) {
      return;
    }

    final MenuItem addToWordlist = menu.add(String.format(
        getString(R.string.addToWordList), wordList.getName()));
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
    // selectedTokenRowIndex =
    // languageList.languageData.getIndexEntryForRow(rowIndex).startRow;
    setSelection(rowIndex);
    getListView().setSelected(true); // TODO: is this doing anything?
    updateSearchText();
  }

  private void updateSearchText() {
    Log.d(LOG, "updateSearchText");
    if (!searchText.hasFocus()) {
      final String word = languageList.languageData
          .getIndexEntryForRow(selectedRowIndex).word;
      if (!word.equals(searchText.getText().toString())) {
        Log.d(LOG, "updateSearchText: setText: " + word);
        searchText.setText(word);
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
        result.setText(languageData.rowToString(row));
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
    boolean finished = false;

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
      if (interrupted.get()) {
        return;
      }
      final IndexEntry indexEntry = languageData.sortedIndex.get(indexLocation);
      
      Log.d(LOG, "SearchOperation completed: " + indexEntry.toString());
      uiHandler.post(new Runnable() {
        public void run() {
          // Check is just a performance operation.
          if (!interrupted.get()) {
            // This is safe, because it checks that the listAdapter hasn't changed.
            jumpToRow(listAdapter, indexEntry.startRow);
          }
        }
      });
      
      synchronized (this) {
        finished = true;
        this.notifyAll();
      }
    }
    
    public void stopAndWait() {
      interrupted.set(true);
      synchronized (this) {
        while (!finished) {
          Log.d(LOG, "stopAndWait: " + searchText);
          try {
            this.wait();
          } catch (InterruptedException e) {
            Log.e(LOG, "Interrupted", e);
          }
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