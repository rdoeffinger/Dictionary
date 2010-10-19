package com.hughes.android.dictionary;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
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
  
  static final int VIBRATE_MILLIS = 100;

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

  private Vibrator vibrator = null;
  
  public DictionaryActivity() {
  }
  
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
    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    
    PersistentObjectCache.init(this);
    QuickDicConfig quickDicConfig = PersistentObjectCache.init(
        this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);
    
    final Intent intent = getIntent();
    
    final int dictIndex = intent.getIntExtra(C.DICT_INDEX, 0);
    try {
      final DictionaryConfig dictionaryConfig = quickDicConfig.dictionaryConfigs.get(dictIndex);
      dictRaf = new RandomAccessFile(dictionaryConfig.localFile, "r");
      dictionary = new Dictionary(dictRaf); 
    } catch (Exception e) {
      Log.e(LOG, "Unable to load dictionary.", e);
      DictionaryEditActivity.getIntent(dictIndex);
      finish();
      return;
    }

    // Pre-load the collators.
    searchExecutor.execute(new Runnable() {
      public void run() {
        final long startMillis = System.currentTimeMillis();
        for (final Index index : dictionary.indices) {
          index.sortLanguage.getFindCollator();
          final com.ibm.icu.text.Collator c = index.sortLanguage
              .getSortCollator();
          if (c.compare("pre-print", "preppy") >= 0) {
            Log.e(LOG, c.getClass()
                + " is buggy, lookups may not work properly.");
          }
        }
        Log.d(LOG, "Loading collators took:"
            + (System.currentTimeMillis() - startMillis));
      }
    });
    
    indexIndex = intent.getIntExtra(C.INDEX_INDEX, 0) % dictionary.indices.size();
    index = dictionary.indices.get(indexIndex);
    setListAdapter(new IndexAdapter(index));
    
    setContentView(R.layout.dictionary_activity);
    searchText = (EditText) findViewById(R.id.SearchText);
    langButton = (Button) findViewById(R.id.LangButton);
    
    searchText.addTextChangedListener(new SearchTextWatcher());
    
    
    final Button clearSearchTextButton = (Button) findViewById(R.id.ClearSearchTextButton);
    clearSearchTextButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onClearSearchTextButton(clearSearchTextButton);
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
        onUpDownButton(true);
      }
    });
    final Button downButton = (Button) findViewById(R.id.DownButton);
    downButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        onUpDownButton(false);
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());
    
    if (prefs.getBoolean(getString(R.string.vibrateOnFailedSearchKey), true)) {
      vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    updateLangButton();

  }

  // --------------------------------------------------------------------------
  // Buttons
  // --------------------------------------------------------------------------

  private void onClearSearchTextButton(final Button clearSearchTextButton) {
    clearSearchTextButton.requestFocus();
    searchText.setText("");
    searchText.requestFocus();
    Log.d(LOG, "Trying to show soft keyboard.");
    final InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    manager.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
  }
  
  void updateLangButton() {
    langButton.setText(index.shortName.toUpperCase());
  }

  void onLanguageButton() {
    if (currentSearchOperation != null) {
      currentSearchOperation.interrupted.set(true);
      currentSearchOperation = null;
    }
    
    indexIndex = (indexIndex + 1) % dictionary.indices.size();
    index = dictionary.indices.get(indexIndex);
    indexAdapter = new IndexAdapter(index);
    Log.d(LOG, "onLanguageButton, newLang=" + index.longName);
    setListAdapter(indexAdapter);
    updateLangButton();
    onSearchTextChange(searchText.getText().toString());
  }
  
  void onUpDownButton(final boolean up) {
    final int firstVisibleRow = getListView().getFirstVisiblePosition();
    final RowBase row = index.rows.get(firstVisibleRow);
    final TokenRow tokenRow = row.getTokenRow(true);
    final int destIndexEntry;
    if (up) {
      if (row != tokenRow) {
        destIndexEntry = tokenRow.referenceIndex;
      } else {
        destIndexEntry = Math.max(tokenRow.referenceIndex - 1, 0);
      }
    } else {
      // Down
      destIndexEntry = Math.min(tokenRow.referenceIndex + 1, index.sortedIndexEntries.size());
    }
    
    Log.d(LOG, "onUpDownButton, destIndexEntry=" + destIndexEntry);
    jumpToRow(index.sortedIndexEntries.get(destIndexEntry).startRow);
  }

  // --------------------------------------------------------------------------
  // Menu
  // --------------------------------------------------------------------------

  // --------------------------------------------------------------------------
  // SearchOperation
  // --------------------------------------------------------------------------

  private void searchFinished(final SearchOperation searchOperation) {
    if (searchOperation != this.currentSearchOperation) {
      return;
    }
    
    final Index.SearchResult searchResult = searchOperation.searchResult;
    Log.d(LOG, "searchFinished, " + searchResult.longestPrefixString + ", success=" + searchResult.success);

    jumpToRow(searchResult.longestPrefix.startRow);
    
    if (!searchResult.success) {
      if (vibrator != null) {
        vibrator.vibrate(VIBRATE_MILLIS);
      }
      searchText.setText(searchResult.longestPrefixString);
      searchText.setSelection(searchResult.longestPrefixString.length());
      return;
    }
  }
  
  private final void jumpToRow(final int row) {
    setSelection(row);
    getListView().setSelected(true);
  }

  final class SearchOperation implements Runnable {
    
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    final String searchText;
    final Index index;
    
    long searchStartMillis;

    Index.SearchResult searchResult;
    
    SearchOperation(final String searchText, final Index index) {
      this.searchText = searchText.trim();
      this.index = index;
    }

    @Override
    public void run() {
      searchStartMillis = System.currentTimeMillis();
      searchResult = index.findLongestSubstring(searchText, interrupted);
      Log.d(LOG, "searchText=" + searchText + ", searchDuration="
          + (System.currentTimeMillis() - searchStartMillis) + ", interrupted="
          + interrupted.get());
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
