package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFListSerializer;

public abstract class RowBase {
  /**
   * the Index owning this RowBase.
   */
  final Index index;
  
  /**
   * Where this row lives within the list of Rows.
   */
  int thisRowIndex;
  
  /**
   * Where this RowBase points to.
   */
  int referenceIndex;

  /**
   * the TokenRow above this RowBase, populated on demand.
   */
  TokenRow tokenRow = null;
  
  RowBase(final RandomAccessFile raf, final int thisRowIndex, final Index index) throws IOException {
    this.index = index;
    this.thisRowIndex = thisRowIndex;  // where this was inside the list.
    this.referenceIndex = raf.readInt();  // what this points to.
  }

  public void write(RandomAccessFile raf) throws IOException {
    raf.writeInt(referenceIndex);
  }
  
  /**
   * @return the TokenRow that this row is "filed under".
   */
  public TokenRow getTokenRow(final boolean search) {
    if (tokenRow == null && search) {
      int r = thisRowIndex - 1;
      while (r >= 0) {
        final RowBase row = index.rows.get(r);
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
  
  public void setTokenRow(TokenRow tokenRow) {
    assert this.tokenRow == null;
    assert tokenRow != null;
    this.tokenRow = tokenRow;
  }

  
  public abstract Object draw(final String searchText);


  // RowBase must manage "disk-based" polymorphism.  All other polymorphism is
  // dealt with in the normal manner.
  static class Serializer implements RAFListSerializer<RowBase> {
    
    final Index index;
    
    Serializer(final Index index) {
      this.index = index;
    }

    @Override
    public RowBase read(RandomAccessFile raf, final int listIndex) throws IOException {
      final byte rowType = raf.readByte();
      if (rowType == 0) {
        return new PairEntry.Row(raf, listIndex, index);
      } else if (rowType == 1) {
        return new TokenRow(raf, listIndex, index);
      }
      throw new RuntimeException("Invalid rowType:" + rowType);
    }

    @Override
    public void write(RandomAccessFile raf, RowBase t) throws IOException {
      if (t instanceof PairEntry.Row) {
        raf.writeByte(0);
      } else if (t instanceof TokenRow) {
        raf.writeByte(1);
      }
      t.write(raf);
    }
  };


}
