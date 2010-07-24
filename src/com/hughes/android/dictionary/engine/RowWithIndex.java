package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;

public abstract class RowWithIndex implements Row {
  final Dictionary.Index index;
  int thisRowIndex;
  int referenceIndex;

  TokenRow tokenRow = null;
  
  RowWithIndex(final RandomAccessFile raf, final int thisRowIndex, final Dictionary.Index index) throws IOException {
    this.index = index;
    this.thisRowIndex = thisRowIndex;
    this.referenceIndex = raf.readInt();
  }

  @Override
  public void write(RandomAccessFile raf) throws IOException {
    raf.writeInt(referenceIndex);
  }
  
  @Override
  public TokenRow getTokenRow(final boolean search) {
    if (tokenRow == null && search) {
      int r = thisRowIndex - 1;
      while (r >= 0) {
        final Row row = index.rows.get(r);
        final TokenRow candidate = row.getTokenRow(false);
        if (candidate != null) {
          for (++r; r <= thisRowIndex; ++r) {
            index.rows.get(r).setTokenRow(candidate);
          }
        }
      }
      assert tokenRow != null;
    }
    return tokenRow;
  }
  
  @Override
  public void setTokenRow(TokenRow tokenRow) {
    assert this.tokenRow == null;
    assert tokenRow != null;
    this.tokenRow = tokenRow;
  }


}
