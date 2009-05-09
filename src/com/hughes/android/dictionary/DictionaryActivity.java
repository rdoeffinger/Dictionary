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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
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

  String WORD_LIST_FILE; 
  String DICT_FILE; 
  String DICT_FETCH_URL; 
  
  static final Intent preferencesIntent = new Intent().setClassName(PreferenceActivity.class.getPackage().getName(), PreferenceActivity.class.getCanonicalName());

  private final Handler uiHandler = new Handler();
  private final Executor searchExecutor = Executors.newSingleThreadExecutor();
  private final DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();

  // Never null.
  private File wordList = new File("/sdcard/wordList.txt");

  // Can be null.
  private File dictFile = null;
  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;
  private LanguageData activeLangaugeData = null;

  private SearchOperation searchOperation = null;
  private int selectedRowIndex = -1;
  private int selectedTokenRowIndex = -1;
  

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WORD_LIST_FILE = getResources().getString(R.string.wordListFile); 
    DICT_FILE = getResources().getString(R.string.dictFile); 
    DICT_FETCH_URL = getResources().getString(R.string.dictFetchUrl); 

    Log.d("THAD", "onCreate");
  }
  
  @Override
  public void onResume() {
    super.onResume();

    // Have to close, because we might have downloaded a new copy of the dictionary.
    closeCurrentDictionary();

    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
    wordList = new File(settings.getString(WORD_LIST_FILE, wordList.getAbsolutePath()));
    final File newDictFile = new File(settings.getString(DICT_FILE, "/sdcard/de-en.dict"));
    dictFile = newDictFile;
    Log.d("THAD", "wordList=" + wordList);
    Log.d("THAD", "dictFile=" + dictFile);

    if (!dictFile.canRead()) {
      dictionaryListAdapter.notifyDataSetChanged();
      Log.d("THAD", "Unable to read dictionary file.");
      final AlertDialog alert = new AlertDialog.Builder(DictionaryActivity.this).create();
      alert.setMessage("Unable to read dictionary file: " + dictFile.getAbsolutePath());
      alert.setButton("Download dictionary", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          startDownloadDictActivity();
        }});
      alert.show();
      return;
    }
    
    try {
      dictRaf = new RandomAccessFile(dictFile, "r");
      dictionary = new Dictionary(dictRaf);
      activeLangaugeData = dictionary.languageDatas[Entry.LANG1];
      dictionaryListAdapter.notifyDataSetChanged();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setContentView(R.layout.main);

    getSearchText().addTextChangedListener(new DictionaryTextWatcher());

    setListAdapter(dictionaryListAdapter);

    // Language button.
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        switchLanguage();
      }});
    updateLangButton();

    final Button upButton = (Button) findViewById(R.id.UpButton);
    upButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        if (dictionary == null) {
          return;
        }
        final int destRowIndex;
        final Row tokenRow = activeLangaugeData.rows.get(selectedTokenRowIndex);
        assert tokenRow.isToken();
        final int prevTokenIndex = tokenRow.getIndex() - 1;
        if (selectedRowIndex == selectedTokenRowIndex && selectedRowIndex > 0) {
          destRowIndex = activeLangaugeData.sortedIndex.get(prevTokenIndex).startRow;
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
        final Row tokenRow = activeLangaugeData.rows.get(selectedTokenRowIndex);
        assert tokenRow.isToken();
        final int nextTokenIndex = tokenRow.getIndex() + 1;
        final int destRowIndex;
        if (nextTokenIndex < activeLangaugeData.sortedIndex.size()) {
          destRowIndex = activeLangaugeData.sortedIndex.get(nextTokenIndex).startRow;
        } else {
          destRowIndex = activeLangaugeData.rows.size() - 1;
        }
        jumpToRow(destRowIndex);
      }});

    // ContextMenu.
    registerForContextMenu(getListView());

    // ItemSelectedListener.
    getListView().setOnItemSelectedListener(new OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> arg0, View arg1, int rowIndex,
          long arg3) {
        Log.d("THAD", "onItemSelected: " + rowIndex);
        selectedRowIndex = rowIndex;
        selectedTokenRowIndex = activeLangaugeData.getIndexEntryForRow(rowIndex).startRow;
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

    onSearchTextChange("");
  }
  
  
  @Override
  public void onStop() {
    super.onStop();
    closeCurrentDictionary();
  }

  private void closeCurrentDictionary() {
    dictionary = null;
    activeLangaugeData = null;
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
    return activeLangaugeData.rowToString(activeLangaugeData.rows.get(selectedRowIndex));
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
    switchLanguageMenuItem = menu.add("Switch to language.");
    switchLanguageMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        switchLanguage();
        return false;
      }});

    final MenuItem preferences = menu.add("Preferences...");
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        startActivity(preferencesIntent);
        return false;
      }});

    final MenuItem about = menu.add("About...");
    about.setOnMenuItemClickListener(new OnMenuItemClickListener(){
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class.getPackage().getName(), AboutActivity.class.getCanonicalName());
        final StringBuilder currentDictInfo = new StringBuilder();
        if (dictionary == null) {
          currentDictInfo.append("No dictionary loaded.");
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

    final MenuItem download = menu.add("Download dictionary...");
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
      switchLanguageMenuItem.setTitle(String.format("Switch to %s", dictionary.languageDatas[Entry.otherLang(activeLangaugeData.lang)].language.symbol));
    }
    switchLanguageMenuItem.setEnabled(dictionary != null);
    return super.onPrepareOptionsMenu(menu);
  }

  void switchLanguage() {
    if (dictionary == null) {
      return;
    }
    activeLangaugeData = dictionary.languageDatas[(activeLangaugeData == dictionary.languageDatas[0]) ? 1 : 0];
    selectedRowIndex = 0;
    selectedTokenRowIndex = 0;
    updateLangButton();
    dictionaryListAdapter.notifyDataSetChanged();
    onSearchTextChange(getSearchText().getText().toString());
  }
  
  void updateLangButton() {
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setText(activeLangaugeData.language.symbol);
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
    final MenuItem addToWordlist = menu.add("Add to wordlist: " + wordList.getName());
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        final StringBuilder rawText = new StringBuilder();
        final String word = activeLangaugeData.getIndexEntryForRow(selectedRowIndex).word;
        rawText.append(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date())).append("\t");
        rawText.append(word).append("\t");
        rawText.append(getSelectedRowRawText());
        Log.d("THAD", "Writing : " + rawText);
        try {
          wordList.getParentFile().mkdirs();
          final PrintWriter out = new PrintWriter(new FileWriter(wordList, true));
          out.println(rawText.toString());
          out.close();
        } catch (IOException e) {
          Log.e("THAD", "Unable to append to " + wordList.getAbsolutePath(), e);
          final AlertDialog alert = new AlertDialog.Builder(DictionaryActivity.this).create();
          alert.setMessage("Failed to append to file: " + wordList.getAbsolutePath());
          alert.show();
        }
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
    Log.d("THAD", "Clicked: " + getSelectedRowRawText());
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d("THAD", "onSearchTextChange: " + searchText);
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
    Log.d("THAD", "jumpToRow: " + rowIndex);
    selectedRowIndex = rowIndex;
    selectedTokenRowIndex = activeLangaugeData.getIndexEntryForRow(rowIndex).startRow;
    getListView().setSelection(rowIndex);
    getListView().setSelected(true);  // TODO: is this doing anything?
    updateSearchText();
  }

  private void updateSearchText() {
    final EditText searchText = getSearchText();
    if (!searchText.hasFocus()) {
      final String word = activeLangaugeData.getIndexEntryForRow(selectedRowIndex).word;
      if (!word.equals(searchText.getText().toString())) {
        Log.d("THAD", "updateSearchText: setText: " + word);
        searchText.setText(word);
      }
    }
  }

  private void startDownloadDictActivity() {
    final Intent intent = new Intent().setClassName(
        DownloadActivity.class.getPackage().getName(),
        DownloadActivity.class.getCanonicalName());
    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(DictionaryActivity.this);
    final String dictFetchUrl = settings.getString(DICT_FETCH_URL, null);
    final String dictFileName = settings.getString(DICT_FILE, null);
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
      Log.d("THAD", "SearchOperation: " + searchText);
      final int indexLocation = activeLangaugeData.lookup(searchText, interrupted);
      if (interrupted.get()) {
        return;
      }
      final IndexEntry indexEntry = activeLangaugeData.sortedIndex
          .get(indexLocation);
      uiHandler.post(new Runnable() {
        public void run() {
          jumpToRow(indexEntry.startRow);
        }
      });
    }
  }

  private class DictionaryListAdapter extends BaseAdapter {

    public int getCount() {
      return dictionary != null ? activeLangaugeData.rows.size() : 0;
    }

    public Dictionary.Row getItem(int rowIndex) {
      assert rowIndex < activeLangaugeData.rows.size();
      return activeLangaugeData.rows.get(rowIndex);
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
        result.setText(activeLangaugeData.rowToString(row));
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
        final String col1Text = entry.getAllText(activeLangaugeData.lang)[r]; 
        column1.setText(col1Text, TextView.BufferType.SPANNABLE);
        final Spannable col1Spannable = (Spannable) column1.getText();
        int startPos = 0;
        final String token = activeLangaugeData.getIndexEntryForRow(rowIndex).word;
        while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
          col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos, startPos + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
         startPos += token.length();
        }
        
        column2.setText(entry.getAllText(Entry.otherLang(activeLangaugeData.lang))[r], TextView.BufferType.NORMAL);
        
        result.addView(tableRow);
      }
      
      return result;
    }
  }  // DictionaryListAdapter

  private class DictionaryTextWatcher implements TextWatcher {
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