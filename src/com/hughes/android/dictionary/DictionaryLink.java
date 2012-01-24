package com.hughes.android.dictionary;

import java.io.Serializable;

public class DictionaryLink implements Serializable {
  
  private static final long serialVersionUID = -3842984045642836981L;

  final String uncompressedFilename;
  final int index;
  final String searchText;

  private DictionaryLink(String uncompressedFilename, int index,
      String searchText) {
    this.uncompressedFilename = uncompressedFilename;
    this.index = index;
    this.searchText = searchText;
  }

}
