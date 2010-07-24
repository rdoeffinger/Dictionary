/**
 * 
 */
package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import com.hughes.android.dictionary.engine.Dictionary.Index.IndexEntry;
import com.hughes.util.raf.RAFSerializable;

final class Index {
  final Dictionary dict; 
  final String name;
  
  // One big list!
  // Various sub-types.
  // persisted
  final List<Row> rows;
  
  // persisted
  final List<Index.IndexEntry> sortedIndexEntries;

  static final class IndexEntry implements RAFSerializable<Index.IndexEntry> {
    String token;
    int startRow;
    
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeUTF(token);
      raf.write(startRow);
    }
  }
}