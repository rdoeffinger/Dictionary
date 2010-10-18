package com.hughes.android.dictionary;

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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.hughes.android.util.PersistentObjectCache;

public class DictionaryListActivity extends ListActivity {

  static final String LOG = "QuickDic";
  
  QuickDicConfig quickDicConfig = new QuickDicConfig();
  
  
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);

    // UI init.
    setContentView(R.layout.list_activity);

    /*
    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int row,
          long arg3) {
        return false;
      }
    });
    */

    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int index,
          long id) {
        onClick(index);
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());
    
  }
  
  private void onClick(int dictIndex) {
    final Intent intent = DictionaryActivity.getIntent(dictIndex, 0, "");
    startActivity(intent);
  }
  
  @Override
  protected void onResume() {
    super.onResume();

    quickDicConfig = PersistentObjectCache.init(this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);
    if (quickDicConfig == null) {
      quickDicConfig = new QuickDicConfig();
      PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
    }

    setListAdapter(new Adapter());
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem newDictionaryMenuItem = menu.add(R.string.addDictionary);
    newDictionaryMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
          public boolean onMenuItemClick(final MenuItem menuItem) {
            final DictionaryConfig dictionaryConfig = new DictionaryConfig();
            dictionaryConfig.name = getString(R.string.newDictionary);
            quickDicConfig.dictionaryConfigs.add(0, dictionaryConfig);
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
        startActivity(DictionaryEditActivity.getIntent(adapterContextMenuInfo.position));
        return true;
      }
    });

    if (adapterContextMenuInfo.position > 0) {
      final MenuItem moveUpMenuItem = menu.add(R.string.moveUp);
      moveUpMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          final DictionaryConfig dictionaryConfig = quickDicConfig.dictionaryConfigs.remove(adapterContextMenuInfo.position);
          quickDicConfig.dictionaryConfigs.add(adapterContextMenuInfo.position - 1, dictionaryConfig);
          dictionaryConfigsChanged();
          return true;
        }
      });
    }

    final MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
    deleteMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        quickDicConfig.dictionaryConfigs.remove(adapterContextMenuInfo.position);
        dictionaryConfigsChanged();
        return true;
      }
    });

  }

  private void dictionaryConfigsChanged() {
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
    setListAdapter(getListAdapter());
  }

  class Adapter extends BaseAdapter {

    @Override
    public int getCount() {
      return quickDicConfig.dictionaryConfigs.size();
    }

    @Override
    public DictionaryConfig getItem(int position) {
      return quickDicConfig.dictionaryConfigs.get(position);
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
      tableLayout.addView(view);

      return tableLayout;
    }
    
  }


}
