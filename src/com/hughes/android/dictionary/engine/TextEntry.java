package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;

public class TextEntry extends Entry implements RAFSerializable<TextEntry> {
  
  final String text;
  
  public TextEntry(final RandomAccessFile raf) throws IOException {
    text = raf.readUTF();
  }
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    raf.writeUTF(text);
  }
  
  static final RAFSerializer<TextEntry> SERIALIZER = new RAFSerializer<TextEntry>() {
    @Override
    public TextEntry read(RandomAccessFile raf) throws IOException {
      return new TextEntry(raf);
    }

    @Override
    public void write(RandomAccessFile raf, TextEntry t) throws IOException {
      t.write(raf);
    }
  };
  

  public static class Row extends RowBase {
    
    Row(final RandomAccessFile raf, final int thisRowIndex,
        final Index index) throws IOException {
      super(raf, thisRowIndex, index);
    }
    
    public TextEntry getEntry() {
      return index.dict.textEntries.get(referenceIndex);
    }
    
    @Override
    public void print(PrintStream out) {
      out.println("  " + getEntry().text);
    }

    @Override
    public String getRawText(boolean compact) {
      return getEntry().text;
    }
  }



}
