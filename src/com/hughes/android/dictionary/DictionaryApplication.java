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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Language;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.PersistentObjectCache;
import com.ibm.icu.text.Collator;

public class DictionaryApplication extends Application {
  
  static final String LOG = "QuickDicApp";
  
  private static final File DICT_DIR = new File(Environment.getExternalStorageDirectory().getName(), "quickdic");

  // Static, determined by resources (and locale).
  // Unordered.
  static Map<String,DictionaryInfo> DOWNLOADABLE_NAME_TO_INFO = null;
  
  static final class DictionaryConfig implements Serializable {
    private static final long serialVersionUID = -1444177164708201260L;
    // User-ordered list, persisted, just the ones that are/have been present.
    final List<String> dictionaryFiles = new ArrayList<String>();
  }
  DictionaryConfig dictionaryConfig = null;
    
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
        Log.d("THAD", "prefs changed: " + key);
        if (key.equals(getString(R.string.themeKey))) {
          setTheme(getSelectedTheme().themeId);
        }
      }
    });
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

  private File getPath(String uncompressedFilename) {
    return new File(DICT_DIR, uncompressedFilename);
  }


  public List<DictionaryInfo> getUsableDicts() {
    final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(dictionaryConfig.dictionaryFiles.size());
    for (final String uncompressedFilename : dictionaryConfig.dictionaryFiles) {
      final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(getPath(uncompressedFilename));
      if (dictionaryInfo != null) {
        result.add(dictionaryInfo);
      }
    }
    return result;
  }

  final Map<String, String> fileToNameCache = new LinkedHashMap<String, String>();
  public synchronized String getDictionaryName(final String uncompressedFilename) {
    String name = fileToNameCache.get(uncompressedFilename);
    if (name != null) {
      return name;
    }
    
    final DictionaryInfo dictionaryInfo = DOWNLOADABLE_NAME_TO_INFO.get(uncompressedFilename);
    final Context context = getApplicationContext();
    if (dictionaryInfo != null) {
      final StringBuilder nameBuilder = new StringBuilder();
      for (int i = 0; i < dictionaryInfo.indexInfos.size(); ++i) {
        final Integer langCode = Language.isoCodeToResourceId.get(dictionaryInfo.indexInfos.get(i).shortName);
        final String lang = langCode != null ? context.getString(langCode) : dictionaryInfo.indexInfos.get(i).shortName;
        if (i > 0) {
          nameBuilder.append("-");
        }
        nameBuilder.append(lang);
      }
      name = nameBuilder.toString();
    } else {
      name = uncompressedFilename.replace(".quickdic", "");
    }
    fileToNameCache.put(uncompressedFilename, name);
    return name;
  }

  public void moveDictionaryToTop(final String canonicalPath) {
    dictionaryConfig.dictionaryFiles.remove(canonicalPath);
    dictionaryConfig.dictionaryFiles.add(0, canonicalPath);
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
  }

  public void deleteDictionary(String canonicalPath) {
    while (dictionaryConfig.dictionaryFiles.remove(canonicalPath)) {};
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
  }

  public List<DictionaryInfo> getAllDictionaries() {
    final List<DictionaryInfo> result = getUsableDicts();
    
    // The ones we knew about...
    final Set<String> known = new LinkedHashSet<String>();
    for (final DictionaryInfo usable : result) {
      known.add(usable.uncompressedFilename);
    }
    
    // Are there dictionaries on the device that we didn't know about already?
    // Pick them up and put them at the end of the list.
    boolean foundNew = false;
    final File[] dictDirFiles = DICT_DIR.listFiles();
    for (final File file : dictDirFiles) {
      if (!file.getName().endsWith(".quickdic")) {
        continue;
      }
      if (known.contains(file.getName())) {
        // We have it in our list already.
        continue;
      }
      final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(file);
      if (dictionaryInfo == null) {
        Log.e(LOG, "Unable to parse dictionary: " + file.getPath());
        continue;
      }
      known.add(file.getName());
      foundNew = true;
      dictionaryConfig.dictionaryFiles.add(file.getName());
      result.add(dictionaryInfo);
    }
    if (foundNew) {
      PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    // The downloadable ones.
    final Map<String,DictionaryInfo> remaining = new LinkedHashMap<String, DictionaryInfo>(DOWNLOADABLE_NAME_TO_INFO);
    remaining.keySet().removeAll(known);
    final List<DictionaryInfo> remainingSorted = new ArrayList<DictionaryInfo>(remaining.values());
    final Collator collator = Collator.getInstance();
    Collections.sort(remainingSorted, new Comparator<DictionaryInfo>() {
      @Override
      public int compare(DictionaryInfo object1, DictionaryInfo object2) {
        return collator.compare(getDictionaryName(object1.uncompressedFilename), getDictionaryName(object2.uncompressedFilename));
      }
    });

    result.addAll(remainingSorted);
    return result;
  }

  public boolean isDictionaryOnDevice(String uncompressedFilename) {
    return getPath(uncompressedFilename).canRead();
  }

  
}
