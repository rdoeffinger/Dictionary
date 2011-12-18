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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.hughes.android.dictionary.engine.Language;

public final class QuickDicConfig implements Serializable {
  
  private static final long serialVersionUID = 6711617368780900979L;
  
  // Just increment this to have them all update...
  static final int LATEST_VERSION = 3;
  
  final List<DictionaryConfig> dictionaryConfigs = new ArrayList<DictionaryConfig>();
  int currentVersion = LATEST_VERSION;
  
  public QuickDicConfig() {
    addDefaultDictionaries();
  }

  public void addDefaultDictionaries() {
    {
      final DictionaryConfig config = new DictionaryConfig();
      config.name = "German<->English";
      config.downloadUrl = "https://sites.google.com/site/quickdic/dictionaries-1/DE-EN_chemnitz_enwiktionary.quickdic.zip?attredirects=0&d=1";
      config.localFile = "/sdcard/quickDic/DE-EN_chemnitz_enwiktionary.quickdic";
      addOrReplace(config);
    }
    
    for (final String iso : Language.isoCodeToWikiName.keySet()) {
      if (iso.equals("EN") || iso.equals("DE")) {
        continue;
      }
      final DictionaryConfig config = new DictionaryConfig();
      config.name = String.format("EN<->%s English<->%s (Wiktionary)", iso, Language.isoCodeToWikiName.get(iso));
      config.downloadUrl = String.format("https://sites.google.com/site/quickdic/dictionaries-1/EN-%s_enwiktionary.quickdic.zip?attredirects=0&d=1", iso);
      config.localFile = String.format("/sdcard/quickDic/EN-%s_enwiktionary.quickdic", iso);
      addOrReplace(config);
    }

  }

  private void addOrReplace(final DictionaryConfig dictionaryConfig) {
    for (int i = 0; i < dictionaryConfigs.size(); ++i) {
      if (dictionaryConfigs.get(i).name.equals(dictionaryConfig.name)) {
        dictionaryConfigs.set(i, dictionaryConfig);
        return;
      }
    }
    dictionaryConfigs.add(dictionaryConfig);
  }

}