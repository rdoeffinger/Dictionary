package com.hughes.android.dictionary;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.hughes.android.util.PersistentObjectCache;

public class DictionaryListActivity extends ListActivity {

  static final String LOG = "QuickDic";
  
  static final String DICTIONARY_CONFIGS = "dictionaryConfigs";
  
  List<DictionaryConfig> dictionaries = new ArrayList<DictionaryConfig>();
  
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);

    // UI init.
    setContentView(R.layout.list_activity);
    
    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int row,
          long arg3) {
        return false;
      }
    });
    
    // ContextMenu.
    registerForContextMenu(getListView());
    
    getListView().setItemsCanFocus(true);
  }
  
  @SuppressWarnings("unchecked")
  @Override
  protected void onResume() {
    super.onResume();

    dictionaries = (List<DictionaryConfig>) PersistentObjectCache.init(this).read(DICTIONARY_CONFIGS);
    if (dictionaries == null) {
      dictionaries = new ArrayList<DictionaryConfig>();
    }
    if (dictionaries.size() == 0) {
      final DictionaryConfig dictionaryConfig = DictionaryConfig.defaultConfig();
      dictionaries.add(dictionaryConfig);
      PersistentObjectCache.getInstance().write(DICTIONARY_CONFIGS, dictionaries);
    }

    setListAdapter(new Adapter());
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem newDictionaryMenuItem = menu.add(R.string.addDictionary);
    newDictionaryMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            final DictionaryConfig dictionaryConfig = new DictionaryConfig();
            dictionaryConfig.name = getString(R.string.newDictionary);
            dictionaries.add(0, dictionaryConfig);
            dictionaryConfigsChanged();
            return false;
          }
        });
    
    return true;
  }
  

  @Override
  public void onCreateContextMenu(final ContextMenu menu, final View view,
      final ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, view, menuInfo);
    
    final AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
    
    final MenuItem editMenuItem = menu.add(R.string.editDictionary);
    editMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        final Intent intent = new Intent(DictionaryListActivity.this, DictionaryEditActivity.class);
        intent.putExtra(DictionaryEditActivity.DICT_INDEX, adapterContextMenuInfo.position);
        startActivity(intent);
        return true;
      }
    });

    if (adapterContextMenuInfo.position > 0) {
      final MenuItem moveUpMenuItem = menu.add(R.string.moveUp);
      moveUpMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          final DictionaryConfig dictionaryConfig = dictionaries.remove(adapterContextMenuInfo.position);
          dictionaries.add(adapterContextMenuInfo.position - 1, dictionaryConfig);
          dictionaryConfigsChanged();
          return true;
        }
      });
    }

    final MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
    deleteMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        dictionaries.remove(adapterContextMenuInfo.position);
        dictionaryConfigsChanged();
        return true;
      }
    });

  }

  private void dictionaryConfigsChanged() {
    PersistentObjectCache.getInstance().write(DICTIONARY_CONFIGS, dictionaries);
    setListAdapter(getListAdapter());
  }

  static final OnFocusChangeListener focusListener = new OnFocusChangeListener() {
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      final TextView textView = (TextView) v;
      if (hasFocus) {
        textView.setTextAppearance(v.getContext(), R.style.Theme_QuickDic);
      } else {
        //textView.setTextAppearance(v.getContext(), android.R.style.TextAppearance_Medium);
      }
    }
  };


  class Adapter extends BaseAdapter {

    @Override
    public int getCount() {
      return dictionaries.size();
    }

    @Override
    public DictionaryConfig getItem(int position) {
      return dictionaries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final DictionaryConfig dictionaryConfig = getItem(position);
      final TableLayout tableLayout = new TableLayout(parent.getContext());
      final TextView view = new TextView(parent.getContext());

      view.setText(dictionaryConfig.name);
      view.setTextSize(20);
      view.setFocusable(true);
      view.setOnFocusChangeListener(focusListener);
      tableLayout.addView(view);

      final EditText view2 = new EditText(parent.getContext());
      view2.setText(dictionaryConfig.name + "2");
      view2.setFocusable(true);
      view2.setOnFocusChangeListener(focusListener);
      tableLayout.addView(view2);

      return tableLayout;
    }
    
  }


}
