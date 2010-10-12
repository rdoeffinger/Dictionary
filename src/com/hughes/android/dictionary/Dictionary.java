package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hughes.android.dictionary.engine.Language;
import com.hughes.util.CachingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializableSerializer;
import com.hughes.util.raf.RAFSerializer;
import com.hughes.util.raf.UniformRAFList;

public final class Dictionary implements RAFSerializable<Dictionary> {
  
  private static final String VERSION_CODE = "DictionaryVersion=2.0";

  static final RAFSerializer<SimpleEntry> ENTRY_SERIALIZER = null;
  static final RAFSerializer<Row> ROW_SERIALIZER = new RAFSerializableSerializer<Row>(
      Row.RAF_FACTORY);
  static final RAFSerializer<IndexEntry> INDEX_ENTRY_SERIALIZER = new RAFSerializableSerializer<IndexEntry>(
      IndexEntry.RAF_FACTORY);

  final String dictionaryInfo;
  final List<String> sources;
  final List<Entry> entries;
  final LanguageData[] languageDatas = new LanguageData[2];

  public Dictionary(final String dictionaryInfo, final Language language0, final Language language1) {
    this.dictionaryInfo = dictionaryInfo;
    sources = new ArrayList<String>();
    languageDatas[0] = new LanguageData(this, language0, SimpleEntry.LANG1);
    languageDatas[1] = new LanguageData(this, language1, SimpleEntry.LANG2);
    entries = new ArrayList<Entry>();
  }

  public Dictionary(final RandomAccessFile raf) throws IOException {
    dictionaryInfo = raf.readUTF();
    sources = new ArrayList<String>(RAFList.create(raf, RAFSerializer.STRING, raf.getFilePointer()));
    entries = null;
    languageDatas[0] = new LanguageData(this, raf, SimpleEntry.LANG1);
    languageDatas[1] = new LanguageData(this, raf, SimpleEntry.LANG2);
    final String version = raf.readUTF();
    if (!VERSION_CODE.equals(version)) {
      throw new IOException("Invalid dictionary version, found " + version + ", expected: " + VERSION_CODE);
    }
  }

  public void write(RandomAccessFile raf) throws IOException {
    raf.writeUTF(dictionaryInfo);
    RAFList.write(raf, sources, RAFSerializer.STRING);
    //RAFList.write(raf, entries, ENTRY_SERIALIZER);
    languageDatas[0].write(raf);
    languageDatas[1].write(raf);
    raf.writeUTF(VERSION_CODE);
  }

  final class LanguageData implements RAFSerializable<LanguageData> {
    final Dictionary dictionary;
    final Language language;
    final byte lang;
    final List<Row> rows;
    final List<IndexEntry> sortedIndex;

    LanguageData(final Dictionary dictionary, final Language language, final byte lang) {
      this.dictionary = dictionary;
      this.language = language;
      this.lang = lang;
      rows = new ArrayList<Row>();
      sortedIndex = new ArrayList<IndexEntry>();
    }

    LanguageData(final Dictionary dictionary, final RandomAccessFile raf, final byte lang) throws IOException {
      this.dictionary = dictionary;
      language = Language.lookup(raf.readUTF());
      if (language == null) {
        throw new RuntimeException("Unknown language.");
      }
      this.lang = lang;
      rows = CachingList.create(UniformRAFList.create(raf, ROW_SERIALIZER, raf
          .getFilePointer()), 10000);
      sortedIndex = CachingList.create(RAFList.create(raf,
          INDEX_ENTRY_SERIALIZER, raf.getFilePointer()), 10000);
    }

    public void write(final RandomAccessFile raf) throws IOException {
      raf.writeUTF(language.symbol);
      UniformRAFList.write(raf, rows, ROW_SERIALIZER, 4);
      RAFList.write(raf, sortedIndex, INDEX_ENTRY_SERIALIZER);
    }

    String rowToString(final Row row, final boolean onlyFirstSubentry) {
      return null;
      //return row.isToken() ? sortedIndex.get(row.getIndex()).word : entries
      //    .get(row.getIndex()).getRawText(onlyFirstSubentry);
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
        if (midEntry.word.equals("pre-print")) {
          System.out.println();
        }

        final int comp = language.sortComparator.compare(word, midEntry.word.toLowerCase());
        if (comp == 0) {
          int result = mid;
          while (result > 0 && language.findComparator.compare(word, sortedIndex.get(result - 1).word.toLowerCase()) == 0) {
            --result;
            if (interrupted.get()) {
              return result;
            }
          }
          return result;
        } else if (comp < 0) {
//          Log.d("THAD", "Upper bound: " + midEntry);
          end = mid;
        } else {
//          Log.d("THAD", "Lower bound: " + midEntry);
          start = mid + 1;
        }
      }
      return Math.min(sortedIndex.size() - 1, start);
    }
    
    public int getPrevTokenRow(final int rowIndex) {
      final IndexEntry indexEntry = getIndexEntryForRow(rowIndex);
      final Row tokenRow = rows.get(indexEntry.startRow);
      assert tokenRow.isToken();
      final int prevTokenIndex = tokenRow.getIndex() - 1;
      if (indexEntry.startRow == rowIndex && prevTokenIndex >= 0) {
        return sortedIndex.get(prevTokenIndex).startRow;
      }
      return indexEntry.startRow;
    }

    public int getNextTokenRow(final int rowIndex) {
      final IndexEntry indexEntry = getIndexEntryForRow(rowIndex);
      final Row tokenRow = rows.get(indexEntry.startRow);
      assert tokenRow.isToken();
      final int nextTokenIndex = tokenRow.getIndex() + 1;
      if (nextTokenIndex < sortedIndex.size()) {
        return sortedIndex.get(nextTokenIndex).startRow;
      }
      return rows.size() - 1;
    }

    public IndexEntry getIndexEntryForRow(final int rowIndex) {
      // TODO: this kinda blows.
      int r = rowIndex;
      Row row;
      while (true) {
        row = rows.get(r); 
        if (row.isToken() || row.indexEntry != null) {
          break;
        }
        --r;
      }
      final IndexEntry indexEntry = row.isToken() ? sortedIndex.get(row.getIndex()) : row.indexEntry;
      for (; r <= rowIndex; ++r) {
        rows.get(r).indexEntry = indexEntry;
      }
      assert rows.get(indexEntry.startRow).isToken();
      return indexEntry;
    }
  }

  public static final class Row implements RAFSerializable<Row> {
    final int index;

    IndexEntry indexEntry = null;

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
