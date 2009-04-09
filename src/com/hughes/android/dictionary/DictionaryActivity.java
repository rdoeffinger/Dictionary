package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ListActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
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

  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;
  private LanguageData activeLangaugeData = null;

  private File wordList = new File("/sdcard/wordList.txt");

  final Handler uiHandler = new Handler();

  private Executor searchExecutor = Executors.newSingleThreadExecutor();
  private SearchOperation searchOperation = null;
  // private List<Entry> entries = Collections.emptyList();
  private DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();
  private int selectedRowIndex = -1;
  private int selectedTokenRowIndex = -1;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("THAD", "onCreate");
    super.onCreate(savedInstanceState);

    try {
      dictRaf = new RandomAccessFile("/sdcard/de-en.dict", "r");
      dictionary = new Dictionary(dictRaf);
      activeLangaugeData = dictionary.languageDatas[Entry.LANG1];
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setContentView(R.layout.main);

    final EditText searchText = (EditText) findViewById(R.id.SearchText);
    searchText.addTextChangedListener(new DictionaryTextWatcher());

    setListAdapter(dictionaryListAdapter);

    onSearchTextChange("");
    
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

    // Context menu.
    registerForContextMenu(getListView());
    
    getListView().setOnItemSelectedListener(new OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> arg0, View arg1, int rowIndex,
          long arg3) {
        Log.d("THAD", "onItemSelected: " + rowIndex);
        selectedRowIndex = rowIndex;
        selectedTokenRowIndex = activeLangaugeData.getTokenRow(rowIndex);
        updateSearchText();
      }

      public void onNothingSelected(AdapterView<?> arg0) {
      }});
    
    
    getListView().setOnItemLongClickListener((new OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int rowIndex,
          long arg3) {
        selectedRowIndex = rowIndex;
        return false;
      }
    }));
  }
  
  public String getSelectedRowText() {
    return activeLangaugeData.rowToString(activeLangaugeData.rows.get(selectedRowIndex));
  }

  private MenuItem switchLanguageMenuItem = null;
  
  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    switchLanguageMenuItem = menu.add("Switch to language.");
    return true;
  }
  
  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    switchLanguageMenuItem.setTitle(String.format("Switch to %s", dictionary.languageDatas[Entry.otherLang(activeLangaugeData.lang)].language.symbol));
    return super.onPrepareOptionsMenu(menu);
  }
  
  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    if (item == switchLanguageMenuItem) {
      switchLanguage();
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    if (selectedRowIndex == -1) {
      return;
    }
    final MenuItem addToWordlist = menu.add("Add to wordlist.");
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        final String rawText = getSelectedRowText();
        Log.d("THAD", "Writing : " + rawText);
        try {
          final OutputStream out = new FileOutputStream(wordList, true);
          out.write((rawText + "\n").getBytes());
          out.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return false;
      }
    });
  }
  
  void switchLanguage() {
    activeLangaugeData = dictionary.languageDatas[(activeLangaugeData == dictionary.languageDatas[0]) ? 1 : 0];
    updateLangButton();
    dictionaryListAdapter.notifyDataSetChanged();
    final EditText searchText = (EditText) findViewById(R.id.SearchText);
    onSearchTextChange(searchText.getText().toString());
  }
  
  void updateLangButton() {
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setText(activeLangaugeData.language.symbol);
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (event.getUnicodeChar() != 0) {
      final EditText searchText = (EditText) findViewById(R.id.SearchText);
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
    Log.d("THAD", "Clicked: " + getSelectedRowText());
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d("THAD", "onSearchTextChange: " + searchText);
    if (searchOperation != null) {
      searchOperation.interrupted.set(true);
    }
    searchOperation = new SearchOperation(searchText);
    searchExecutor.execute(searchOperation);
  }
  
  private void jumpToRow(final int rowIndex) {
    selectedRowIndex = rowIndex;
    selectedTokenRowIndex = activeLangaugeData.getTokenRow(rowIndex);
    getListView().setSelection(rowIndex);
    getListView().setSelected(true);
    updateSearchText();
  }

  private void updateSearchText() {
    final EditText searchText = (EditText) findViewById(R.id.SearchText);
    if (!searchText.hasFocus()) {
      searchText.setText(activeLangaugeData.rowToString(activeLangaugeData.rows.get(selectedTokenRowIndex)));
    }
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
      return activeLangaugeData.rows.size();
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
//        result.setBackgroundColor(Color.WHITE);
        result.setText(activeLangaugeData.rowToString(row));
        result.setTextAppearance(parent.getContext(),
            android.R.style.TextAppearance_Large);
        return result;
      }

      // Entry row(s).
      final TableLayout result = new TableLayout(parent.getContext());

      final Entry entry = dictionary.entries.get(row.getIndex());
      final int rowCount = entry.getRowCount();
      for (int r = 0; r < rowCount; ++r) {
        final TableRow tableRow = new TableRow(result.getContext());
//        if (r > 0) {
//          tableRow.setBackgroundColor(Color.DKGRAY);  
//        }
        
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
        if (r>0){
          final TextView spacer = new TextView(tableRow.getContext());
          spacer.setText(r == 0 ? "• " : " • ");
          tableRow.addView(spacer);
        }
        tableRow.addView(column2, layoutParams);
        
        column1.setWidth(1);
        column2.setWidth(1);
        // column1.setTextAppearance(parent.getContext(), android.R.style.Text);
        
        // TODO: highlight query word in entries.
        column1.setText(entry.getAllText(activeLangaugeData.lang)[r]);
        column2.setText(entry.getAllText(Entry.otherLang(activeLangaugeData.lang))[r]);
        
        result.addView(tableRow);
      }
      
      return result;
    }
  }  // DictionaryListAdapter

  private class DictionaryTextWatcher implements TextWatcher {
    public void afterTextChanged(Editable searchText) {
      onSearchTextChange(searchText.toString());
    }

    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
        int arg3) {
    }

    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
    }
  }

}