package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFSerializer;


public class Dictionary {
  
  // persisted
  final List<PairEntry> pairEntries;
  
  // persisted
  final List<EntrySource> sources;
  
  // persisted
  final List<Index> indices;
  
  public Dictionary() {
    pairEntries = new ArrayList<PairEntry>();
    sources = new ArrayList<EntrySource>();
    indices = new ArrayList<Index>();
  }

  public Dictionary(final RandomAccessFile raf) throws IOException {
    pairEntries = RAFList.create(raf, PairEntry.SERIALIZER, raf.getFilePointer());
    sources = new ArrayList<EntrySource>();

    final RAFSerializer<Index> indexSerializer = new RAFSerializer<Index>() {

      @Override
      public Index read(RandomAccessFile raf) throws IOException {
        return new Index(Dictionary.this, raf);
      }

      @Override
      public void write(RandomAccessFile raf, Index t) throws IOException {
        t.write(raf);
        
      }};
    indices = RAFList.create(raf, indexSerializer, raf.getFilePointer());
  }
  
}