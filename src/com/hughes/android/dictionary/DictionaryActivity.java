package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hughes.android.dictionary.Dictionary.Row;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemLongClickListener;

public class DictionaryActivity extends ListActivity {

  private Dictionary dictionary = null;
  
  private File wordList = new File("/sdcard/wordList.txt");

  final Handler uiHandler = new Handler();

  private final Object mutex = new Object();
  private Executor searchExecutor = Executors.newSingleThreadExecutor();
  private SearchOperation searchOperation = null;
//  private List<Entry> entries = Collections.emptyList();
  private DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();
  private int selectedItem = -1;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("THAD", "onCreate");
    super.onCreate(savedInstanceState);
    
    try {
      dictionary = new Dictionary("/sdcard/dict-de-en.txt", Entry.LANG1);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    setContentView(R.layout.main);

    EditText searchText = (EditText) findViewById(R.id.SearchText);
    searchText.addTextChangedListener(new DictionaryTextWatcher());

    setListAdapter(dictionaryListAdapter);

    onSearchTextChange("");

    registerForContextMenu(getListView());
    getListView().setOnItemLongClickListener((new OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
          long arg3) {
        selectedItem = arg2;
        return false;
      }
    }));
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    if (selectedItem == -1) {
      return;
    }
    final MenuItem addToWordlist = menu.add("Add to wordlist.");
    addToWordlist.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        final String rawText = "";//entries.get(selectedItem).getRawText();
        Log.d("THAD", "Writing : "
                + rawText);
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

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    try {
      Log.d("THAD", "Clicked: " + dictionary.getRow(position));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    selectedItem = position;
    openContextMenu(getListView());
  }

  void onSearchTextChange(final String searchText) {
    Log.d("THAD", "onSearchTextChange: " + searchText);
    if (1==1) return;
    synchronized (mutex) {
      if (searchOperation != null) {
        searchOperation.interrupted.set(true);
      }
      searchOperation = new SearchOperation(searchText);
    }
    searchExecutor.execute(searchOperation);
  }

  private final class SearchOperation implements Runnable {
    final String searchText;
    final int count = 100;
    final AtomicBoolean interrupted = new AtomicBoolean(false);

    public SearchOperation(final String searchText) {
      this.searchText = searchText.toLowerCase(); // TODO: better
    }

    public void run() {
      Log.d("THAD", "SearchOperation: " + searchText);
 
      uiHandler.post(new Runnable() {
        public void run() {
          synchronized (mutex) {
            dictionaryListAdapter.notifyDataSetChanged();
          }
        }
      });
    }
  }

  private class DictionaryListAdapter extends BaseAdapter {

    public int getCount() {
      return dictionary.numRows();
    }

    public Dictionary.Row getItem(int position) {
      assert position < dictionary.numRows();
      try {
        return dictionary.getRow(position);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(final int position, final View convertView,
        final ViewGroup parent) {
      TextView result = null;
      if (convertView instanceof TextView) {
        result = (TextView) convertView;
      } else {
        result = new TextView(parent.getContext());
      }
      final Row row = getItem(position);
      result.setText(row.text);
      result.setBackgroundColor(row.isWord ? );
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