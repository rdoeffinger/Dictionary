/**
 * 
 */
package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hughes.util.CachingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;
import com.hughes.util.raf.UniformRAFList;
import com.ibm.icu.text.Collator;

public final class Index implements RAFSerializable<Index> {
  
  static final int CACHE_SIZE = 5000;
  
  final Dictionary dict;
  
  final String shortName;
  final String longName;
  
  // persisted: tells how the entries are sorted.
  final Language sortLanguage;
    
  // persisted
  final List<IndexEntry> sortedIndexEntries;

  // One big list!
  // Various sub-types.
  // persisted
  final List<RowBase> rows;
  
  // --------------------------------------------------------------------------
  
  public Index(final Dictionary dict, final String shortName, final String longName, final Language sortLanguage) {
    this.dict = dict;
    this.shortName = shortName;
    this.longName = longName;
    this.sortLanguage = sortLanguage;
    sortedIndexEntries = new ArrayList<IndexEntry>();
    rows = new ArrayList<RowBase>();
  }
  
  public Index(final Dictionary dict, final RandomAccessFile raf) throws IOException {
    this.dict = dict;
    shortName = raf.readUTF();
    longName = raf.readUTF();
    final String languageCode = raf.readUTF();
    sortLanguage = Language.lookup(languageCode);
    if (sortLanguage == null) {
      throw new IOException("Unsupported language: " + languageCode);
    }
    sortedIndexEntries = CachingList.create(RAFList.create(raf, IndexEntry.SERIALIZER, raf.getFilePointer()), CACHE_SIZE);
    rows = CachingList.create(UniformRAFList.create(raf, new RowBase.Serializer(this), raf.getFilePointer()), CACHE_SIZE);
  }
  
  public void print(final PrintStream out) {
    for (final RowBase row : rows) {
      row.print(out);
    }
  }
  
  @Override
  public void write(final RandomAccessFile raf) throws IOException {
    raf.writeUTF(shortName);
    raf.writeUTF(longName);
    raf.writeUTF(sortLanguage.getSymbol());
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
      
    public IndexEntry(final String token, final int startRow) {
      assert token.equals(token.trim());
      assert token.length() > 0;
      this.token = token;
      this.startRow = startRow;
    }
    
    public IndexEntry(final RandomAccessFile raf) throws IOException {
      token = raf.readUTF();
      startRow = raf.readInt();
    }
    
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeUTF(token);
      raf.writeInt(startRow);
    }

    public String toString() {
      return token + "@" + startRow;
    }
}
  

  private TokenRow sortedIndexToToken(final int sortedIndex) {
    final IndexEntry indexEntry = sortedIndexEntries.get(sortedIndex);
    return (TokenRow) rows.get(indexEntry.startRow);
  }

  public TokenRow find(String token, final AtomicBoolean interrupted) {
    token = sortLanguage.textNorm(token, true);

    int start = 0;
    int end = sortedIndexEntries.size();
    
    final Collator sortCollator = sortLanguage.getSortCollator();
    while (start < end) {
      final int mid = (start + end) / 2;
      if (interrupted.get()) {
        return sortedIndexToToken(mid);
      }
      final IndexEntry midEntry = sortedIndexEntries.get(mid);

      final int comp = sortCollator.compare(token, sortLanguage.textNorm(midEntry.token, true));
      if (comp == 0) {
        final int result = windBack(token, mid, sortCollator, interrupted);
        return sortedIndexToToken(result);
      } else if (comp < 0) {
//        Log.d("THAD", "Upper bound: " + midEntry);
        end = mid;
      } else {
//        Log.d("THAD", "Lower bound: " + midEntry);
        start = mid + 1;
      }
    }
    int result = Math.min(start, sortedIndexEntries.size() - 1);
    result = windBack(token, result, sortCollator, interrupted);
    if (result > 0 && sortCollator.compare(sortLanguage.textNorm(sortedIndexEntries.get(result).token, true), token) > 0) {
      result = windBack(sortLanguage.textNorm(sortedIndexEntries.get(result - 1).token, true), result, sortCollator, interrupted);
    }
    return sortedIndexToToken(result);
  }
  
  private final int windBack(final String token, int result, final Collator sortCollator, final AtomicBoolean interrupted) {
    while (result > 0 && sortCollator.compare(sortLanguage.textNorm(sortedIndexEntries.get(result - 1).token, true), token) >= 0) {
      --result;
      if (interrupted.get()) {
        return result;
      }
    }
    return result;
  }

}