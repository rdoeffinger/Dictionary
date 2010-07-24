package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;

public class TokenRow extends RowWithIndex {
  
  TokenRow(final RandomAccessFile raf, final int thisRowIndex, final Index index) throws IOException {
    super(raf, thisRowIndex, index);
  }

  @Override
  public TokenRow getTokenRow(final boolean search) {
    return this;
  }

  @Override
  public void setTokenRow(TokenRow tokenRow) {
    throw new RuntimeException("Shouldn't be setting TokenRow!");
  }
  
  public String getToken() {
    return index.sortedIndexEntries.get(referenceIndex).token;
  }


}
