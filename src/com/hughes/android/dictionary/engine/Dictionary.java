package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.hughes.util.CachingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.RAFSerializable;


public class Dictionary implements RAFSerializable<Dictionary> {
  
  static final int CACHE_SIZE = 5000;
  
  static final String END_OF_DICTIONARY = "END OF DICTIONARY";
  
  // persisted
  final int dictFileVersion;
  public final String dictInfo;
  public final List<PairEntry> pairEntries;
  public final List<TextEntry> textEntries;
  public final List<EntrySource> sources;
  public final List<Index> indices;
  
  public Dictionary(final String dictInfo) {
    this.dictFileVersion = 0;
    this.dictInfo = dictInfo;
    pairEntries = new ArrayList<PairEntry>();
    textEntries = new ArrayList<TextEntry>();
    sources = new ArrayList<EntrySource>();
    indices = new ArrayList<Index>();
  }

  public Dictionary(final RandomAccessFile raf) throws IOException {
    dictFileVersion = raf.readInt();
    if (dictFileVersion != 0) {
      throw new IOException("Invalid dictionary version: " + dictFileVersion);
    }
    dictInfo = raf.readUTF();
    sources = CachingList.createFullyCached(RAFList.create(raf, EntrySource.SERIALIZER, raf.getFilePointer()));
    pairEntries = CachingList.create(RAFList.create(raf, PairEntry.SERIALIZER, raf.getFilePointer()), CACHE_SIZE);
    textEntries = CachingList.create(RAFList.create(raf, TextEntry.SERIALIZER, raf.getFilePointer()), CACHE_SIZE);
    indices = CachingList.createFullyCached(RAFList.create(raf, indexSerializer, raf.getFilePointer()));
    final String end = raf.readUTF(); 
    if (!end.equals(END_OF_DICTIONARY)) {
      throw new IOException("Dictionary seems corrupt: " + end);
    }
  }
  
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    raf.writeInt(dictFileVersion);
    raf.writeUTF(dictInfo);
    RAFList.write(raf, sources, EntrySource.SERIALIZER);
    RAFList.write(raf, pairEntries, PairEntry.SERIALIZER);
    RAFList.write(raf, textEntries, TextEntry.SERIALIZER);
    RAFList.write(raf, indices, indexSerializer);
    raf.writeUTF(END_OF_DICTIONARY);
  }

  private final RAFListSerializer<Index> indexSerializer = new RAFListSerializer<Index>() {
    @Override
    public Index read(RandomAccessFile raf, final int readIndex) throws IOException {
      return new Index(Dictionary.this, raf);
    }
    @Override
    public void write(RandomAccessFile raf, Index t) throws IOException {
      t.write(raf);
    }};
    
    public void print(final PrintStream out) {
      out.println("dictInfo=" + dictInfo);
      for (final Index index : indices) {
        index.print(out);
        out.println();
      }
    }


}