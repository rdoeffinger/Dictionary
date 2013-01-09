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

import com.hughes.android.dictionary.DictionaryInfo;
import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.RowBase.RowKey;
import com.hughes.util.CachingList;
import com.hughes.util.TransformingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;
import com.hughes.util.raf.SerializableSerializer;
import com.hughes.util.raf.UniformRAFList;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class Index implements RAFSerializable<Index> {
  
  static final int CACHE_SIZE = 5000;
  
  public final Dictionary dict;
  
  public final String shortName;  // Typically the ISO code for the language.
  public final String longName;
  
  // persisted: tells how the entries are sorted.
  public final Language sortLanguage;
  final String normalizerRules;
  
  // Built from the two above.
  private Transliterator normalizer;
    
  // persisted
  public final List<IndexEntry> sortedIndexEntries;
  
  // persisted.
  public final Set<String> stoplist;

  // One big list!
  // Various sub-types.
  // persisted
  public final List<RowBase> rows;
  public final boolean swapPairEntries;
  
  // Version 2:
  int mainTokenCount = -1;
  
  // --------------------------------------------------------------------------
  
  public Index(final Dictionary dict, final String shortName, final String longName, final Language sortLanguage, final String normalizerRules, final boolean swapPairEntries, final Set<String> stoplist) {
    this.dict = dict;
    this.shortName = shortName;
    this.longName = longName;
    this.sortLanguage = sortLanguage;
    this.normalizerRules = normalizerRules;
    this.swapPairEntries = swapPairEntries;
    sortedIndexEntries = new ArrayList<IndexEntry>();
    this.stoplist = stoplist;
    rows = new ArrayList<RowBase>();
    
    normalizer = null;
  }
  
  /**
   * Deferred initialization because it can be slow.
   */
  public synchronized Transliterator normalizer() {
    if (normalizer == null) {
      normalizer = Transliterator.createFromRules("", normalizerRules, Transliterator.FORWARD);
    }
    return normalizer;
  }
  
  /**
   * Note that using this comparator probably involves doing too many text normalizations.
   */
  public NormalizeComparator getSortComparator() {
    return new NormalizeComparator(normalizer(), sortLanguage.getCollator());
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
    if (dict.dictFileVersion >= 2) {
      mainTokenCount = raf.readInt();
    }
    sortedIndexEntries = CachingList.create(RAFList.create(raf, indexEntrySerializer, raf.getFilePointer()), CACHE_SIZE);
    if (dict.dictFileVersion >= 4) {
      stoplist = new SerializableSerializer<Set<String>>().read(raf);
    } else {
      stoplist = Collections.emptySet();
    }
    rows = CachingList.create(UniformRAFList.create(raf, new RowBase.Serializer(this), raf.getFilePointer()), CACHE_SIZE);
  }
  
  @Override
  public void write(final RandomAccessFile raf) throws IOException {
    raf.writeUTF(shortName);
    raf.writeUTF(longName);
    raf.writeUTF(sortLanguage.getIsoCode());
    raf.writeUTF(normalizerRules);
    raf.writeBoolean(swapPairEntries);
    if (dict.dictFileVersion >= 2) {
      raf.writeInt(mainTokenCount);
    }
    RAFList.write(raf, sortedIndexEntries, indexEntrySerializer);
    new SerializableSerializer<Set<String>>().write(raf, stoplist);
    UniformRAFList.write(raf, (Collection<RowBase>) rows, new RowBase.Serializer(this), 5 /* bytes per entry */);
  }

  public void print(final PrintStream out) {
    for (final RowBase row : rows) {
      row.print(out);
    }
  }
  
  private final RAFSerializer<IndexEntry> indexEntrySerializer = new RAFSerializer<IndexEntry> () {
      @Override
      public IndexEntry read(RandomAccessFile raf) throws IOException {
        return new IndexEntry(Index.this, raf);
      }
      @Override
      public void write(RandomAccessFile raf, IndexEntry t) throws IOException {
        t.write(raf);
      }};
      

  public static final class IndexEntry implements RAFSerializable<Index.IndexEntry> {
    private final Index index;
    public final String token;
    private final String normalizedToken;
    public final int startRow;
    public final int numRows;  // doesn't count the token row!
    public final List<HtmlEntry> htmlEntries;
    
    
    public IndexEntry(final Index index, final String token, final String normalizedToken, final int startRow, final int numRows) {
      this.index = index;
      assert token.equals(token.trim());
      assert token.length() > 0;
      this.token = token;
      this.normalizedToken = normalizedToken;
      this.startRow = startRow;
      this.numRows = numRows;
      this.htmlEntries = new ArrayList<HtmlEntry>();
    }
    
    public IndexEntry(final Index index, final RandomAccessFile raf) throws IOException {
      this.index = index;
      token = raf.readUTF();
      startRow = raf.readInt();
      numRows = raf.readInt();
      final boolean hasNormalizedForm = raf.readBoolean();
      normalizedToken = hasNormalizedForm ? raf.readUTF() : token;
      if (index.dict.dictFileVersion >= 6) {
        this.htmlEntries = CachingList.create(RAFList.create(raf, index.dict.htmlEntryIndexSerializer, raf.getFilePointer()), 1);
      } else {
        this.htmlEntries = Collections.emptyList();
      }
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
      RAFList.write(raf, htmlEntries, index.dict.htmlEntryIndexSerializer);
    }

    public String toString() {
      return String.format("%s@%d(%d)", token, startRow, numRows);
    }

    public String normalizedToken() {
      return normalizedToken;
    }
  }
  
  static final TransformingList.Transformer<IndexEntry, String> INDEX_ENTRY_TO_TOKEN = new TransformingList.Transformer<IndexEntry, String>() {
    @Override
    public String transform(IndexEntry t1) {
      return t1.token;
    }
  };
  
  public IndexEntry findExact(final String exactToken) {
    final int result = Collections.binarySearch(TransformingList.create(sortedIndexEntries, INDEX_ENTRY_TO_TOKEN), exactToken, getSortComparator());
    if (result >= 0) {
      return sortedIndexEntries.get(result);
    }
    return null;
  }
  
  public IndexEntry findInsertionPoint(String token, final AtomicBoolean interrupted) {
    final int index = findInsertionPointIndex(token, interrupted);
    return index != -1 ? sortedIndexEntries.get(index) : null;
  }
  
  public int findInsertionPointIndex(String token, final AtomicBoolean interrupted) {
    token = normalizeToken(token);

    int start = 0;
    int end = sortedIndexEntries.size();
    
    final Collator sortCollator = sortLanguage.getCollator();
    while (start < end) {
      final int mid = (start + end) / 2;
      if (interrupted.get()) {
        return -1;
      }
      final IndexEntry midEntry = sortedIndexEntries.get(mid);

      final int comp = sortCollator.compare(token, midEntry.normalizedToken());
      if (comp == 0) {
        final int result = windBackCase(token, mid, interrupted);
        return result;
      } else if (comp < 0) {
        // System.out.println("Upper bound: " + midEntry + ", norm=" + midEntry.normalizedToken() + ", mid=" + mid);
        end = mid;
      } else {
        // System.out.println("Lower bound: " + midEntry + ", norm=" + midEntry.normalizedToken() + ", mid=" + mid);
        start = mid + 1;
      }
    }

    // If we search for a substring of a string that's in there, return that.
    int result = Math.min(start, sortedIndexEntries.size() - 1);
    result = windBackCase(sortedIndexEntries.get(result).normalizedToken(), result, interrupted);
    return result;
  }
    
  private final int windBackCase(final String token, int result, final AtomicBoolean interrupted) {
    while (result > 0 && sortedIndexEntries.get(result - 1).normalizedToken().equals(token)) {
      --result;
      if (interrupted.get()) {
        return result;
      }
    }
    return result;
  }

  public IndexInfo getIndexInfo() {
    return new DictionaryInfo.IndexInfo(shortName, sortedIndexEntries.size(), mainTokenCount);
  }
  
  private static final int MAX_SEARCH_ROWS = 1000;
  
  private final Map<String,Integer> prefixToNumRows = new LinkedHashMap<String, Integer>();
  private synchronized final int getUpperBoundOnRowsStartingWith(final String normalizedPrefix, final int maxRows, final AtomicBoolean interrupted) {
    final Integer numRows = prefixToNumRows.get(normalizedPrefix);
    if (numRows != null) {
      return numRows;
    }
    final int insertionPointIndex = findInsertionPointIndex(normalizedPrefix, interrupted);
  
    int rowCount = 0;
    for (int index = insertionPointIndex; index < sortedIndexEntries.size(); ++index) {
      if (interrupted.get()) { return -1; }
      final IndexEntry indexEntry = sortedIndexEntries.get(index);
      if (!indexEntry.normalizedToken.startsWith(normalizedPrefix)) {
        break;
      }
      rowCount += indexEntry.numRows + indexEntry.htmlEntries.size();
      if (rowCount > maxRows) {
        System.out.println("Giving up, too many words with prefix: " + normalizedPrefix);
        break;
      }
    }
    prefixToNumRows.put(normalizedPrefix, numRows);
    return rowCount;
  }
  
  
  public final List<RowBase> multiWordSearch(
          final String searchText, final List<String> searchTokens, final AtomicBoolean interrupted) {
    final long startMills = System.currentTimeMillis();
    final List<RowBase> result = new ArrayList<RowBase>();
    
    final Set<String> normalizedNonStoplist = new LinkedHashSet<String>();
    
    String bestPrefix = null;
    int leastRows = Integer.MAX_VALUE;
    final StringBuilder searchTokensRegex = new StringBuilder();
    for (int i = 0; i < searchTokens.size(); ++i) {
      if (interrupted.get()) { return null; }
      final String searchToken = searchTokens.get(i);
      final String normalized = normalizeToken(searchTokens.get(i));
      // Normalize them all.
      searchTokens.set(i, normalized);

      if (!stoplist.contains(searchToken)) {
        if (normalizedNonStoplist.add(normalized)) {
          final int numRows = getUpperBoundOnRowsStartingWith(normalized, MAX_SEARCH_ROWS, interrupted);
          if (numRows != -1 && numRows < leastRows) {
            if (numRows == 0) {
              // We really are done here.
              return Collections.emptyList();
            }
            leastRows = numRows;
            bestPrefix = normalized;
          }
        }
      }

      if (searchTokensRegex.length() > 0) {
        searchTokensRegex.append("[\\s]*");
      }
      searchTokensRegex.append(Pattern.quote(normalized));
    }
    final Pattern pattern = Pattern.compile(searchTokensRegex.toString());
    
    if (bestPrefix == null) {
      bestPrefix = searchTokens.get(0);
      System.out.println("Everything was in the stoplist!");
    }
    System.out.println("Searching using prefix: " + bestPrefix + ", leastRows=" + leastRows + ", searchTokens=" + searchTokens);

    // Place to store the things that match.
    final Map<RowMatchType,List<RowBase>> matches = new EnumMap<RowMatchType, List<RowBase>>(RowMatchType.class);
    for (final RowMatchType rowMatchType : RowMatchType.values()) {
      if (rowMatchType != RowMatchType.NO_MATCH) {
        matches.put(rowMatchType, new ArrayList<RowBase>());
      }
    }
    
    int matchCount = 0;
    
    final int exactMatchIndex = findInsertionPointIndex(searchText, interrupted);
    if (exactMatchIndex != -1) {
        final IndexEntry exactMatch = sortedIndexEntries.get(exactMatchIndex);
        if (pattern.matcher(exactMatch.token).find()) {
            matches.get(RowMatchType.TITLE_MATCH).add(rows.get(exactMatch.startRow));
        }
    }
    
    final String searchToken = bestPrefix;
    final int insertionPointIndex = findInsertionPointIndex(searchToken, interrupted);
    final Set<RowKey> rowsAlreadySeen = new HashSet<RowBase.RowKey>();
    for (int index = insertionPointIndex; 
            index < sortedIndexEntries.size() && matchCount < MAX_SEARCH_ROWS; 
            ++index) {
        if (interrupted.get()) { return null; }
        final IndexEntry indexEntry = sortedIndexEntries.get(index);
        if (!indexEntry.normalizedToken.startsWith(searchToken)) {
          break;
        }

//        System.out.println("Searching indexEntry: " + indexEntry.token);

        // Extra +1 to skip token row.
        for (int rowIndex = indexEntry.startRow + 1; 
                rowIndex < indexEntry.startRow + 1 + indexEntry.numRows && rowIndex < rows.size(); 
                ++rowIndex) {
          if (interrupted.get()) { return null; }
          final RowBase row = rows.get(rowIndex);
          final RowBase.RowKey rowKey = row.getRowKey();
          if (rowsAlreadySeen.contains(rowKey)) {
            continue;
          }
          rowsAlreadySeen.add(rowKey);
          final RowMatchType matchType = row.matches(searchTokens, pattern, normalizer(), swapPairEntries);
          if (matchType != RowMatchType.NO_MATCH) {
            matches.get(matchType).add(row);
            ++matchCount;
          }
        }
      }
//    }  // searchTokens

    // Sort them into a reasonable order.
    final RowBase.LengthComparator lengthComparator = new RowBase.LengthComparator(swapPairEntries);
    for (final Collection<RowBase> rows : matches.values()) {
      final List<RowBase> ordered = new ArrayList<RowBase>(rows);
      Collections.sort(ordered, lengthComparator);
      result.addAll(ordered);
    }
    
    System.out.println("searchDuration: " + (System.currentTimeMillis() - startMills));
    return result;
  }
  
  private String normalizeToken(final String searchToken) {
    if (TransliteratorManager.init(null)) {
      final Transliterator normalizer = normalizer();
      return normalizer.transliterate(searchToken);
    } else {
      // Do our best since the Transliterators aren't up yet.
      return searchToken.toLowerCase();
    }
  }

}