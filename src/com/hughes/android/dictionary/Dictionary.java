package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hughes.util.CachingList;
import com.hughes.util.raf.FileList;
import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializableSerializer;
import com.hughes.util.raf.RAFSerializer;
import com.hughes.util.raf.UniformFileList;

public final class Dictionary implements RAFSerializable<Dictionary> {

  static final RAFSerializer<Entry> ENTRY_SERIALIZER = new RAFSerializableSerializer<Entry>(
      Entry.RAF_FACTORY);
  static final RAFSerializer<Row> ROW_SERIALIZER = new RAFSerializableSerializer<Row>(
      Row.RAF_FACTORY);
  static final RAFSerializer<IndexEntry> INDEX_ENTRY_SERIALIZER = new RAFSerializableSerializer<IndexEntry>(
      IndexEntry.RAF_FACTORY);

  final List<Entry> entries;
  final Language[] languages = new Language[2];

  public Dictionary(final String lang0, final String lang1) {
    languages[0] = new Language(lang0, Entry.LANG1);
    languages[1] = new Language(lang1, Entry.LANG2);
    entries = new ArrayList<Entry>();
  }

  public Dictionary(final RandomAccessFile raf) throws IOException {
    entries = CachingList.create(FileList.create(raf, ENTRY_SERIALIZER, raf
        .getFilePointer()), 10000);
    languages[0] = new Language(raf, Entry.LANG1);
    languages[1] = new Language(raf, Entry.LANG2);
  }

  public void write(RandomAccessFile raf) throws IOException {
    FileList.write(raf, entries, ENTRY_SERIALIZER);
    languages[0].write(raf);
    languages[1].write(raf);
  }

  final class Language implements RAFSerializable<Language> {
    final byte lang;
    final String symbol;
    final List<Row> rows;
    final List<IndexEntry> sortedIndex;
    final Comparator<String> comparator = EntryFactory.entryFactory
        .getEntryComparator();

    Language(final String symbol, final byte lang) {
      this.lang = lang;
      this.symbol = symbol;
      rows = new ArrayList<Row>();
      sortedIndex = new ArrayList<IndexEntry>();
    }

    Language(final RandomAccessFile raf, final byte lang) throws IOException {
      this.lang = lang;
      symbol = raf.readUTF();
      rows = CachingList.create(UniformFileList.create(raf, ROW_SERIALIZER, raf
          .getFilePointer()), 10000);
      sortedIndex = CachingList.create(FileList.create(raf,
          INDEX_ENTRY_SERIALIZER, raf.getFilePointer()), 10000);
    }

    public void write(final RandomAccessFile raf) throws IOException {
      raf.writeUTF(symbol);
      UniformFileList.write(raf, rows, ROW_SERIALIZER, 4);
      FileList.write(raf, sortedIndex, INDEX_ENTRY_SERIALIZER);
    }

    String rowToString(final Row row) {
      return row.isToken() ? sortedIndex.get(row.getIndex()).word : entries
          .get(row.getIndex()).toString();
    }

    int lookup(String word, final AtomicBoolean interrupted) {
      word = word.toLowerCase();

      int start = 0;
      int end = sortedIndex.size();
      while (start < end) {
        final int mid = (start + end) / 2;
        if (interrupted.get()) {
          return mid;
        }
        final IndexEntry midEntry = sortedIndex.get(mid);

        final int comp = comparator.compare(word, midEntry.word.toLowerCase());
        if (comp == 0) {
          int result = mid;
          while (result > 0 && comparator.compare(word, sortedIndex.get(result - 1).word.toLowerCase()) == 0) {
            --result;
            if (interrupted.get()) {
              return result;
            }
          }
          return result;
        } else if (comp < 0) {
          end = mid;
        } else {
          start = mid + 1;
        }
      }
      return start;
    }
  }

  public static final class Row implements RAFSerializable<Row> {
    final int index;

    public Row(final int index) {
      this.index = index;
    }

    static final RAFFactory<Row> RAF_FACTORY = new RAFFactory<Row>() {
      public Row create(RandomAccessFile raf) throws IOException {
        return new Row(raf.readInt());
      }
    };

    public void write(RandomAccessFile raf) throws IOException {
      raf.writeInt(index);
    }

    boolean isToken() {
      return index < 0;
    }

    public int getIndex() {
      if (index >= 0) {
        return index;
      }
      return -index - 1;
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
      }
    };

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
