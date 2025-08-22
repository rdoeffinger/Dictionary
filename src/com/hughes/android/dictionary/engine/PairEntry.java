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

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hughes.util.StringUtil;
import com.hughes.util.raf.RAFListSerializerSkippable;
import com.ibm.icu.text.Transliterator;

public class PairEntry extends AbstractEntry implements Comparable<PairEntry> {

    public final List<Pair> pairs;

    public PairEntry(final EntrySource entrySource) {
        super(entrySource);
        pairs = new ArrayList<>(1);
    }

    public PairEntry(final EntrySource entrySource, final String lang1, final String lang2) {
        this(entrySource);
        this.pairs.add(new Pair(lang1, lang2));
    }

    public PairEntry(final Dictionary dictionary, final DataInput raf, final int index)
    throws IOException {
        super(dictionary, raf, index);
        final int size = dictionary.dictFileVersion >= 7 ? StringUtil.readVarInt(raf) : raf.readInt();
        // Use singletonList for better performance in common case
        if (size == 1) pairs = Collections.singletonList(new Pair(raf.readUTF(), raf.readUTF()));
        else
        {
            pairs = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                pairs.add(new Pair(raf.readUTF(), raf.readUTF()));
            }
        }
    }

    private void fastWriteUTF(DataOutput raf, String s) throws IOException {
        int pos = 2;
        char[] in = s.toCharArray();
        byte[] out = new byte[in.length * 3 + 2];
        for (char c: in) {
            if (c < 128) out[pos++] = (byte)c;
            else if (c < 2048) {
                out[pos++] = (byte) (0xc0 | (c >> 6));
                out[pos++] = (byte) (0x80 | (c & 0x3f));
            } else {
                out[pos++] = (byte) (0xe0 | (c >> 12));
                out[pos++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                out[pos++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        int utflen = pos - 2;
        assert utflen < 0x10000;
        out[0] = (byte)(utflen >> 8);
        out[1] = (byte)utflen;
        raf.write(out, 0, pos);
    }

    @Override
    public void write(DataOutput raf) throws IOException {
        super.write(raf);
        StringUtil.writeVarInt(raf, pairs.size());
        for (Pair p : pairs) {
            assert !p.lang1.isEmpty();
            fastWriteUTF(raf, p.lang1);
            fastWriteUTF(raf, p.lang2);
        }
    }

    static final class Serializer implements RAFListSerializerSkippable<PairEntry> {

        final Dictionary dictionary;

        Serializer(Dictionary dictionary) {
            this.dictionary = dictionary;
        }

        @Override
        public PairEntry read(DataInput raf, int index) throws IOException {
            return new PairEntry(dictionary, raf, index);
        }

        @Override
        public void skip(DataInput raf, int index) throws IOException {
            final int size;
            if (dictionary.dictFileVersion >= 7)
            {
                StringUtil.readVarInt(raf);
                size = StringUtil.readVarInt(raf);
            }
            else
            {
                raf.skipBytes(2);
                size = raf.readInt();
            }
            for (int i = 0; i < 2*size; ++i) {
                int l = raf.readUnsignedShort();
                raf.skipBytes(l);
            }
        }

        @Override
        public void write(DataOutput raf, PairEntry t) throws IOException {
            t.write(raf);
        }
    }

    @Override
    public void addToDictionary(final Dictionary dictionary) {
        assert index == -1;
        dictionary.pairEntries.add(this);
        index = dictionary.pairEntries.size() - 1;
    }

    @Override
    public RowBase CreateRow(int rowIndex, Index dictionaryIndex) {
        return new Row(this.index, rowIndex, dictionaryIndex);
    }

    // --------------------------------------------------------------------

    public static class Row extends RowBase {

        Row(final DataInput raf, final int thisRowIndex,
            final Index index, int extra) throws IOException {
            super(raf, thisRowIndex, index, extra);
        }

        Row(final int referenceIndex, final int thisRowIndex,
            final Index index) {
            super(referenceIndex, thisRowIndex, index);
        }

        @Override
        public String toString() {
            return getRawText(false);
        }

        public PairEntry getEntry() {
            return index.dict.pairEntries.get(referenceIndex);
        }

        @Override
        public void print(PrintStream out) {
            final PairEntry pairEntry = getEntry();
            for (int i = 0; i < pairEntry.pairs.size(); ++i) {
                out.print((i == 0 ? "  " : "    ") + pairEntry.pairs.get(i));
                out.println();
            }
        }

        @Override
        public String getRawText(boolean compact) {
            final PairEntry pairEntry = getEntry();
            return pairEntry.getRawText(compact);
        }

        @Override
        public RowMatchType matches(final List<String> searchTokens,
                                    final Pattern orderedMatchPattern, final Transliterator normalizer,
                                    final boolean swapPairEntries) {
            final int side = swapPairEntries ? 1 : 0;
            final List<Pair> pairs = getEntry().pairs;
            final String[] pairSides = new String[pairs.size()];
            for (int i = 0; i < pairs.size(); ++i) {
                pairSides[i] = normalizer.transform(pairs.get(i).get(side));
            }
            for (int i = searchTokens.size() - 1; i >= 0; --i) {
                final String searchToken = searchTokens.get(i);
                boolean found = false;
                for (final String pairSide : pairSides) {
                    found |= pairSide.contains(searchToken);
                }
                if (!found) {
                    return RowMatchType.NO_MATCH;
                }
            }
            for (final String pairSide : pairSides) {
                if (orderedMatchPattern.matcher(pairSide).find()) {
                    return RowMatchType.ORDERED_MATCH;
                }
            }
            return RowMatchType.BAG_OF_WORDS_MATCH;
        }

        @Override
        public int getSideLength(boolean swapPairEntries) {
            int result = 0;
            final int side = swapPairEntries ? 1 : 0;
            for (final Pair pair : getEntry().pairs) {
                result += pair.get(side).length();
            }
            return result;
        }

    }

    private String getRawText(final boolean compact) {
        if (compact) {
            return this.pairs.get(0).toStringTab();
        }
        final StringBuilder builder = new StringBuilder();
        final String lang1 = this.pairs.stream()
            .map(e -> e.lang1)
            .collect(Collectors.joining(" | "));
        builder.append(lang1);
        builder.append("\t");
        final String lang2 = this.pairs.stream()
                .map(e -> e.lang2)
                .collect(Collectors.joining(" | "));
        builder.append(lang2);
        return builder.toString();
    }

    @Override
    public int compareTo(/*@NonNull*/ final PairEntry that) {
        return this.getRawText(false).compareTo(that.getRawText(false));
    }

    @Override
    public String toString() {
        return getRawText(false);
    }

    // -----------------------------------------------------------------------

    public static final class Pair {

        public final String lang1;
        public final String lang2;

        @SuppressWarnings("WeakerAccess")
        public Pair(final String lang1, final String lang2) {
            this.lang1 = lang1;
            this.lang2 = lang2;
            assert !lang1.trim().isEmpty() && !lang2.trim().isEmpty() : "Empty pair!!!";
        }

        public Pair(final String lang1, final String lang2, final boolean swap) {
            this(swap ? lang2 : lang1, swap ? lang1 : lang2);
        }

        public String toString() {
            return lang1 + " :: " + lang2;
        }

        String toStringTab() {
            return lang1 + "\t" + lang2;
        }

        public String get(int i) {
            if (i == 0) {
                return lang1;
            } else if (i == 1) {
                return lang2;
            }
            throw new IllegalArgumentException();
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == this) return true;
            if (!(o instanceof Pair)) return false;
            Pair p = (Pair)o;
            return p.lang1.equals(lang1) && p.lang2.equals(lang2);
        }

        @Override
        public int hashCode()
        {
            return (lang1 + "|" + lang2).hashCode();
        }
    }
}
