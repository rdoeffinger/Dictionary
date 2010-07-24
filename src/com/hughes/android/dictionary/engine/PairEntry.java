package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public class PairEntry extends Entry {

  @Override
  List<String> getMainTokens() {
    return null;
  }

  @Override
  List<String> getOtherTokens() {
    return null;
  }

  
  public static class Row extends RowWithIndex {
    Row(final RandomAccessFile raf, final int thisRowIndex, final Dictionary.Index index) throws IOException {
      super(raf, thisRowIndex, index);
    }
    public PairEntry getEntry() {
      return index.getDict().pairEntries.get(referenceIndex);
    }
  }

}
