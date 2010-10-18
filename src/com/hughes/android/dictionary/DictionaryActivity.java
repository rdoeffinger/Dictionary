package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ListActivity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Index;
import com.hughes.android.dictionary.engine.PairEntry;
import com.hughes.android.dictionary.engine.RowBase;
import com.hughes.android.dictionary.engine.TokenRow;
import com.hughes.android.util.PersistentObjectCache;

public class DictionaryActivity extends ListActivity {

  static final String LOG = "QuickDic";

  RandomAccessFile dictRaf = null;
  Dictionary dictionary = null;
  int indexIndex = 0;
  Index index = null;
  
  // package for test.
  final Handler uiHandler = new Handler();
  private final Executor searchExecutor = Executors.newSingleThreadExecutor();
  private SearchOperation currentSearchOperation = null;

  EditText searchText;
  Button langButton;

  // Never null.
  private File wordList = null;
  private boolean saveOnlyFirstSubentry = false;

  // Visible for testing.
  ListAdapter indexAdapter = null;

  
  public static Intent getIntent(final int dictIndex, final int indexIndex, final String searchToken) {
    final Intent intent = new Intent();
    intent.setClassName(DictionaryActivity.class.getPackage().getName(), DictionaryActivity.class.getName());
    intent.putExtra(C.DICT_INDEX, dictIndex);
    intent.putExtra(C.INDEX_INDEX, indexIndex);
    intent.putExtra(C.SEARCH_TOKEN, searchToken);
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    PersistentObjectCache.init(this);
    QuickDicConfig quickDicConfig = PersistentObjectCache.init(
        this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);
    
    final Intent intent = getIntent();
    
    final DictionaryConfig dictionaryConfig = quickDicConfig.dictionaryConfigs.get(intent.getIntExtra(C.DICT_INDEX, 0));
    try {
      dictRaf = new RandomAccessFile(dictionaryConfig.localFile, "r");
      dictionary = new Dictionary(dictRaf); 
    } catch (IOException e) {
      Log.e(LOG, "Unable to load dictionary.", e);
      // TODO: Start up the editor.
      finish();
      return;
    }
    
    indexIndex = intent.getIntExtra(C.INDEX_INDEX, 0);
    index = dictionary.indices.get(indexIndex);
    setListAdapter(new IndexAdapter(index));
    
    setContentView(R.layout.dictionary_activity);
    searchText = (EditText) findViewById(R.id.SearchText);
    langButton = (Button) findViewById(R.id.LangButton);
    
    searchText.addTextChangedListener(new SearchTextWatcher());
    
    
    final Button clearSearchTextButton = (Button) findViewById(R.id.ClearSearchTextButton);
    clearSearchTextButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        //onClearSearchTextButton(clearSearchTextButton);
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
        //onUpButton();
      }
    });
    final Button downButton = (Button) findViewById(R.id.DownButton);
    downButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        //onDownButton();
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());

    updateLangButton();

  }
  
  void updateLangButton() {
    langButton.setText(index.shortName.toUpperCase());
  }


  
  
  void onLanguageButton() {
    // TODO: synchronized, stop search.
    
    indexIndex = (indexIndex + 1) % dictionary.indices.size();
    index = dictionary.indices.get(indexIndex);
    indexAdapter = new IndexAdapter(index);
    Log.d(LOG, "onLanguageButton, newLang=" + index.longName);
    setListAdapter(indexAdapter);
    updateLangButton();
    onSearchTextChange(searchText.getText().toString());
  }
  
  // --------------------------------------------------------------------------
  // SearchOperation
  // --------------------------------------------------------------------------

  private void searchFinished(final SearchOperation searchOperation) {
    if (searchOperation == this.currentSearchOperation) {
      setSelection(searchOperation.tokenRow.index());
      getListView().setSelected(true);
    }
  }

  final class SearchOperation implements Runnable {
    
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    final String searchText;
    final Index index;
    
    boolean failed = false;
    TokenRow tokenRow;
    
    SearchOperation(final String searchText, final Index index) {
      this.searchText = searchText.trim();
      this.index = index;
    }

    @Override
    public void run() {
      tokenRow = index.findInsertionPoint(searchText, interrupted);
      failed = false; // TODO
      if (!interrupted.get()) {
        uiHandler.post(new Runnable() {
          @Override
          public void run() {            
            searchFinished(SearchOperation.this);
          }
        });
      }
    }
  }

  
  // --------------------------------------------------------------------------
  // IndexAdapter
  // --------------------------------------------------------------------------

  static final class IndexAdapter extends BaseAdapter {
    
    final Index index;

    IndexAdapter(final Index index) {
      this.index = index;
    }

    @Override
    public int getCount() {
      return index.rows.size();
    }

    @Override
    public Object getItem(int position) {
      return index.rows.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final RowBase row = index.rows.get(position);
      if (row instanceof PairEntry.Row) {
        return getView((PairEntry.Row) row, parent);
      } else if (row instanceof TokenRow) {
        return getView((TokenRow) row, parent);
      } else {
        throw new IllegalArgumentException("Unsupported Row type: " + row.getClass());
      }
    }

    private View getView(PairEntry.Row row, ViewGroup parent) {
      final TableLayout result = new TableLayout(parent.getContext());
      final PairEntry entry = row.getEntry();
      final int rowCount = entry.pairs.length;
      for (int r = 0; r < rowCount; ++r) {
        final TableRow tableRow = new TableRow(result.getContext());

        TextView column1 = new TextView(tableRow.getContext());
        TextView column2 = new TextView(tableRow.getContext());
        final TableRow.LayoutParams layoutParams = new TableRow.LayoutParams();
        layoutParams.weight = 0.5f;

        if (r > 0) {
          final TextView spacer = new TextView(tableRow.getContext());
          spacer.setText(" • ");
          tableRow.addView(spacer);
        }
        tableRow.addView(column1, layoutParams);
        if (r > 0) {
          final TextView spacer = new TextView(tableRow.getContext());
          spacer.setText(" • ");
          tableRow.addView(spacer);
        }
        tableRow.addView(column2, layoutParams);

        column1.setWidth(1);
        column2.setWidth(1);

        // TODO: color words by gender
        final String col1Text = index.swapPairEntries ? entry.pairs[r].lang2 : entry.pairs[r].lang1;
        column1.setText(col1Text, TextView.BufferType.SPANNABLE);
        final Spannable col1Spannable = (Spannable) column1.getText();
        
        int startPos = 0;
        final String token = row.getTokenRow(true).getToken();
        while ((startPos = col1Text.indexOf(token, startPos)) != -1) {
          col1Spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos,
              startPos + token.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
          startPos += token.length();
        }

        final String col2Text = index.swapPairEntries ? entry.pairs[r].lang1 : entry.pairs[r].lang2;
        column2.setText(col2Text, TextView.BufferType.NORMAL);

        result.addView(tableRow);
      }

      return result;
    }

    private View getView(TokenRow row, ViewGroup parent) {
      final TextView textView = new TextView(parent.getContext());
      textView.setText(row.getToken());
      textView.setTextSize(20);
      return textView;
    }
    
  }

  // --------------------------------------------------------------------------
  // SearchText
  // --------------------------------------------------------------------------

  void onSearchTextChange(final String searchText) {
    Log.d(LOG, "onSearchTextChange: " + searchText);
    if (currentSearchOperation != null) {
      currentSearchOperation.interrupted.set(true);
    }
    currentSearchOperation = new SearchOperation(searchText, index);
    searchExecutor.execute(currentSearchOperation);
  }
  
  private class SearchTextWatcher implements TextWatcher {
    public void afterTextChanged(final Editable searchTextEditable) {
      Log.d(LOG, "Search text changed: " + searchText.getText());
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
