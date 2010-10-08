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
  
  // persisted
  final String dictInfo;
  final List<PairEntry> pairEntries;
  final List<TextEntry> textEntries;
  final List<EntrySource> sources;
  final List<Index> indices;
  
  public Dictionary(final String dictInfo) {
    this.dictInfo = dictInfo;
    pairEntries = new ArrayList<PairEntry>();
    textEntries = new ArrayList<TextEntry>();
    sources = new ArrayList<EntrySource>();
    indices = new ArrayList<Index>();
  }

  public Dictionary(final RandomAccessFile raf) throws IOException {
    dictInfo = raf.readUTF();

    sources = RAFList.create(raf, EntrySource.SERIALIZER, raf.getFilePointer());

    // TODO: caching
    pairEntries = RAFList.create(raf, PairEntry.SERIALIZER, raf.getFilePointer());

    // TODO: caching
    textEntries = RAFList.create(raf, TextEntry.SERIALIZER, raf.getFilePointer());

    final List<Index> rawIndices = RAFList.create(raf, indexSerializer, raf.getFilePointer());
    indices = CachingList.create(rawIndices, rawIndices.size());
  }
  
  public void print(final PrintStream out) {
    out.println("dictInfo=" + dictInfo);
    for (final Index index : indices) {
      index.print(out);
      out.println();
    }
  }

  @Override
  public void write(RandomAccessFile raf) throws IOException {
    raf.writeUTF(dictInfo);
    RAFList.write(raf, sources, EntrySource.SERIALIZER);
    RAFList.write(raf, pairEntries, PairEntry.SERIALIZER);
    RAFList.write(raf, textEntries, TextEntry.SERIALIZER);
    RAFList.write(raf, indices, indexSerializer);
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

}