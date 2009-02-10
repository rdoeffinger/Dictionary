package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.hughes.util.FileUtil;

public class Dictionary extends ListActivity {

  public static final String INDEX_FORMAT = "%s_index_%d";
  private File dictionaryFile = new File("/sdcard/dict-de-en.txt");

  private RandomAccessFile dictionaryRaf;
  private final Index[] indexes = new Index[2];
  private final byte lang = Entry.LANG1;

  final Handler uiHandler = new Handler();

  private final Object mutex = new Object();
  private Executor searchExecutor = Executors.newSingleThreadExecutor();
  private SearchOperation searchOperation = null;
  private List<Entry> entries = Collections.emptyList();
  private DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();
  private int selectedItem = -1;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("THAD", "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    EditText searchText = (EditText) findViewById(R.id.SearchText);
    searchText.addTextChangedListener(new DictionaryTextWatcher());

    setListAdapter(dictionaryListAdapter);

    try {
      loadIndex();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
        Log
            .d("THAD", "context menu: "
                + entries.get(selectedItem).getRawText());
        return false;
      }
    });
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Log.d("THAD", "Clicked: " + entries.get(position).getRawText());
    selectedItem = position;
    openContextMenu(getListView());
  }

  private void loadIndex() throws IOException, ClassNotFoundException {
    Log.d("THAD", "enter loadIndex");
    indexes[0] = new Index(String.format(INDEX_FORMAT, dictionaryFile
        .getAbsolutePath(), Entry.LANG1));
    dictionaryRaf = new RandomAccessFile(dictionaryFile, "r");
    Log.d("THAD", "exit loadIndex");
  }

  void onSearchTextChange(final String searchText) {
    Log.d("THAD", "onSearchTextChange: " + searchText);
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
      final List<Entry> newEntries = new ArrayList<Entry>(count);
      try {
        final Index.Node node = indexes[lang].lookup(searchText);
        final Set<Integer> entryOffsets = new LinkedHashSet<Integer>(count);
        node.getDescendantEntryOffsets(entryOffsets, count);
        for (final int entryOffset : entryOffsets) {
          if (interrupted.get()) {
            Log.d("THAD", "Interrupted while parsing entries.");
            return;
          }
          final String line = FileUtil.readLine(dictionaryRaf, entryOffset);
          final Entry entry = new Entry(line);
          newEntries.add(entry);
        }
      } catch (IOException e) {
        Log.e("THAD", "Search failure.", e);
      }

      synchronized (mutex) {
        if (interrupted.get()) {
          return;
        }
        entries = newEntries;
      }

      uiHandler.post(new Runnable() {
        public void run() {
          synchronized (mutex) {
            dictionaryListAdapter.notifyDataSetChanged();
            if (entries.size() > 0) {
              setSelection(0);
            }
          }
        }
      });
    }
  }

  private class DictionaryListAdapter extends BaseAdapter {

    public int getCount() {
      synchronized (mutex) {
        return entries.size();
      }
    }

    public Object getItem(int position) {
      synchronized (mutex) {
        assert position < entries.size();
        return entries.get(position).getFormattedEntry(lang);
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
      result.setText((String) getItem(position));
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

  public void run() {
    // TODO Auto-generated method stub

  }

}