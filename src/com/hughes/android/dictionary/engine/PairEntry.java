package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;

public class PairEntry extends Entry implements RAFSerializable<PairEntry>, Comparable<PairEntry> {
  
  public static final class Pair {
    
    public final String lang1;
    public final String lang2;
    
    public Pair(final String lang1, final String lang2) {
      this.lang1 = lang1;
      this.lang2 = lang2;
    }

    public Pair(final String lang1, final String lang2, final boolean swap) {
      this(swap ? lang2 : lang1, swap ? lang1 : lang2);
    }

    public String toString() {
      return lang1 + " :: " + lang2;
    }

    public String toStringTab() {
      return lang1 + "\t" + lang2;
    }

    public String get(int i) {
      if (i == 0) {
        return lang1;
      } else if (i == 1) {
        return lang2;
      }
      throw new IllegalArgumentException();
    }

  }
  
  public final Pair[] pairs;
  
  public PairEntry(final Pair[] pairs) {
    this.pairs = pairs;
  }
  
  public PairEntry(final String lang1, final String lang2) {
    this.pairs = new Pair[] { new Pair(lang1, lang2) };
  }
  
  public PairEntry(final RandomAccessFile raf) throws IOException {
    pairs = new Pair[raf.readInt()];
    for (int i = 0; i < pairs.length; ++i) {
      pairs[i] = new Pair(raf.readUTF(), raf.readUTF());
    }
  }
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    // TODO: this could be a short.
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
    public void print(PrintStream out) {
      final PairEntry pairEntry = getEntry();
      for (int i = 0; i < pairEntry.pairs.length; ++i) {
        out.print((i == 0 ? "  " : "    ") + pairEntry.pairs[i]);
        out.println();
      }
    }

    @Override
    public String getRawText(boolean compact) {
      final PairEntry pairEntry = getEntry();
      return pairEntry.getRawText(compact);
    }
  
  }

  public String getRawText(final boolean compact) {
    if (compact) {
      return this.pairs[0].toStringTab();
    }
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < this.pairs.length; ++i) {
      if (i > 0) {
        builder.append(" | ");
      }
      builder.append(this.pairs[i].lang1);
    }
    builder.append("\t");
    for (int i = 0; i < this.pairs.length; ++i) {
      if (i > 0) {
        builder.append(" | ");
      }
      builder.append(this.pairs[i].lang2);
    }
    return builder.toString();
  }

  @Override
  public int compareTo(final PairEntry that) {
    return this.getRawText(false).compareTo(that.getRawText(false));
  }
  
  @Override
  public String toString() {
    return getRawText(false);
  }
  

}
