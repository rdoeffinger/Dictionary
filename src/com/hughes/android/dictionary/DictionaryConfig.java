package com.hughes.android.dictionary;

import java.io.Serializable;

public class DictionaryConfig implements Serializable {
  
  private static final long serialVersionUID = -6850863377577700387L;

  String name = "";
  String localFile = "/sdcard/quickDic/";
  String downloadUrl = "http://";
  
  int openIndex = 0;
  String openWord = "";
  
}
