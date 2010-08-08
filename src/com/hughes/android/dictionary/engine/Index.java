/**
 * 
 */
package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.List;

import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;
import com.hughes.util.raf.UniformRAFList;

final class Index implements RAFSerializable<Index> {
  final Dictionary dict;
  
  final String shortName;
  final String longName;
    
  // persisted
  final List<Index.IndexEntry> sortedIndexEntries;

  // One big list!
  // Various sub-types.
  // persisted
  final List<RowBase> rows;
  
  // --------------------------------------------------------------------------
  
  public Index(final Dictionary dict, final RandomAccessFile raf) throws IOException {
    this.dict = dict;
    shortName = raf.readUTF();
    longName = raf.readUTF();
    // TODO: caching
    sortedIndexEntries = RAFList.create(raf, IndexEntry.SERIALIZER, raf.getFilePointer());
    rows = UniformRAFList.create(raf, new RowBase.Serializer(this), raf.getFilePointer());
  }
  @Override
  public void write(final RandomAccessFile raf) throws IOException {
    raf.writeUTF(shortName);
    raf.writeUTF(longName);
    RAFList.write(raf, sortedIndexEntries, IndexEntry.SERIALIZER);
    UniformRAFList.write(raf, (Collection<RowBase>) rows, new RowBase.Serializer(this), 5);
  }

  
  static final class IndexEntry implements RAFSerializable<Index.IndexEntry> {
    String token;
    int startRow;
    static final RAFSerializer<IndexEntry> SERIALIZER = new RAFSerializer<IndexEntry> () {
      @Override
      public IndexEntry read(RandomAccessFile raf) throws IOException {
        return new IndexEntry(raf);
      }
      @Override
      public void write(RandomAccessFile raf, IndexEntry t) throws IOException {
        t.write(raf);
      }};
    public IndexEntry(final RandomAccessFile raf) throws IOException {
      token = raf.readUTF();
      startRow = raf.readInt();
    }
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeUTF(token);
      raf.write(startRow);
    }
  }

}