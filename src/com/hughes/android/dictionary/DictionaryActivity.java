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
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
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

import com.hughes.android.dictionary.Dictionary.IndexEntry;
import com.hughes.android.dictionary.Dictionary.Language;
import com.hughes.android.dictionary.Dictionary.Row;

public class DictionaryActivity extends ListActivity {

  private RandomAccessFile dictRaf = null;
  private Dictionary dictionary = null;
  private Language activeLanguge = null;

  private File wordList = new File("/sdcard/wordList.txt");

  final Handler uiHandler = new Handler();

  private Executor searchExecutor = Executors.newSingleThreadExecutor();
  private SearchOperation searchOperation = null;
  // private List<Entry> entries = Collections.emptyList();
  private DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();
  private int selectedRow = -1;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("THAD", "onCreate");
    super.onCreate(savedInstanceState);

    try {
      dictRaf = new RandomAccessFile("/sdcard/de-en.dict", "r");
      dictionary = new Dictionary(dictRaf);
      activeLanguge = dictionary.languages[Entry.LANG1];
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setContentView(R.layout.main);

    final EditText searchText = (EditText) findViewById(R.id.SearchText);
    searchText.addTextChangedListener(new DictionaryTextWatcher());

    setListAdapter(dictionaryListAdapter);

    onSearchTextChange("");
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        switchLanguage();
      }});
    updateLangButton();

    registerForContextMenu(getListView());
    getListView().setOnItemLongClickListener((new OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
          long arg3) {
        selectedRow = arg2;
        return false;
      }
    }));
  }
  
  public String getSelectedRowText() {
    return activeLanguge.rowToString(activeLanguge.rows.get(selectedRow));
  }

  private MenuItem switchLanguageMenuItem = null;
  
  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    switchLanguageMenuItem = menu.add("Switch to language.");
    return true;
  }
  
  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    switchLanguageMenuItem.setTitle(String.format("Switch to %s", dictionary.languages[Entry.otherLang(activeLanguge.lang)].symbol));
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
    if (selectedRow == -1) {
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
    activeLanguge = dictionary.languages[(activeLanguge == dictionary.languages[0]) ? 1 : 0];
    updateLangButton();
    dictionaryListAdapter.notifyDataSetChanged();
    final EditText searchText = (EditText) findViewById(R.id.SearchText);
    onSearchTextChange(searchText.getText().toString());
  }
  
  void updateLangButton() {
    final Button langButton = (Button) findViewById(R.id.LangButton);
    langButton.setText(dictionary.languages[activeLanguge.lang].symbol.toUpperCase());
  }

  @Override
  protected void onListItemClick(ListView l, View v, int row, long id) {
    selectedRow = row;
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
  
  private void jumpToRow(final int row) {
    selectedRow = row;
    getListView().setSelection(row);
  }

  private final class SearchOperation implements Runnable {
    final String searchText;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    public SearchOperation(final String searchText) {
      this.searchText = searchText;
    }

    public void run() {
      Log.d("THAD", "SearchOperation: " + searchText);
      final int indexLocation = activeLanguge.lookup(searchText, interrupted);
      if (interrupted.get()) {
        return;
      }
      final IndexEntry indexEntry = activeLanguge.sortedIndex
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
      return activeLanguge.rows.size();
    }

    public Dictionary.Row getItem(int position) {
      assert position < activeLanguge.rows.size();
      return activeLanguge.rows.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(final int position, final View convertView,
        final ViewGroup parent) {
      final Row row = getItem(position);
      if (row.isToken()) {
        TextView result = null;
        if (convertView instanceof TextView) {
          result = (TextView) convertView;
        } else {
          result = new TextView(parent.getContext());
        }
        result.setText(activeLanguge.rowToString(row));
        result.setTextAppearance(parent.getContext(),
            android.R.style.TextAppearance_Large);
        return result;
      }

      TableLayout result = null;
      if (convertView instanceof TableLayout) {
        result = (TableLayout) convertView;
      } else {
        result = new TableLayout(parent.getContext());
      }

      TableRow tableRow = null;
      if (result.getChildCount() != 1) {
        result.removeAllViews();
        tableRow = new TableRow(result.getContext());
        result.addView(tableRow);
      } else {
        tableRow = (TableRow) result.getChildAt(0);
      }
      TextView column1 = null;
      TextView column2 = null;
      if (tableRow.getChildCount() != 2
          || !(tableRow.getChildAt(0) instanceof TextView)
          || !(tableRow.getChildAt(1) instanceof TextView)) {
        tableRow.removeAllViews();
        column1 = new TextView(tableRow.getContext());
        column2 = new TextView(tableRow.getContext());
        tableRow.addView(column1);
        tableRow.addView(column2);
      } else {
        column1 = (TextView) tableRow.getChildAt(0);
        column2 = (TextView) tableRow.getChildAt(1);
      }
      column1.setWidth(100);
      column2.setWidth(100);
      // column1.setTextAppearance(parent.getContext(), android.R.style.Text);
      final Entry entry = dictionary.entries.get(row.getIndex());
      column1.setText(entry.getAllText(activeLanguge.lang));
      column2.setText(entry.getAllText(Entry.otherLang(activeLanguge.lang)));
      // result.setTextAppearance(parent.getContext(),
      // android.R.style.TextAppearance_Small);
      return result;

    }
  }

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