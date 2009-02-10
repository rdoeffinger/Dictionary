package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class Dictionary extends Activity {

  private String searchText = "";
  
  private List<Entry> entries = new ArrayList<Entry>(100);

  public static final String INDEX_FORMAT = "%s_index_%d"; 
  private final File dictionaryFile = new File("/sdcard/dict-de-en.txt");
  private final Index[] indexes = new Index[2];
  private final byte lang = Entry.LANG1; 

  private DictionaryListAdapter dictionaryListAdapter = new DictionaryListAdapter();
  

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("THAD", "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    EditText searchText = (EditText) findViewById(R.id.SearchText);
    searchText.addTextChangedListener(new DictionaryTextWatcher());

    ListView searchResults = (ListView) findViewById(R.id.SearchResults);
    searchResults.setAdapter(dictionaryListAdapter);

    try {
      loadIndex();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  private void loadIndex() throws IOException, ClassNotFoundException {
    Log.d("THAD", "enter loadIndex");
    indexes[0] = new Index(String.format(INDEX_FORMAT, dictionaryFile.getAbsolutePath(), Entry.LANG1));
    Log.d("THAD", "exit loadIndex");
  }

  void onSearchTextChange(final String searchText) {
    this.searchText = searchText;
    final Index.Node node = indexes[lang].lookup(searchText);

    try {
      final long length = dictionaryFile.length();
      Log.d("THAD", "Dictionary file length=" + length);

      final RandomAccessFile raf = new RandomAccessFile(dictionaryFile, "r");
      raf.seek(length / 2);
//      final InputStreamReader dictionaryReader = new InputStreamReader(new BufferedInputStream(new FileInputStream("/sdcard/dict-de-en.txt")));
//      skip(dictionaryReader, length / 2);

      for (int i = 0; i < entries.length; ++i) {
        entries[i] = raf.readLine();
        raf.skipBytes((int) (length / 100000));
      }
      
      raf.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    dictionaryListAdapter.notifyDataSetChanged();
  }
  
  private class DictionaryListAdapter extends BaseAdapter {

    public int getCount() {
      return 1000000;

    }

    public Object getItem(int position) {
      return searchText + position + entries[position % entries.length];
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
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

}