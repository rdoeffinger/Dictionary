// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
import com.ibm.icu.text.Transliterator;

public final class Index implements RAFSerializable<Index> {
  
  static final int CACHE_SIZE = 5000;
  
  final Dictionary dict;
  
  public final String shortName;
  public final String longName;
  
  // persisted: tells how the entries are sorted.
  public final Language sortLanguage;
  final String normalizerRules;
  
  // Built from the two above.
  private Transliterator normalizer;
    
  // persisted
  public final List<IndexEntry> sortedIndexEntries;

  // One big list!
  // Various sub-types.
  // persisted
  public final List<RowBase> rows;
  
  public final boolean swapPairEntries;
  
  // --------------------------------------------------------------------------
  
  public Index(final Dictionary dict, final String shortName, final String longName, final Language sortLanguage, final String normalizerRules, final boolean swapPairEntries) {
    this.dict = dict;
    this.shortName = shortName;
    this.longName = longName;
    this.sortLanguage = sortLanguage;
    this.normalizerRules = normalizerRules;
    this.swapPairEntries = swapPairEntries;
    sortedIndexEntries = new ArrayList<IndexEntry>();
    rows = new ArrayList<RowBase>();
    
    normalizer = null;
  }
  
  public synchronized Transliterator normalizer() {
    if (normalizer == null) {
      normalizer = Transliterator.createFromRules("", normalizerRules, Transliterator.FORWARD);
    }
    return normalizer;
  }
  
  public Index(final Dictionary dict, final RandomAccessFile raf) throws IOException {
    this.dict = dict;
    shortName = raf.readUTF();
    longName = raf.readUTF();
    final String languageCode = raf.readUTF();
    sortLanguage = Language.lookup(languageCode);
    normalizerRules = raf.readUTF();
    swapPairEntries = raf.readBoolean();
    if (sortLanguage == null) {
      throw new IOException("Unsupported language: " + languageCode);
    }
    sortedIndexEntries = CachingList.create(RAFList.create(raf, IndexEntry.SERIALIZER, raf.getFilePointer()), CACHE_SIZE);
    rows = CachingList.create(UniformRAFList.create(raf, new RowBase.Serializer(this), raf.getFilePointer()), CACHE_SIZE);
  }
  
  @Override
  public void write(final RandomAccessFile raf) throws IOException {
    raf.writeUTF(shortName);
    raf.writeUTF(longName);
    raf.writeUTF(sortLanguage.getSymbol());
    raf.writeUTF(normalizerRules);
    raf.writeBoolean(swapPairEntries);
    RAFList.write(raf, sortedIndexEntries, IndexEntry.SERIALIZER);
    UniformRAFList.write(raf, (Collection<RowBase>) rows, new RowBase.Serializer(this), 5);
  }

  public void print(final PrintStream out) {
    for (final RowBase row : rows) {
      row.print(out);
    }
  }
  
  public static final class IndexEntry implements RAFSerializable<Index.IndexEntry> {
    public final String token;
    private final String normalizedToken;
    public final int startRow;
    public final int numRows;
    
    
    static final RAFSerializer<IndexEntry> SERIALIZER = new RAFSerializer<IndexEntry> () {
      @Override
      public IndexEntry read(RandomAccessFile raf) throws IOException {
        return new IndexEntry(raf);
      }
      @Override
      public void write(RandomAccessFile raf, IndexEntry t) throws IOException {
        t.write(raf);
      }};
      
    public IndexEntry(final String token, final String normalizedToken, final int startRow, final int numRows) {
      assert token.equals(token.trim());
      assert token.length() > 0;
      this.token = token;
      this.normalizedToken = normalizedToken;
      this.startRow = startRow;
      this.numRows = numRows;
    }
    
    public IndexEntry(final RandomAccessFile raf) throws IOException {
      token = raf.readUTF();
      startRow = raf.readInt();
      numRows = raf.readInt();
      final boolean hasNormalizedForm = raf.readBoolean();
      normalizedToken = hasNormalizedForm ? raf.readUTF() : token;
    }
    
    public void write(RandomAccessFile raf) throws IOException {
      raf.writeUTF(token);
      raf.writeInt(startRow);
      raf.writeInt(numRows);
      final boolean hasNormalizedForm = !token.equals(normalizedToken);
      raf.writeBoolean(hasNormalizedForm);
      if (hasNormalizedForm) {
        raf.writeUTF(normalizedToken);
      }
    }

    public String toString() {
      return String.format("%s@%d(%d)", token, startRow, numRows);
    }

    public String normalizedToken() {
      return normalizedToken;
    }
  }
  
  public IndexEntry findInsertionPoint(String token, final AtomicBoolean interrupted) {
    final Transliterator normalizer = normalizer();
    if (TransliteratorManager.init(null)) {
      token = normalizer.transliterate(token);
    } else {
      // Do our best since the Transliterators aren't up yet.
      token = token.toLowerCase();
    }

    int start = 0;
    int end = sortedIndexEntries.size();
    
    final Collator sortCollator = sortLanguage.getCollator();
    while (start < end) {
      final int mid = (start + end) / 2;
      if (interrupted.get()) {
        return null;
      }
      final IndexEntry midEntry = sortedIndexEntries.get(mid);

      final int comp = sortCollator.compare(token, midEntry.normalizedToken());
      if (comp == 0) {
        final int result = windBackCase(token, mid, interrupted);
        return sortedIndexEntries.get(result);
      } else if (comp < 0) {
        //System.out.println("Upper bound: " + midEntry + ", norm=" + midEntry.normalizedToken() + ", mid=" + mid);
        end = mid;
      } else {
        //System.out.println("Lower bound: " + midEntry + ", norm=" + midEntry.normalizedToken() + ", mid=" + mid);
        start = mid + 1;
      }
    }

    // If we search for a substring of a string that's in there, return that.
    int result = Math.min(start, sortedIndexEntries.size() - 1);
    result = windBackCase(sortedIndexEntries.get(result).normalizedToken(), result, interrupted);
    return sortedIndexEntries.get(result);
  }
  
  public static final class SearchResult {
    public final IndexEntry insertionPoint;
    public final IndexEntry longestPrefix;
    public final String longestPrefixString;
    public final boolean success;
    
    public SearchResult(IndexEntry insertionPoint, IndexEntry longestPrefix,
        String longestPrefixString, boolean success) {
      this.insertionPoint = insertionPoint;
      this.longestPrefix = longestPrefix;
      this.longestPrefixString = longestPrefixString;
      this.success = success;
    }
    
    @Override
    public String toString() {
      return String.format("inerstionPoint=%s,longestPrefix=%s,longestPrefixString=%s,success=%b", insertionPoint.toString(), longestPrefix.toString(), longestPrefixString, success);
    }
  }
  
//  public SearchResult findLongestSubstring(String token, final AtomicBoolean interrupted) {
//    token = normalizer.transliterate(token);
//    if (token.length() == 0) {
//      return new SearchResult(sortedIndexEntries.get(0), sortedIndexEntries.get(0), "", true);
//    }
//    IndexEntry insertionPoint = null;
//    IndexEntry result = null;
//    boolean unmodified = true;
//    while (!interrupted.get() && token.length() > 0) {
//      result = findInsertionPoint(token, interrupted);
//      if (result == null) {
//        return null;
//      }
//      if (unmodified) {
//        insertionPoint = result;
//      }
//      if (result.normalizedToken(normalizer).startsWith(token)) {
//        return new SearchResult(insertionPoint, result, token, unmodified);
//      }
//      unmodified = false;
//      token = token.substring(0, token.length() - 1);      
//    }
//    return new SearchResult(insertionPoint, sortedIndexEntries.get(0), "", false);
//  }
  
  private final int windBackCase(final String token, int result, final AtomicBoolean interrupted) {
    while (result > 0 && sortedIndexEntries.get(result - 1).normalizedToken().equals(token)) {
      --result;
      if (interrupted.get()) {
        return result;
      }
    }
    return result;
  }

  /*
  public int tokenRowBinarySearch(final int rowIndex) {
    int start = 0;
    int end = sortedIndexEntries.size();
    while (start < end) {
      final int mid = (start + end) / 2;
      final int midRowIndex = sortedIndexEntries.get(mid).startRow;
      if (midRowIndex == rowIndex) {
        return mid;
      }
      if ()
    }
  }
  */

}