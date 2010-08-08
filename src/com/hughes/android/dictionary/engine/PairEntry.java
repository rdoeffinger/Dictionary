package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;

public class PairEntry extends Entry implements RAFSerializable<PairEntry> {
  
  public PairEntry(final RandomAccessFile raf) {
  }
  @Override
  public void write(RandomAccessFile raf) throws IOException {
  }
  
  static final RAFSerializer<PairEntry> SERIALIZER = new RAFSerializer<PairEntry>() {
    @Override
    public PairEntry read(RandomAccessFile raf) throws IOException {
      return new PairEntry(raf);
    }

    @Override
    public void write(RandomAccessFile raf, PairEntry t) throws IOException {
      t.write(raf);
    }
  };
  

  @Override
  List<String> getMainTokens() {
    return null;
  }

  @Override
  List<String> getOtherTokens() {
    return null;
  }
  
  

  
  public static class Row extends RowBase {
    
    Row(final RandomAccessFile raf, final int thisRowIndex,
        final Index index) throws IOException {
      super(raf, thisRowIndex, index);
    }
    
    public PairEntry getEntry() {
      return index.dict.pairEntries.get(referenceIndex);
    }
    
    @Override
    public Object draw(String searchText) {
      // TODO Auto-generated method stub
      return null;
    }
  }



}
