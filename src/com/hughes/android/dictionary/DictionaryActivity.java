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
import android.content.DialogInterface;
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
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import com.hughes.android.dictionary.Dictionary.IndexEntry;
import com.hughes.android.dictionary.Dictionary.LanguageData;
import com.hughes.android.dictionary.Dictionary.Row;

public class DictionaryActivity extends ListActivity {

  static final Intent preferencesIntent = new Intent().setClassName(PreferenceActivity.class.getPackage().getName(), PreferenceActivity.class.getCanonicalName());
  
  static final String LOG = "QuickDic";
  static final String PREF_DICT_ACTIVE_LANG = "DICT_DIR_PREF";
  static final String PREF_ACTIVE_SEARCH_TEXT = "ACTIVE_WORD_PREF";

  private final Handler uiHandler = new Handler();
  private final Executor searchExecutor = Executors.newSingleThreadExecutor();
  private final DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();

  // Never null.
  private File wordList;

  // Can be null.
  private File dictFile = null;
  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;
  private LanguageData activeLanguageData = null;

  private SearchOperation searchOperation = null;
  private int selectedRowIndex = -1;
  private int selectedTokenRowIndex = -1;
  

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate");
    
    if (Language.EN.sortCollator.compare("preppy", "pre-print") >= 0) {
      Log.e(LOG, "Android java.text.Collator is buggy, lookups may not work properly.");
    }
    
    setContentView(R.layout.main);

    getSearchText().addTextChangedListener(new SearchTextWatcher());

    setListAdapter(dictionaryListAdapter);

    // Language button.
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        switchLanguage();
      }});

    final Button upButton = (Button) findViewById(R.id.UpButton);
    upButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        if (dictionary == null) {
          return;
        }
        final int destRowIndex;
        final Row tokenRow = activeLanguageData.rows.get(selectedTokenRowIndex);
        assert tokenRow.isToken();
        final int prevTokenIndex = tokenRow.getIndex() - 1;
        if (selectedRowIndex == selectedTokenRowIndex && selectedRowIndex > 0) {
          destRowIndex = activeLanguageData.sortedIndex.get(prevTokenIndex).startRow;
        } else {
          destRowIndex = selectedTokenRowIndex;
        }
        jumpToRow(destRowIndex);
      }});
    
    final Button downButton = (Button) findViewById(R.id.DownButton);
    downButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        if (dictionary == null) {
          return;
        }
        final Row tokenRow = activeLanguageData.rows.get(selectedTokenRowIndex);
        assert tokenRow.isToken();
        final int nextTokenIndex = tokenRow.getIndex() + 1;
        final int destRowIndex;
        if (nextTokenIndex < activeLanguageData.sortedIndex.size()) {
          destRowIndex = activeLanguageData.sortedIndex.get(nextTokenIndex).startRow;
        } else {
          destRowIndex = activeLanguageData.rows.size() - 1;
        }
        jumpToRow(destRowIndex);
      }});

    // ContextMenu.
    registerForContextMenu(getListView());

    // ItemSelectedListener.
    getListView().setOnItemSelectedListener(new OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> arg0, View arg1, int rowIndex,
          long arg3) {
        if (activeLanguageData == null) {
          return;
        }
        Log.d(LOG, "onItemSelected: " + rowIndex);        
        selectedRowIndex = rowIndex;
        selectedTokenRowIndex = activeLanguageData.getIndexEntryForRow(rowIndex).startRow;
        updateSearchText();
      }

      public void onNothingSelected(AdapterView<?> arg0) {
      }});
    

    // LongClickListener.
    getListView().setOnItemLongClickListener((new OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int rowIndex,
          long arg3) {
        selectedRowIndex = rowIndex;
        return false;
      }
    }));

  }
  
  @Override
  public void onResume() {
    super.onResume();

    // Have to close, because we might have downloaded a new copy of the dictionary.
    closeCurrentDictionary();

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    wordList = new File(prefs.getString(getString(R.string.wordListFileKey), getString(R.string.wordListFileDefault)));
    final File newDictFile = new File(prefs.getString(getString(R.string.dictFileKey), getString(R.string.dictFileDefault)));
    dictFile = newDictFile;
    Log.d(LOG, "wordList=" + wordList);
    Log.d(LOG, "dictFile=" + dictFile);

    if (!dictFile.canRead()) {
      dictionaryListAdapter.notifyDataSetChanged();
      Log.d(LOG, "Unable to read dictionary file.");
      final AlertDialog alert = new AlertDialog.Builder(DictionaryActivity.this).create();
      alert.setMessage(String.format(getString(R.string.unableToReadDictionaryFile), dictFile.getAbsolutePath()));
      alert.setButton(getString(R.string.downloadDictionary), new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          startDownloadDictActivity();
        }});
      alert.show();
      return;
    }
    
    final byte lang = prefs.getInt(PREF_DICT_ACTIVE_LANG, Entry.LANG1) == Entry.LANG1 ? Entry.LANG1 : Entry.LANG2;
      
    try {
      dictRaf = new RandomAccessFile(dictFile, "r");
      dictionary = new Dictionary(dictRaf);
      activeLanguageData = dictionary.languageDatas[lang];
      dictionaryListAdapter.notifyDataSetChanged();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    updateLangButton();

    final String searchText = prefs.getString(PREF_ACTIVE_SEARCH_TEXT, "");
    getSearchText().setText(searchText);
    onSearchTextChange(searchText);
  }
  
  @Override
  public void onPause() {
    super.onPause();
    if (activeLanguageData != null) {
      final Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
      prefs.putInt(PREF_DICT_ACTIVE_LANG, activeLanguageData.lang);
      prefs.putString(PREF_ACTIVE_SEARCH_TEXT, getSearchText().getText().toString());
      prefs.commit();
    }
  }
  
  
  @Override
  public void onStop() {
    super.onStop();
    closeCurrentDictionary();
  }

  private void closeCurrentDictionary() {
    dictionary = null;
    activeLanguageData = null;
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
    return activeLanguageData.rowToString(activeLanguageData.rows.get(selectedRowIndex));
  }
  
  public EditText getSearchText() {
    return (EditText) findViewById(R.id.SearchText);
  }
  
  // ----------------------------------------------------------------
  // OptionsMenu
  // ----------------------------------------------------------------

  private MenuItem switchLanguageMenuItem = null;
  

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    switchLanguageMenuItem = menu.add(getString(R.string.switchToLanguage));
    switchLanguageMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        switchLanguage();
        return false;
      }});

    final MenuItem preferences = menu.add(getString(R.string.preferences));
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        startActivity(preferencesIntent);
        return false;
      }});

    final MenuItem about = menu.add(getString(R.string.about));
    about.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class.getPackage().getName(), AboutActivity.class.getCanonicalName());
        final StringBuilder currentDictInfo = new StringBuilder();
        if (dictionary == null) {
          currentDictInfo.append(getString(R.string.noDictLoaded));
        } else {
          currentDictInfo.append(dictionary.dictionaryInfo).append("\n\n");
          currentDictInfo.append("Entry count: " + dictionary.entries.size()).append("\n");
          for (int i = 0; i < 2; ++i) {
            final LanguageData languageData = dictionary.languageDatas[i]; 
            currentDictInfo.append(languageData.language.symbol).append(":\n");
            currentDictInfo.append("  Unique token count: " + languageData.sortedIndex.size()).append("\n");
            currentDictInfo.append("  Row count: " + languageData.rows.size()).append("\n");
          }
        }
        intent.putExtra(AboutActivity.CURRENT_DICT_INFO, currentDictInfo.toString());
        startActivity(intent);
        return false;
      }});

    final MenuItem download = menu.add(getString(R.string.downloadDictionary));
    download.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        startDownloadDictActivity();
        return false;
      }});

    return true;
  }
  
  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    if (dictionary != null) {
      switchLanguageMenuItem.setTitle(String.format(
          getString(R.string.switchToLanguage), dictionary.languageDatas[Entry
              .otherLang(activeLanguageData.lang)].language.symbol));
    }
    switchLanguageMenuItem.setEnabled(dictionary != null);
    return super.onPrepareOptionsMenu(menu);
  }

  void switchLanguage() {
    if (dictionary == null) {
      return;
    }
    activeLanguageData = dictionary.languageDatas[(activeLanguageData == dictionary.languageDatas[0]) ? 1 : 0];
    selectedRowIndex = 0;
    selectedTokenRowIndex = 0;
    updateLangButton();
    dictionaryListAdapter.notifyDataSetChanged();
    onSearchTextChange(getSearchText().getText().toString());
  }
  
  void updateLangButton() {
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setText(activeLanguageData.language.symbol);
  }
  
  // ----------------------------------------------------------------
  // ContextMenu
  // ----------------------------------------------------------------
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    if (selectedRowIndex == -1) {
      return;
    }
    
    final MenuItem addToWordlist = menu.add(String.format(getString(R.string.addToWordList), wordList.getName()));
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        final StringBuilder rawText = new StringBuilder();
        final String word = activeLanguageData.getIndexEntryForRow(selectedRowIndex).word;
        rawText.append(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date())).append("\t");
        rawText.append(word).append("\t");
        rawText.append(getSelectedRowRawText());
        Log.d(LOG, "Writing : " + rawText);
        try {
          wordList.getParentFile().mkdirs();
          final PrintWriter out = new PrintWriter(new FileWriter(wordList, true));
          out.println(rawText.toString());
          out.close();
        } catch (IOException e) {
          Log.e(LOG, "Unable to append to " + wordList.getAbsolutePath(), e);
          final AlertDialog alert = new AlertDialog.Builder(DictionaryActivity.this).create();
          alert.setMessage("Failed to append to file: " + wordList.getAbsolutePath());
          alert.show();
        }
        return false;
      }
    });

    final MenuItem copy = menu.add(android.R.string.copy);
    copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        Log.d(LOG, "Copy.");
        final StringBuilder result = new StringBuilder();
        result.append(getSelectedRowRawText());
        final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setText(result.toString());
        return false;
      }
    });

  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (event.getUnicodeChar() != 0) {
      final EditText searchText = getSearchText();
      if (!searchText.hasFocus()) {
        searchText.setText("" + (char)event.getUnicodeChar());
        onSearchTextChange(searchText.getText().toString());
        searchText.requestFocus();
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int row, long id) {
    selectedRowIndex = row;
    Log.d(LOG, "Clicked: " + getSelectedRowRawText());
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d(LOG, "onSearchTextChange: " + searchText);
    if (dictionary == null) {
      return;
    }
    if (searchOperation != null) {
      searchOperation.interrupted.set(true);
    }
    searchOperation = new SearchOperation(searchText);
    searchExecutor.execute(searchOperation);
  }
  
  private void jumpToRow(final int rowIndex) {
    Log.d(LOG, "jumpToRow: " + rowIndex);
    selectedRowIndex = rowIndex;
    selectedTokenRowIndex = activeLanguageData.getIndexEntryForRow(rowIndex).startRow;
    getListView().setSelection(rowIndex);
    getListView().setSelected(true);  // TODO: is this doing anything?
    updateSearchText();
  }

  private void updateSearchText() {
    final EditText searchText = getSearchText();
    if (!searchText.hasFocus()) {
      final String word = activeLanguageData.getIndexEntryForRow(selectedRowIndex).word;
      if (!word.equals(searchText.getText().toString())) {
        Log.d(LOG, "updateSearchText: setText: " + word);
        searchText.setText(word);
      }
    }
  }

  private void startDownloadDictActivity() {
    final Intent intent = new Intent().setClassName(
        DownloadActivity.class.getPackage().getName(),
        DownloadActivity.class.getCanonicalName());
    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(DictionaryActivity.this);
    final String dictFetchUrl = settings.getString(getString(R.string.dictFetchUrlKey), getString(R.string.dictFetchUrlDefault));
    final String dictFileName = settings.getString(getString(R.string.dictFileKey), getString(R.string.dictFileDefault));
    intent.putExtra(DownloadActivity.SOURCE, dictFetchUrl);
    intent.putExtra(DownloadActivity.DEST, dictFileName);
    startActivity(intent);
  }

  private final class SearchOperation implements Runnable {
    final String searchText;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    public SearchOperation(final String searchText) {
      this.searchText = searchText;
    }

    public void run() {
      Log.d(LOG, "SearchOperation: " + searchText);
      final int indexLocation = activeLanguageData.lookup(searchText, interrupted);
      if (interrupted.get()) {
        return;
      }
      final IndexEntry indexEntry = activeLanguageData.sortedIndex
          .get(indexLocation);
      Log.d(LOG, "SearchOperation completed: " + indexEntry.toString());
      uiHandler.post(new Runnable() {
        public void run() {
          jumpToRow(indexEntry.startRow);
        }
      });
    }
  }

  private class DictionaryListAdapter extends BaseAdapter {

    public int getCount() {
      if (dictionary == null) {
        return 0;
      }
      return activeLanguageData.rows.size();
    }

    public Dictionary.Row getItem(int rowIndex) {
      final LanguageData activeLanguageData = DictionaryActivity.this.activeLanguageData;
      if (activeLanguageData == null) {
        return null;
      }
      assert rowIndex < activeLanguageData.rows.size();
      return activeLanguageData.rows.get(rowIndex);
    }

    public long getItemId(int rowIndex) {
      return rowIndex;
    }

    public View getView(final int rowIndex, final View convertView,
        final ViewGroup parent) {
      final Row row = getItem(rowIndex);

      // Token row.
      if (row == null || row.isToken()) {
        TextView result = null;
        if (convertView instanceof TextView) {
          result = (TextView) convertView;
        } else {
          result = new TextView(parent.getContext());
        }
        if (row == null) {
          return result;
        }
        result.setText(activeLanguageData.rowToString(row));
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
        
        if (r>0){
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
        final String col1Text = entry.getAllText(activeLanguageData.lang)[r]; 
        column1.setText(col1Text, TextView.BufferType.SPANNABLE);
        final Spannable col1Spannable = (Spannable) column1.getText();
        int startPos = 0;
        final String token = activeLanguageData.getIndexEntryForRow(rowIndex).word;
        while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
          col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos, startPos + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
         startPos += token.length();
        }
        
        column2.setText(entry.getAllText(Entry.otherLang(activeLanguageData.lang))[r], TextView.BufferType.NORMAL);
        
        result.addView(tableRow);
      }
      
      return result;
    }
  }  // DictionaryListAdapter

  private class SearchTextWatcher implements TextWatcher {
    public void afterTextChanged(final Editable searchText) {
      if (getSearchText().hasFocus()) {
        // If they were typing to cause the change, update the UI.
        onSearchTextChange(searchText.toString());
      }
    }

    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
        int arg3) {
    }

    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
    }
  }
  
}