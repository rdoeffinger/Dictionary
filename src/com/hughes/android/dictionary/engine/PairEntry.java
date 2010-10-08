package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;

public class PairEntry extends Entry implements RAFSerializable<PairEntry> {
  
  public static final class Pair {
    final String lang1;
    final String lang2;
    public Pair(final String lang1, final String lang2) {
      this.lang1 = lang1;
      this.lang2 = lang2;
    }
    public String toString() {
      return lang1 + "\t" + lang2;
    }
  }
  
  final Pair[] pairs;
  
  public PairEntry(final Pair[] pairs) {
    this.pairs = pairs;
  }
  
  public PairEntry(final RandomAccessFile raf) throws IOException {
    pairs = new Pair[raf.readInt()];
    for (int i = 0; i < pairs.length; ++i) {
      pairs[i] = new Pair(raf.readUTF(), raf.readUTF());
    }
  }
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    // TODO: this couls be a short.
    raf.writeInt(pairs.length);
    for (int i = 0; i < pairs.length; ++i) {
      raf.writeUTF(pairs[i].lang1);
      raf.writeUTF(pairs[i].lang2);
    }
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
  

  public static class Row extends RowBase {
    
    Row(final RandomAccessFile raf, final int thisRowIndex,
        final Index index) throws IOException {
      super(raf, thisRowIndex, index);
    }

    Row(final int referenceIndex, final int thisRowIndex,
        final Index index) {
      super(referenceIndex, thisRowIndex, index);
    }

    public PairEntry getEntry() {
      return index.dict.pairEntries.get(referenceIndex);
    }
    
    @Override
    public Object draw(String searchText) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void print(PrintStream out) {
      final PairEntry pairEntry = getEntry();
      for (int i = 0; i < pairEntry.pairs.length; ++i) {
        out.println((i == 0 ? "  " : "    ") + pairEntry.pairs[i]);
      }
    }
  }



}
