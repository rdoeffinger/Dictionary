package com.hughes.android.dictionary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class QuickDicConfig implements Serializable {
  
  private static final long serialVersionUID = 6711617368780900979L;
  
  static final int LATEST_VERSION = 1;
  
  final List<DictionaryConfig> dictionaryConfigs = new ArrayList<DictionaryConfig>();
  int currentVersion = LATEST_VERSION;
  
  public QuickDicConfig() {
    addDefaultDictionaries();
  }

  public void addDefaultDictionaries() {
    {
      final DictionaryConfig de_en_chemnitz = new DictionaryConfig();
      de_en_chemnitz.name = "DE<->EN (Chemnitz)";
      de_en_chemnitz.downloadUrl = "https://sites.google.com/site/quickdic/dictionaries-1/DE-EN_chemnitz.quickdic.zip?attredirects=0&d=1";
      de_en_chemnitz.localFile = "/sdcard/quickDic/DE-EN_chemnitz.quickdic";
      addOrReplace(de_en_chemnitz);
    }

    {
      final DictionaryConfig en_it_wiktionary = new DictionaryConfig();
      en_it_wiktionary.name = "EN<->IT (EN Wiktionary)";
      en_it_wiktionary.downloadUrl = "https://sites.google.com/site/quickdic/dictionaries-1/EN-IT_enwiktionary.quickdic.zip?attredirects=0&d=1";
      en_it_wiktionary.localFile = "/sdcard/quickDic/EN-IT_enwiktionary.quickdic";
      addOrReplace(en_it_wiktionary);
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
