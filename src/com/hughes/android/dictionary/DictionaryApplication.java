// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.hughes.android.dictionary;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Language;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.PersistentObjectCache;
import com.hughes.util.ListUtil;
import com.ibm.icu.text.Collator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DictionaryApplication extends Application {
  
  static final String LOG = "QuickDicApp";
  
  // Static, determined by resources (and locale).
  // Unordered.
  static Map<String,DictionaryInfo> DOWNLOADABLE_NAME_TO_INFO = null;
  
  static final class DictionaryConfig implements Serializable {
    private static final long serialVersionUID = -1444177164708201263L;
    // User-ordered list, persisted, just the ones that are/have been present.
    final List<String> dictionaryFilesOrdered = new ArrayList<String>();
    final Map<String, DictionaryInfo> dictionaryInfoCache = new LinkedHashMap<String, DictionaryInfo>();
  }
  DictionaryConfig dictionaryConfig = null;

  static final class DictionaryHistory implements Serializable {
    private static final long serialVersionUID = -4842995032541390284L;
    // User-ordered list, persisted, just the ones that are/have been present.
    final List<DictionaryLink> dictionaryLinks = new ArrayList<DictionaryLink>();
  }
  DictionaryHistory dictionaryHistory = null;
  
  private File dictDir;

  static synchronized void staticInit(final Context context) {
    if (DOWNLOADABLE_NAME_TO_INFO != null) {
      return;
    }
    DOWNLOADABLE_NAME_TO_INFO = new LinkedHashMap<String,DictionaryInfo>();
    final BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.dictionary_info)));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.length() == 0) {
          continue;
        }
        final DictionaryInfo dictionaryInfo = new DictionaryInfo(line);
        DOWNLOADABLE_NAME_TO_INFO.put(dictionaryInfo.uncompressedFilename, dictionaryInfo);
      }
      reader.close();
    } catch (IOException e) {
      Log.e(LOG, "Failed to load downloadable dictionary lists.", e);
    }
  }

  
  @Override
  public void onCreate() {
    super.onCreate();
    Log.d("QuickDic", "Application: onCreate");
    TransliteratorManager.init(null);
    staticInit(getApplicationContext());
    
    // Load the dictionaries we know about.
    dictionaryConfig = PersistentObjectCache.init(getApplicationContext()).read(C.DICTIONARY_CONFIGS, DictionaryConfig.class);
    if (dictionaryConfig == null) {
      dictionaryConfig = new DictionaryConfig();
    }
    
    // Theme stuff.
    setTheme(getSelectedTheme().themeId);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
      @Override
      public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
          String key) {
        Log.d("QuickDic", "prefs changed: " + key);
        if (key.equals(getString(R.string.themeKey))) {
          setTheme(getSelectedTheme().themeId);
        }
      }
    });
  }
  
  public void onCreateGlobalOptionsMenu(
      final Context context, final Menu menu) {
    final MenuItem about = menu.add(getString(R.string.about));
    about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class
            .getPackage().getName(), AboutActivity.class.getCanonicalName());
        context.startActivity(intent);
        return false;
      }
    });

    final MenuItem help = menu.add(getString(R.string.help));
    help.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        context.startActivity(HtmlDisplayActivity.getHelpLaunchIntent());
        return false;
      }
    });

    final MenuItem preferences = menu.add(getString(R.string.preferences));
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        PreferenceActivity.prefsMightHaveChanged = true;
        final Intent intent = new Intent().setClassName(PreferenceActivity.class
            .getPackage().getName(), PreferenceActivity.class.getCanonicalName());
        context.startActivity(intent);
        return false;
      }
    });
    
    
    final MenuItem reportIssue = menu.add(getString(R.string.reportIssue));
    reportIssue.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://code.google.com/p/quickdic-dictionary/issues/entry"));
        context.startActivity(intent);
        return false;
      }
    });
  }
  
  public synchronized File getDictDir() {
    // This metaphore doesn't work, because we've already reset prefsMightHaveChanged.
//    if (dictDir == null || PreferenceActivity.prefsMightHaveChanged) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      final String dir = prefs.getString(getString(R.string.quickdicDirectoryKey), getString(R.string.quickdicDirectoryDefault));
      dictDir = new File(dir);
      dictDir.mkdirs();
//    }
    return dictDir;
  }
  
  public C.Theme getSelectedTheme() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final String theme = prefs.getString(getString(R.string.themeKey), "themeLight");
    if (theme.equals("themeLight")) {
      return C.Theme.LIGHT;
    } else {
      return C.Theme.DEFAULT;
    }
  }
    
  public File getPath(String uncompressedFilename) {
    return new File(getDictDir(), uncompressedFilename);
  }
  

  String defaultLangISO2 = Locale.getDefault().getLanguage().toLowerCase();
  String defaultLangName = null;
  final Map<String, String> fileToNameCache = new LinkedHashMap<String, String>();

  public String getLanguageName(final String isoCode) {
    final Language.LanguageResources languageResources = Language.isoCodeToResources.get(isoCode); 
    final String lang = languageResources != null ? getApplicationContext().getString(languageResources.nameId) : isoCode;
    return lang;
  }
  

  public synchronized String getDictionaryName(final String uncompressedFilename) {
    final String currentLocale = Locale.getDefault().getLanguage().toLowerCase(); 
    if (!currentLocale.equals(defaultLangISO2)) {
      defaultLangISO2 = currentLocale;
      fileToNameCache.clear();
      defaultLangName = null;
    }
    if (defaultLangName == null) {
      defaultLangName = getLanguageName(defaultLangISO2);
    }
    
    String name = fileToNameCache.get(uncompressedFilename);
    if (name != null) {
      return name;
    }
    
    final DictionaryInfo dictionaryInfo = DOWNLOADABLE_NAME_TO_INFO.get(uncompressedFilename);
    if (dictionaryInfo != null) {
      final StringBuilder nameBuilder = new StringBuilder();

      // Hack to put the default locale first in the name.
      boolean swapped = false;
      if (dictionaryInfo.indexInfos.size() > 1 && 
          dictionaryInfo.indexInfos.get(1).shortName.toLowerCase().equals(defaultLangISO2)) {
        ListUtil.swap(dictionaryInfo.indexInfos, 0, 1);
        swapped = true;
      }
      for (int i = 0; i < dictionaryInfo.indexInfos.size(); ++i) {
        if (i > 0) {
          nameBuilder.append("-");
        }
        nameBuilder.append(getLanguageName(dictionaryInfo.indexInfos.get(i).shortName));
      }
      if (swapped) {
        ListUtil.swap(dictionaryInfo.indexInfos, 0, 1);
      }
      name = nameBuilder.toString();
    } else {
      name = uncompressedFilename.replace(".quickdic", "");
    }
    fileToNameCache.put(uncompressedFilename, name);
    return name;
  }

  public synchronized void moveDictionaryToTop(final DictionaryInfo dictionaryInfo) {
    dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename);
    dictionaryConfig.dictionaryFilesOrdered.add(0, dictionaryInfo.uncompressedFilename);
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
  }

  public synchronized void deleteDictionary(final DictionaryInfo dictionaryInfo) {
    while (dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename)) {};
    dictionaryConfig.dictionaryInfoCache.remove(dictionaryInfo.uncompressedFilename);
    getPath(dictionaryInfo.uncompressedFilename).delete();
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
  }

  final Collator collator = Collator.getInstance();
  final Comparator<String> uncompressedFilenameComparator = new Comparator<String>() {
    @Override
    public int compare(String uncompressedFilename1, String uncompressedFilename2) {
      final String name1 = getDictionaryName(uncompressedFilename1);
      final String name2 = getDictionaryName(uncompressedFilename2);
      if (defaultLangName.length() > 0) {
        if (name1.startsWith(defaultLangName) && !name2.startsWith(defaultLangName)) {
          return -1;
        } else if (name2.startsWith(defaultLangName) && !name1.startsWith(defaultLangName)) {
          return 1;
        }
      }
      return collator.compare(name1, name2);
    }
  };
  final Comparator<DictionaryInfo> dictionaryInfoComparator = new Comparator<DictionaryInfo>() {
    @Override
    public int compare(DictionaryInfo d1, DictionaryInfo d2) {
      // Single-index dictionaries first.
      if (d1.indexInfos.size() != d2.indexInfos.size()) {
          return d1.indexInfos.size() - d2.indexInfos.size();
      }
      return uncompressedFilenameComparator.compare(d1.uncompressedFilename, d2.uncompressedFilename);
    }
  };
  
  public void backgroundUpdateDictionaries(final Runnable onUpdateFinished) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final DictionaryConfig oldDictionaryConfig = new DictionaryConfig();
        synchronized(this) {
          oldDictionaryConfig.dictionaryFilesOrdered.addAll(dictionaryConfig.dictionaryFilesOrdered);
        }
        final DictionaryConfig newDictionaryConfig = new DictionaryConfig();
        for (final String uncompressedFilename : oldDictionaryConfig.dictionaryFilesOrdered) {
          final File dictFile = getPath(uncompressedFilename);
          final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(dictFile);
          if (dictionaryInfo != null) {
            newDictionaryConfig.dictionaryFilesOrdered.add(uncompressedFilename);
            newDictionaryConfig.dictionaryInfoCache.put(uncompressedFilename, dictionaryInfo);
          }
        }
        
        // Are there dictionaries on the device that we didn't know about already?
        // Pick them up and put them at the end of the list.
        final List<String> toAddSorted = new ArrayList<String>();
        final File[] dictDirFiles = getDictDir().listFiles();
        if (dictDirFiles != null) {
          for (final File file : dictDirFiles) {
            if (file.getName().endsWith(".zip")) {
              if (DOWNLOADABLE_NAME_TO_INFO.containsKey(file.getName().replace(".zip", ""))) {
                file.delete();
              }
            }
            if (!file.getName().endsWith(".quickdic")) {
              continue;
            }
            if (newDictionaryConfig.dictionaryInfoCache.containsKey(file.getName())) {
              // We have it in our list already.
              continue;
            }
            final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(file);
            if (dictionaryInfo == null) {
              Log.e(LOG, "Unable to parse dictionary: " + file.getPath());
              continue;
            }
            
            toAddSorted.add(file.getName());
            newDictionaryConfig.dictionaryInfoCache.put(file.getName(), dictionaryInfo);
          }
        } else {
          Log.w(LOG, "dictDir is not a diretory: " + getDictDir().getPath());
        }
        if (!toAddSorted.isEmpty()) {
          Collections.sort(toAddSorted, uncompressedFilenameComparator);
          newDictionaryConfig.dictionaryFilesOrdered.addAll(toAddSorted);
        }

        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, newDictionaryConfig);
        synchronized (this) {
          dictionaryConfig = newDictionaryConfig;
        }
        
        try {
          onUpdateFinished.run();
        } catch (Exception e) {
          Log.e(LOG, "Exception running callback.", e);
        }
      }}).start();
  }

  public synchronized List<DictionaryInfo> getUsableDicts() {
    final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(dictionaryConfig.dictionaryFilesOrdered.size());
    for (final String uncompressedFilename : dictionaryConfig.dictionaryFilesOrdered) {
      final DictionaryInfo dictionaryInfo = dictionaryConfig.dictionaryInfoCache.get(uncompressedFilename);
      if (dictionaryInfo != null) {
        result.add(dictionaryInfo);
      }
    }
    return result;
  }

  public synchronized List<DictionaryInfo> getAllDictionaries() {
    final List<DictionaryInfo> result = getUsableDicts();
    
    // The downloadable ones.
    final Map<String,DictionaryInfo> remaining = new LinkedHashMap<String, DictionaryInfo>(DOWNLOADABLE_NAME_TO_INFO);
    remaining.keySet().removeAll(dictionaryConfig.dictionaryFilesOrdered);
    final List<DictionaryInfo> toAddSorted = new ArrayList<DictionaryInfo>(remaining.values());
    Collections.sort(toAddSorted, dictionaryInfoComparator);
    result.addAll(toAddSorted);
    
    return result;
  }

  public synchronized boolean isDictionaryOnDevice(String uncompressedFilename) {
    return dictionaryConfig.dictionaryInfoCache.get(uncompressedFilename) != null;
  }

  public boolean updateAvailable(final DictionaryInfo dictionaryInfo) {
    final DictionaryInfo downloadable = DOWNLOADABLE_NAME_TO_INFO.get(dictionaryInfo.uncompressedFilename);
    return downloadable != null && downloadable.creationMillis > dictionaryInfo.creationMillis;
  }

  public DictionaryInfo getDownloadable(final String uncompressedFilename) {
    final DictionaryInfo downloadable = DOWNLOADABLE_NAME_TO_INFO.get(uncompressedFilename);
    return downloadable;
  }

}
