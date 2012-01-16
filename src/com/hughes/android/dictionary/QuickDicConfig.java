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

import android.os.Environment;

import com.hughes.android.dictionary.engine.Language;

public final class QuickDicConfig implements Serializable {
  
  private static final long serialVersionUID = 6711617368780900979L;
  
  // Just increment this to have them all update...
  static final int LATEST_VERSION = 6;
  
  final List<DictionaryInfo> dictionaryConfigs = new ArrayList<DictionaryInfo>();
  int currentVersion = LATEST_VERSION;
  
  public QuickDicConfig() {
    addDefaultDictionaries();
  }
  
  static final String BASE_URL = "http://quickdic-dictionary.googlecode.com/files/";

  // TODO: read this from a resource file....
  
  public void addDefaultDictionaries() {
    {
      final DictionaryInfo config = new DictionaryInfo();
      config.name = "German-English";
      config.downloadUrl = String.format("%sDE-EN_chemnitz_enwiktionary.quickdic.%s.zip", BASE_URL, VERSION_SUFFIX);
      config.localFile = String.format("%s/quickDic/DE-EN_chemnitz_enwiktionary.quickdic", Environment.getExternalStorageDirectory());
      addOrReplace(config);
    }
    
    for (final String iso : Language.isoCodeToResourceName.keySet()) {
      if (iso.equals("EN") || iso.equals("DE")) {
        continue;
      }
      final DictionaryInfo config = new DictionaryInfo();
      config.name = String.format("English-%s", Language.isoCodeToWikiName.get(iso));
      config.downloadUrl = String.format("%sEN-%s_enwiktionary.quickdic.%s.zip", BASE_URL, iso, VERSION_SUFFIX);
      config.localFile = String.format("%s/quickDic/EN-%s_enwiktionary.quickdic", Environment.getExternalStorageDirectory(), iso);
      addOrReplace(config);
    }
  }

  private void addOrReplace(final DictionaryInfo dictionaryConfig) {
    for (int i = 0; i < dictionaryConfigs.size(); ++i) {
      if (dictionaryConfigs.get(i).name.equals(dictionaryConfig.name)) {
        dictionaryConfigs.set(i, dictionaryConfig);
        return;
      }
    }
    dictionaryConfigs.add(dictionaryConfig);
  }

}
