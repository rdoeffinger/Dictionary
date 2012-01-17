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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Environment;

import com.hughes.android.dictionary.engine.Language;

public final class QuickDicConfig implements Serializable {
  
  private static final long serialVersionUID = 6711617368780900979L;

  final List<DictionaryInfo> dictionaryInfos = new ArrayList<DictionaryInfo>();
  
  public QuickDicConfig(final Context context) {
    addDefaultDictionaries(context);
  }
  
  public void addDefaultDictionaries(final Context context) {
    for (final DictionaryInfo dictionaryInfo : getDefaultDictionaries(context).values()) {
      addOrReplace(dictionaryInfo);
    }
  }
  
  private static Map<String,DictionaryInfo> defaultDictionaries = null;
  public synchronized static Map<String,DictionaryInfo> getDefaultDictionaries(final Context context) {
    if (defaultDictionaries != null) {
      return defaultDictionaries;
    }
    
    defaultDictionaries = new LinkedHashMap<String, DictionaryInfo>();
    
    final BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.dictionary_info)));
    String line;
    try {
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.length() == 0) {
          continue;
        }
        final DictionaryInfo dictionaryInfo = new DictionaryInfo(line);
        String name = "";
        for (int i = 0; i < dictionaryInfo.indexInfos.size(); ++i) {
          final Integer langCode = Language.isoCodeToResourceId.get(dictionaryInfo.indexInfos.get(i).langIso);
          final String lang = langCode != null ? context.getString(langCode) : dictionaryInfo.indexInfos.get(i).langIso;
          if (i > 0) {
            name += "-";
          }
          name += lang;
        }
        dictionaryInfo.name = name;
        dictionaryInfo.localFile = Environment.getExternalStorageDirectory().getName() + "/quickdic/" + dictionaryInfo.uncompressedFilename; 
        defaultDictionaries.put(dictionaryInfo.localFile, dictionaryInfo);
      }
    } catch (IOException e) {
      defaultDictionaries = null;
      return new LinkedHashMap<String, DictionaryInfo>();
    }
    
    return defaultDictionaries;
  }

  private void addOrReplace(final DictionaryInfo dictionaryConfig) {
    for (int i = 0; i < dictionaryInfos.size(); ++i) {
      if (dictionaryInfos.get(i).uncompressedFilename.equals(dictionaryConfig.uncompressedFilename)) {
        dictionaryInfos.set(i, dictionaryConfig);
        return;
      }
    }
    dictionaryInfos.add(dictionaryConfig);
  }

}
