package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import com.hughes.util.raf.RAFSerializable;

public class Dictionary {
  
  // persisted
  List<PairEntry> pairEntries;
  
  // persisted
  List<EntrySource> sources;

  // --------------------------------------------------------------------------
  
  final class Index {
    // One big list!
    // Various sub-types.
    // persisted
    List<Row> rows;
    
    // persisted
    List<IndexEntry> sortedIndexEntries;
    
    Dictionary getDict() {
      return Dictionary.this;
    }
  }

  static final class IndexEntry implements RAFSerializable<IndexEntry> {
    String token;
    int startRow;
    
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeUTF(token);
      raf.write(startRow);
    }
  }

  
}