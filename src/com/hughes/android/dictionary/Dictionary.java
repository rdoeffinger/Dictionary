package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.hughes.util.CachingList;
import com.hughes.util.raf.FileList;
import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializableSerializer;
import com.hughes.util.raf.RAFSerializer;

public final class Dictionary implements RAFSerializable<Dictionary> {
  
  static final RAFSerializer<Entry> ENTRY_SERIALIZER = new RAFSerializableSerializer<Entry>(Entry.RAF_FACTORY);
  static final RAFSerializer<Row> ROW_SERIALIZER = new RAFSerializableSerializer<Row>(Row.RAF_FACTORY);
  static final RAFSerializer<IndexEntry> INDEX_ENTRY_SERIALIZER = new RAFSerializableSerializer<IndexEntry>(IndexEntry.RAF_FACTORY);

  final List<Entry> entries;
  final Language[] languages = new Language[2];
  
  Language activeLanguage = null;

  public Dictionary(final String lang0, final String lang1) {
    languages[0] = new Language(lang0);
    languages[1] = new Language(lang1);
    entries = new ArrayList<Entry>();
  }
  
  public Dictionary(final RandomAccessFile raf) throws IOException {
    entries = CachingList.create(FileList.create(raf, ENTRY_SERIALIZER, raf.getFilePointer()), 10000);
    languages[0] = new Language(raf);
    languages[1] = new Language(raf);
  }
  public void write(RandomAccessFile raf) throws IOException {
    FileList.write(raf, entries, ENTRY_SERIALIZER);
    languages[0].write(raf);
    languages[1].write(raf);
  }
  
  static final class Language implements RAFSerializable<Language> {
    final String symbol;
    final List<Row> rows;
    final List<IndexEntry> sortedIndex;
    
    public Language(final String symbol) {
      this.symbol = symbol;
      rows = new ArrayList<Row>();
      sortedIndex = new ArrayList<IndexEntry>();
    }

    public Language(final RandomAccessFile raf) throws IOException {
      symbol = raf.readUTF();
      rows = CachingList.create(FileList.create(raf, ROW_SERIALIZER, raf.getFilePointer()), 10000);
      sortedIndex = CachingList.create(FileList.create(raf, INDEX_ENTRY_SERIALIZER, raf.getFilePointer()), 10000);
    }
    public void write(final RandomAccessFile raf) throws IOException {
      raf.writeUTF(symbol);
      FileList.write(raf, rows, ROW_SERIALIZER);
      FileList.write(raf, sortedIndex, INDEX_ENTRY_SERIALIZER);
    }
  }
  
  public static final class Row implements RAFSerializable<Row> {
    final int index;

    public Row(int index) {
      this.index = index;
    }

    static final RAFFactory<Row> RAF_FACTORY = new RAFFactory<Row>() {
      public Row create(RandomAccessFile raf) throws IOException {
        return new Row(raf.readInt());
      }};
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeInt(index);
    }
  }

  public static final class IndexEntry implements RAFSerializable<IndexEntry> {
    final String word;
    final int startRow;
    
    public IndexEntry(final String word, final int startRow) {
      this.word = word;
      this.startRow = startRow;
    }

    static final RAFFactory<IndexEntry> RAF_FACTORY = new RAFFactory<IndexEntry>() {
      public IndexEntry create(RandomAccessFile raf) throws IOException {
        final String word = raf.readUTF();
        final int startRow = raf.readInt();
        return new IndexEntry(word, startRow);
      }};
    public void write(final RandomAccessFile raf) throws IOException {
      raf.writeUTF(word);
      raf.writeInt(startRow);
    }

    @Override
    public String toString() {
      return word + "@" + startRow;
    }
    
    
  }

}
