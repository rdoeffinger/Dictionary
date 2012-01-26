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
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hughes.android.dictionary.DictionaryInfo;
import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
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
  
  public final String shortName;  // Typically the ISO code for the language.
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
  
  // Version 2:
  int mainTokenCount = -1;
  
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
    if (dict.dictFileVersion >= 2) {
      mainTokenCount = raf.readInt();
    }
    sortedIndexEntries = CachingList.create(RAFList.create(raf, IndexEntry.SERIALIZER, raf.getFilePointer()), CACHE_SIZE);
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
  
  final List<RowBase> multiWordSearch(final List<String> searchTokens, final AtomicBoolean interrupted) {
    final List<RowBase> result = new ArrayList<RowBase>();

    // Heuristic: use the longest searchToken as the base.
    String searchToken = null;
    for (int i = 0; i < searchTokens.size(); ++i) {
      if (interrupted.get()) { return null; }
      final String normalized = normalizeToken(searchTokens.get(i));
      // Normalize them all.
      searchTokens.set(i, normalized);
      if (searchToken == null || normalized.length() > searchToken.length()) {
        searchToken = normalized;
      }
    }
    
    final int insertionPointIndex = findInsertionPointIndex(searchToken, interrupted);
    if (insertionPointIndex == -1 || interrupted.get()) {
      return null;
    }
    
    // The things that match.
    // TODO: use a key
    final Map<RowMatchType,Set<RowBase>> matches = new EnumMap<RowMatchType, Set<RowBase>>(RowMatchType.class);
    for (final RowMatchType rowMatchType : RowMatchType.values()) {
      matches.put(rowMatchType, new LinkedHashSet<RowBase>());
    }
    
    for (int index = insertionPointIndex; index < sortedIndexEntries.size(); ++index) {
      if (interrupted.get()) { return null; }
      final IndexEntry indexEntry = sortedIndexEntries.get(index);
      if (!indexEntry.normalizedToken.equals(searchToken)) {
        break;
      }
      
      for (int rowIndex = indexEntry.startRow; rowIndex < indexEntry.startRow + indexEntry.numRows; ++rowIndex) {
        if (interrupted.get()) { return null; }
        final RowBase row = rows.get(rowIndex);
        final RowMatchType matchType = row.matches(searchTokens, normalizer, swapPairEntries);
        if (matchType != RowMatchType.NO_MATCH) {
          matches.get(matchType).add(row);
        }
      }
    }
    
    for (final Set<RowBase> rows : matches.values()) {
      result.addAll(rows);
    }
    
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