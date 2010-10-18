package com.hughes.android.dictionary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class QuickDicConfig implements Serializable {
  
  private static final long serialVersionUID = 6711617368780900979L;
  
  final List<DictionaryConfig> dictionaryConfigs = new ArrayList<DictionaryConfig>();
  
  public QuickDicConfig() {
    dictionaryConfigs.add(DictionaryConfig.defaultConfig());
  }

}
