package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;

public interface Entry extends RAFSerializable<Entry> {
  
  public static final RAFFactory<Entry> RAF_FACTORY = new RAFFactory<Entry>() {
    public Entry create(RandomAccessFile raf) throws IOException {
      final byte type = raf.readByte();
      switch (type) {
      case 0:
        return SimpleEntry.RAF_FACTORY.create(raf);
      }
      throw new RuntimeException("Invalid entry type: " + type);
    }};
    
  
}
