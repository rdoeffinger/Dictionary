package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFListSerializer;

public interface Row {
  
  public void write(RandomAccessFile raf) throws IOException;
  
  /**
   * @return the TokenRow that this row is "filed under".
   */
  public TokenRow getTokenRow(final boolean search);
  
  public void setTokenRow(final TokenRow tokenRow);


  // Row must manage "disk-based" polymorphism.  All other polymorphism is
  // dealt with in the normal manner.
  static class Serializer implements RAFListSerializer<Row> {
    
    final Dictionary.Index index;
    
    Serializer(final Dictionary.Index index) {
      this.index = index;
    }

    @Override
    public Row read(RandomAccessFile raf, final int listIndex) throws IOException {
      final byte rowType = raf.readByte();
      if (rowType == 0) {
        return new PairEntry.Row(raf, listIndex, index);
      } else if (rowType == 1) {
        return new TokenRow(raf, listIndex, index);
      }
      throw new RuntimeException("Invalid rowType:" + rowType);
    }

    @Override
    public void write(RandomAccessFile raf, Row t) throws IOException {
      if (t instanceof PairEntry.Row) {
        raf.writeByte(0);
      } else if (t instanceof TokenRow) {
        raf.writeByte(1);
      }
    }
  };
}
