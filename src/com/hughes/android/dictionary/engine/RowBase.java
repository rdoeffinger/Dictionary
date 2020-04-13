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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.hughes.util.IndexedObject;
import com.hughes.util.raf.RAFListSerializer;
import com.ibm.icu.text.Transliterator;

public abstract class RowBase extends IndexedObject {
    /**
     * the Index owning this RowBase.
     */
    final Index index;

    /**
     * Where this RowBase points to.
     */
    public final int referenceIndex;

    /**
     * the TokenRow above this RowBase, populated on demand.
     */
    private TokenRow tokenRow = null;

    RowBase(final DataInput raf, final int thisRowIndex, final Index index, final int extra)
    throws IOException {
        super(thisRowIndex);
        this.index = index;
        this.referenceIndex = extra == -1 ? raf.readInt() : ((extra << 16) + raf.readUnsignedShort()); // what this points to.
    }

    RowBase(final int referenceIndex, final int thisRowIndex, final Index index) {
        super(thisRowIndex);
        this.index = index;
        this.referenceIndex = referenceIndex;
    }

    static final class RowKey {
        final Class<? extends RowBase> rowClass;
        final int referenceIndex;

        private RowKey(Class<? extends RowBase> rowClass, int referenceIndex) {
            this.rowClass = rowClass;
            this.referenceIndex = referenceIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RowKey)) {
                return false;
            }
            final RowKey that = (RowKey) o;
            return this.referenceIndex == that.referenceIndex
                   && this.rowClass.equals(that.rowClass);
        }

        @Override
        public int hashCode() {
            return rowClass.hashCode() + referenceIndex;
        }
    }

    public RowKey getRowKey() {
        return new RowKey(this.getClass(), referenceIndex);
    }

    /**
     * @return the TokenRow that this row is "filed under".
     */
    public TokenRow getTokenRow(final boolean search) {
        if (tokenRow == null && search) {
            int r = index() - 1;
            int rUp = index() + 1;
            while (r >= 0) {
                final RowBase row = index.rows.get(r);
                final TokenRow candidate = row.getTokenRow(false);
                if (candidate != null) {
                    for (++r; r <= index(); ++r) {
                        index.rows.get(r).setTokenRow(candidate);
                    }
                    break;
                }
                if (rUp < index.rows.size()) {
                    final RowBase rowUp = index.rows.get(rUp);
                    TokenRow candidateUp = rowUp.getTokenRow(false);
                    if (candidateUp != null) {
                        // Did we hit the next set of TokenRows?
                        if (candidateUp.index() > this.index()) {
                            final int tokenIndex = index.sortedIndexEntries
                                                   .get(candidateUp.referenceIndex - 1).startRow;
                            candidateUp = (TokenRow) index.rows.get(tokenIndex);
                        }
                        for (--rUp; rUp >= index(); --rUp) {
                            index.rows.get(rUp).setTokenRow(candidateUp);
                        }
                        break;
                    }
                    rUp++;
                }
                --r;
            }
            assert tokenRow != null;
        }
        return tokenRow;
    }

    void setTokenRow(TokenRow tokenRow) {
        assert this.tokenRow == null;
        assert tokenRow != null;
        this.tokenRow = tokenRow;
    }

    public abstract void print(PrintStream out);

    public abstract String getRawText(final boolean compact);

    public abstract RowMatchType matches(final List<String> searchTokens,
                                         final Pattern orderedMatch, final Transliterator normalizer, boolean swapPairEntries);

    // RowBase must manage "disk-based" polymorphism. All other polymorphism is
    // dealt with in the normal manner.
    static class Serializer implements RAFListSerializer<RowBase> {

        final Index index;

        Serializer(final Index index) {
            this.index = index;
        }

        @Override
        public RowBase read(DataInput raf, final int listIndex) throws IOException {
            int rowType = raf.readUnsignedByte();
            int extra = -1;
            if (rowType >= 0x20) {
                extra = rowType & 0x1f;
                rowType = (rowType >> 5) - 1;
            }
            switch (rowType) {
                case 0:
                    return new PairEntry.Row(raf, listIndex, index, extra);
                case 1:
                case 3:
                    return new TokenRow(raf, listIndex, index, /* hasMainEntry */rowType == 1, extra);
                case 2:
                    return new TextEntry.Row(raf, listIndex, index, extra);
                case 4:
                    return new HtmlEntry.Row(raf, listIndex, index, extra);
            }
            throw new RuntimeException("Invalid rowType:" + rowType);
        }

        @Override
        public void write(DataOutput raf, RowBase t) throws IOException {
            int type = 0;
            if (t instanceof PairEntry.Row) {
                type = 0;
            } else if (t instanceof TokenRow) {
                final TokenRow tokenRow = (TokenRow) t;
                type = tokenRow.hasMainEntry ? 1 : 3;
            } else if (t instanceof TextEntry.Row) {
                type = 2;
            } else if (t instanceof HtmlEntry.Row) {
                type = 4;
            }
            assert t.referenceIndex < (1 << 21);
            if ((t.referenceIndex >> 16) >= (1 << 5))
                throw new RuntimeException("referenceIndex larger than supported max");
            raf.writeByte(((type + 1) << 5) + (t.referenceIndex >> 16));
            raf.writeShort(t.referenceIndex);
        }
    }

    public static final class LengthComparator implements Comparator<RowBase> {

        final boolean swapPairEntries;

        public LengthComparator(boolean swapPairEntries) {
            this.swapPairEntries = swapPairEntries;
        }

        @Override
        public int compare(RowBase row1, RowBase row2) {
            final int l1 = row1.getSideLength(swapPairEntries);
            final int l2 = row2.getSideLength(swapPairEntries);
            return l1 < l2 ? -1 : l1 == l2 ? 0 : 1;
        }
    }

    int getSideLength(boolean swapPairEntries) {
        return getRawText(false).length();
    }

}
