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

package com.hughes.android.dictionary.engine;

import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hughes.android.dictionary.engine.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.RowBase.RowKey;
import com.hughes.util.CachingList;
import com.hughes.util.DataInputBuffer;
import com.hughes.util.StringUtil;
import com.hughes.util.TransformingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.UniformRAFList;
import com.ibm.icu.text.Transliterator;

public final class Index {

    private static final int CACHE_SIZE = 5000;

    public final Dictionary dict;

    public final String shortName; // Typically the ISO code for the language.
    public final String longName;

    // persisted: tells how the entries are sorted.
    public final Language sortLanguage;
    public final String normalizerRules;

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
    @SuppressWarnings("WeakerAccess")
    public int mainTokenCount = -1;

    // --------------------------------------------------------------------------

    public Index(final Dictionary dict, final String shortName, final String longName,
                 final Language sortLanguage, final String normalizerRules,
                 final boolean swapPairEntries, final Set<String> stoplist) {
        this.dict = dict;
        this.shortName = shortName;
        this.longName = longName;
        this.sortLanguage = sortLanguage;
        this.normalizerRules = normalizerRules;
        this.swapPairEntries = swapPairEntries;
        sortedIndexEntries = new ArrayList<>();
        this.stoplist = stoplist;
        rows = new ArrayList<>();

        normalizer = null;
    }

    /**
     * Deferred initialization because it can be slow.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized Transliterator normalizer() {
        if (normalizer == null) {
            normalizer = TransliteratorManager.get(normalizerRules);
        }
        return normalizer;
    }

    /**
     * Note that using this comparator probably involves doing too many text
     * normalizations.
     */
    @SuppressWarnings("WeakerAccess")
    public NormalizeComparator getSortComparator() {
        return new NormalizeComparator(normalizer(), sortLanguage.getCollator(), dict.dictFileVersion);
    }

    public Index(final Dictionary dict, final DataInputBuffer raf) throws IOException {
        this.dict = dict;
        shortName = raf.readUTF();
        longName = raf.readUTF();
        final String languageCode = raf.readUTF();
        sortLanguage = Language.lookup(languageCode);
        normalizerRules = raf.readUTF();
        swapPairEntries = raf.readBoolean();
        if (dict.dictFileVersion >= 2) {
            mainTokenCount = raf.readInt();
        }
        sortedIndexEntries = CachingList.create(
                                 RAFList.create(raf, new IndexEntrySerializer(),
                                                dict.dictFileVersion, dict.dictInfo + " idx " + languageCode + ": "), CACHE_SIZE, true);
        if (dict.dictFileVersion >= 7) {
            int count = StringUtil.readVarInt(raf);
            stoplist = new HashSet<>(count);
            for (int i = 0; i < count; ++i) {
                stoplist.add(raf.readUTF());
            }
        } else if (dict.dictFileVersion >= 4) {
            stoplist = new HashSet<>();
            raf.readInt(); // length
            raf.skipBytes(18);
            byte b = raf.readByte();
            raf.skipBytes(b == 'L' ? 71 : 33);
            while ((b = raf.readByte()) == 0x74) {
                stoplist.add(raf.readUTF());
            }
            if (b != 0x78) throw new IOException("Invalid data in dictionary stoplist!");
        } else {
            stoplist = Collections.emptySet();
        }
        rows = CachingList.create(
                   UniformRAFList.create(raf, new RowBase.Serializer(this)),
                   CACHE_SIZE, true);
    }

    public void write(final DataOutput out) throws IOException {
        RandomAccessFile raf = (RandomAccessFile)out;
        raf.writeUTF(shortName);
        raf.writeUTF(longName);
        raf.writeUTF(sortLanguage.getIsoCode());
        raf.writeUTF(normalizerRules);
        raf.writeBoolean(swapPairEntries);
        raf.writeInt(mainTokenCount);
        RAFList.write(raf, sortedIndexEntries, new IndexEntrySerializer(), 32, true);
        StringUtil.writeVarInt(raf, stoplist.size());
        for (String i : stoplist) {
            raf.writeUTF(i);
        }
        DataOutputStream outb = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(raf.getFD())));
        UniformRAFList.write(outb, rows, new RowBase.Serializer(this), 3 /* bytes per entry */);
        outb.flush();
    }

    public void print(final PrintStream out) {
        for (final RowBase row : rows) {
            row.print(out);
        }
    }

    private final class IndexEntrySerializer implements RAFListSerializer<IndexEntry> {
        @Override
        public IndexEntry read(DataInput raf, int index) throws IOException {
            return new IndexEntry(Index.this, raf);
        }

        @Override
        public void write(DataOutput raf, IndexEntry t) throws IOException {
            t.write(raf);
        }
    }

    public static final class IndexEntry {
        public final String token;
        private final String normalizedToken;
        public final int startRow;
        final int numRows; // doesn't count the token row!
        public List<HtmlEntry> htmlEntries;

        public IndexEntry(final Index index, final String token, final String normalizedToken,
                          final int startRow, final int numRows, final List<HtmlEntry> htmlEntries) {
            assert token.equals(token.trim());
            assert !token.isEmpty();
            this.token = token;
            this.normalizedToken = normalizedToken;
            this.startRow = startRow;
            this.numRows = numRows;
            this.htmlEntries = htmlEntries;
        }

        IndexEntry(final Index index, final DataInput raf) throws IOException {
            token = raf.readUTF();
            if (index.dict.dictFileVersion >= 7) {
                startRow = StringUtil.readVarInt(raf);
                numRows = StringUtil.readVarInt(raf);
            } else {
                startRow = raf.readInt();
                numRows = raf.readInt();
            }
            final boolean hasNormalizedForm = raf.readBoolean();
            normalizedToken = hasNormalizedForm ? raf.readUTF() : token;
            if (index.dict.dictFileVersion >= 7) {
                int size = StringUtil.readVarInt(raf);
                if (size == 0) {
                    this.htmlEntries = Collections.emptyList();
                } else {
                    final int[] htmlEntryIndices = new int[size];
                    for (int i = 0; i < size; ++i) {
                        htmlEntryIndices[i] = StringUtil.readVarInt(raf);
                    }
                    this.htmlEntries = new AbstractList<HtmlEntry>() {
                        @Override
                        public HtmlEntry get(int i) {
                            return index.dict.htmlEntries.get(htmlEntryIndices[i]);
                        }

                        @Override
                        public int size() {
                            return htmlEntryIndices.length;
                        }
                    };
                }
            } else if (index.dict.dictFileVersion == 6) {
                this.htmlEntries = CachingList.create(
                                       RAFList.create((DataInputBuffer)raf, index.dict.htmlEntryIndexSerializer,
                                                      index.dict.dictFileVersion,
                                                      index.dict.dictInfo + " htmlEntries: "), 1, false);
            } else {
                this.htmlEntries = Collections.emptyList();
            }
        }

        public void write(DataOutput raf) throws IOException {
            raf.writeUTF(token);
            StringUtil.writeVarInt(raf, startRow);
            StringUtil.writeVarInt(raf, numRows);
            final boolean hasNormalizedForm = !token.equals(normalizedToken);
            raf.writeBoolean(hasNormalizedForm);
            if (hasNormalizedForm) {
                raf.writeUTF(normalizedToken);
            }
            StringUtil.writeVarInt(raf, htmlEntries.size());
            for (HtmlEntry e : htmlEntries)
                StringUtil.writeVarInt(raf, e.index());
        }

        public String toString() {
            return String.format("%s@%d(%d)", token, startRow, numRows);
        }

        String normalizedToken() {
            return normalizedToken;
        }
    }

    private static final TransformingList.Transformer<IndexEntry, String> INDEX_ENTRY_TO_TOKEN = t1 -> t1.token;

    public IndexEntry findExact(final String exactToken) {
        final int result = Collections.binarySearch(
                               TransformingList.create(sortedIndexEntries, INDEX_ENTRY_TO_TOKEN), exactToken,
                               getSortComparator());
        if (result >= 0) {
            return sortedIndexEntries.get(result);
        }
        return null;
    }

    public IndexEntry findInsertionPoint(String token, final AtomicBoolean interrupted) {
        final int index = findInsertionPointIndex(token, interrupted);
        return index != -1 ? sortedIndexEntries.get(index) : null;
    }

    public IndexEntry findInsertionPoint(String token) {
        final int index = findInsertionPointIndex(token, null);
        return sortedIndexEntries.get(index);
    }

    private int compareIdx(String token, final Comparator<Object> sortCollator, int idx) {
        final IndexEntry entry = sortedIndexEntries.get(idx);
        return NormalizeComparator.compareWithoutDash(token, entry.normalizedToken(), sortCollator, dict.dictFileVersion);
    }

    private int findMatchLen(final Comparator<Object> sortCollator, String a, String b) {
        int start = 0;
        int end = Math.min(a.length(), b.length());
        while (start < end)
        {
            int mid = (start + end + 1) / 2;
            if (sortCollator.compare(a.substring(0, mid), b.substring(0, mid)) == 0)
                start = mid;
            else
                end = mid - 1;
        }
        return start;
    }

    private int findInsertionPointIndex(String token, final AtomicBoolean interrupted) {
        String orig_token = token;
        token = normalizeToken(token);

        int start = 0;
        int end = sortedIndexEntries.size();

        final Comparator<Object> sortCollator = sortLanguage.getCollator();
        while (start < end) {
            final int mid = (start + end) / 2;
            if (interrupted != null && interrupted.get()) {
                return -1;
            }
            final IndexEntry midEntry = sortedIndexEntries.get(mid);

            int comp = NormalizeComparator.compareWithoutDash(token, midEntry.normalizedToken(), sortCollator, dict.dictFileVersion);
            if (comp == 0)
                comp = sortCollator.compare(token, midEntry.normalizedToken());
            if (comp == 0) {
                start = end = mid;
                break;
            } else if (comp < 0) {
                // System.out.println("Upper bound: " + midEntry + ", norm=" +
                // midEntry.normalizedToken() + ", mid=" + mid);

                // Hack for robustness if sort order is broken
                if (mid + 2 < end &&
                    compareIdx(token, sortCollator, mid + 1) > 0 &&
                    compareIdx(token, sortCollator, mid + 2) > 0) {
                    start = mid;
                } else {
                    end = mid;
                }
            } else {
                // System.out.println("Lower bound: " + midEntry + ", norm=" +
                // midEntry.normalizedToken() + ", mid=" + mid);

                // Hack for robustness if sort order is broken
                if (mid - 2 >= start &&
                    compareIdx(token, sortCollator, mid - 1) < 0 &&
                    compareIdx(token, sortCollator, mid - 2) < 0) {
                    end = mid + 1;
                } else {
                    start = mid + 1;
                }
            }
        }

        // if the word before is the better match, move
        // our result to it.
        // This fixes up the binary search result if no match is found.
        if (start > 0 && start < sortedIndexEntries.size()) {
            String prev = sortedIndexEntries.get(start - 1).normalizedToken();
            String next = sortedIndexEntries.get(start).normalizedToken();
            if (findMatchLen(sortCollator, token, prev) >= findMatchLen(sortCollator, token, next))
                start--;
        }

        // If we search for a substring of a string that's in there, return
        // that.
        // This fixes up the binary search result if there are multiple entries
        // that compare equal in sort order so we go to the first one.
        int result = Math.min(start, sortedIndexEntries.size() - 1);
        result = windBackCase(sortedIndexEntries.get(result).normalizedToken(), result, interrupted);

        // If the search term was normalized, try to find an exact match first.
        // This only searches downward, so it is important that the previous
        // steps gave resulted in the very first potential candidate.
        if (!orig_token.equalsIgnoreCase(token)) {
            int matchLen = findMatchLen(sortCollator, token, sortedIndexEntries.get(start).normalizedToken());
            int scan = result;
            while (scan >= 0 && scan < sortedIndexEntries.size()) {
                IndexEntry e = sortedIndexEntries.get(scan);
                if (e.token.equalsIgnoreCase(orig_token))
                {
                    return scan;
                }
                if (matchLen > findMatchLen(sortCollator, token, e.normalizedToken()))
                    break;
                if (interrupted != null && interrupted.get()) return start;
                scan++;
            }
        }

        return result;
    }

    private int windBackCase(final String token, int result, final AtomicBoolean interrupted) {
        while (result > 0 && sortedIndexEntries.get(result - 1).normalizedToken().equals(token)) {
            --result;
            if (interrupted != null && interrupted.get()) {
                return result;
            }
        }
        return result;
    }

    public IndexInfo getIndexInfo() {
        return new IndexInfo(shortName, sortedIndexEntries.size(), mainTokenCount);
    }

    private static final int MAX_SEARCH_ROWS = 1000;

    private final Map<String, Integer> prefixToNumRows = new HashMap<>();

    private synchronized int getUpperBoundOnRowsStartingWith(final String normalizedPrefix,
                                                             final int maxRows, final AtomicBoolean interrupted) {
        final Integer numRows = prefixToNumRows.get(normalizedPrefix);
        if (numRows != null) {
            return numRows;
        }
        final int insertionPointIndex = findInsertionPointIndex(normalizedPrefix, interrupted);

        int rowCount = 0;
        for (int index = insertionPointIndex; index < sortedIndexEntries.size(); ++index) {
            if (interrupted != null && interrupted.get()) {
                return -1;
            }
            final IndexEntry indexEntry = sortedIndexEntries.get(index);
            if (!indexEntry.normalizedToken.startsWith(normalizedPrefix) &&
                !NormalizeComparator.withoutDash(indexEntry.normalizedToken).startsWith(normalizedPrefix)) {
                break;
            }
            rowCount += indexEntry.numRows + indexEntry.htmlEntries.size();
            if (rowCount > maxRows) {
                System.out.println("Giving up, too many words with prefix: " + normalizedPrefix);
                break;
            }
        }
        prefixToNumRows.put(normalizedPrefix, rowCount);
        return rowCount;
    }

    public List<RowBase> multiWordSearch(
            final String searchText, final List<String> searchTokens,
            final AtomicBoolean interrupted) {
        final long startMills = System.currentTimeMillis();
        final List<RowBase> result = new ArrayList<>();

        final Set<String> normalizedNonStoplist = new HashSet<>();

        String bestPrefix = null;
        int leastRows = Integer.MAX_VALUE;
        for (int i = 0; i < searchTokens.size(); ++i) {
            if (interrupted != null && interrupted.get()) {
                return result;
            }
            final String searchToken = searchTokens.get(i);
            final String normalized = normalizeToken(searchToken);
            // Normalize them all.
            searchTokens.set(i, normalized);

            if (!stoplist.contains(searchToken)) {
                if (normalizedNonStoplist.add(normalized)) {
                    final int numRows = getUpperBoundOnRowsStartingWith(normalized,
                                        MAX_SEARCH_ROWS, interrupted);
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
        }
        String searchTokensRegex = searchTokens.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("[\\s]*"));
        final Pattern pattern = Pattern.compile(searchTokensRegex);

        if (bestPrefix == null) {
            bestPrefix = searchTokens.get(0);
            System.out.println("Everything was in the stoplist!");
        }
        System.out.println("Searching using prefix: " + bestPrefix + ", leastRows=" + leastRows
                           + ", searchTokens=" + searchTokens);

        // Place to store the things that match.
        final Map<RowMatchType, List<RowBase>> matches = new EnumMap<>(
                RowMatchType.class);
        for (final RowMatchType rowMatchType : RowMatchType.values()) {
            if (rowMatchType != RowMatchType.NO_MATCH) {
                matches.put(rowMatchType, new ArrayList<>());
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
        final Set<RowKey> rowsAlreadySeen = new HashSet<>();
        for (int index = insertionPointIndex; index < sortedIndexEntries.size()
                && matchCount < MAX_SEARCH_ROWS; ++index) {
            if (interrupted != null && interrupted.get()) {
                return result;
            }
            final IndexEntry indexEntry = sortedIndexEntries.get(index);
            if (!indexEntry.normalizedToken.startsWith(searchToken) &&
                !NormalizeComparator.withoutDash(indexEntry.normalizedToken).startsWith(searchToken)) {
                break;
            }

            // System.out.println("Searching indexEntry: " + indexEntry.token);

            // Extra +1 to skip token row.
            for (int rowIndex = indexEntry.startRow + 1; rowIndex < indexEntry.startRow + 1
                    + indexEntry.numRows
                    && rowIndex < rows.size(); ++rowIndex) {
                if (interrupted != null && interrupted.get()) {
                    return result;
                }
                final RowBase row = rows.get(rowIndex);
                final RowBase.RowKey rowKey = row.getRowKey();
                if (rowsAlreadySeen.contains(rowKey)) {
                    continue;
                }
                rowsAlreadySeen.add(rowKey);
                final RowMatchType matchType = row.matches(searchTokens, pattern, normalizer(),
                                               swapPairEntries);
                if (matchType != RowMatchType.NO_MATCH) {
                    matches.get(matchType).add(row);
                    ++matchCount;
                }
            }
        }
        // } // searchTokens

        // Sort them into a reasonable order.
        final RowBase.LengthComparator lengthComparator = new RowBase.LengthComparator(
            swapPairEntries);
        for (final Collection<RowBase> rows : matches.values()) {
            final List<RowBase> ordered = new ArrayList<>(rows);
            ordered.sort(lengthComparator);
            result.addAll(ordered);
        }

        System.out.println("searchDuration: " + (System.currentTimeMillis() - startMills));
        return result;
    }

    private String normalizeToken(final String searchToken) {
        if (TransliteratorManager.init(null, null)) {
            final Transliterator normalizer = normalizer();
            return normalizer.transliterate(searchToken);
        } else {
            // Do our best since the Transliterators aren't up yet.
            return searchToken.toLowerCase(Locale.US);
        }
    }

}
