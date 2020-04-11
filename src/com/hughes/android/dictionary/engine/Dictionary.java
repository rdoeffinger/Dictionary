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

import com.hughes.android.dictionary.DictionaryInfo;
import com.hughes.util.CachingList;
import com.hughes.util.raf.RAFList;
import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.RAFSerializable;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class Dictionary implements RAFSerializable<Dictionary> {

    private static final int CACHE_SIZE = 5000;

    private static final int CURRENT_DICT_VERSION = 7;
    private static final String END_OF_DICTIONARY = "END OF DICTIONARY";

    // persisted
    final int dictFileVersion;
    private final long creationMillis;
    public final String dictInfo;
    public final List<PairEntry> pairEntries;
    public final List<TextEntry> textEntries;
    public final List<HtmlEntry> htmlEntries;
    public final List<byte[]> htmlData;
    public final List<EntrySource> sources;
    public final List<Index> indices;

    /**
     * dictFileVersion 1 adds: <li>links to sources? dictFileVersion 2 adds: <li>
     * counts of tokens in indices.
     */

    public Dictionary(final String dictInfo) {
        this.dictFileVersion = CURRENT_DICT_VERSION;
        this.creationMillis = System.currentTimeMillis();
        this.dictInfo = dictInfo;
        pairEntries = new ArrayList<>();
        textEntries = new ArrayList<>();
        htmlEntries = new ArrayList<>();
        htmlData = null;
        sources = new ArrayList<>();
        indices = new ArrayList<>();
    }

    public Dictionary(final FileChannel ch) throws IOException {
        DataInput raf = new DataInputStream(Channels.newInputStream(ch));
        dictFileVersion = raf.readInt();
        if (dictFileVersion < 0 || dictFileVersion > CURRENT_DICT_VERSION) {
            throw new IOException("Invalid dictionary version: " + dictFileVersion);
        }
        creationMillis = raf.readLong();
        dictInfo = raf.readUTF();

        // Load the sources, then seek past them, because reading them later
        // disrupts the offset.
        try {
            final RAFList<EntrySource> rafSources = RAFList.create(ch, new EntrySource.Serializer(
                    this), ch.position(), dictFileVersion, dictInfo + " sources: ");
            sources = new ArrayList<>(rafSources);
            ch.position(rafSources.getEndOffset());

            pairEntries = CachingList.create(
                              RAFList.create(ch, new PairEntry.Serializer(this), ch.position(), dictFileVersion, dictInfo + " pairs: "),
                              CACHE_SIZE, false);
            textEntries = CachingList.create(
                              RAFList.create(ch, new TextEntry.Serializer(this), ch.position(), dictFileVersion, dictInfo + " text: "),
                              CACHE_SIZE, true);
            if (dictFileVersion >= 5) {
                htmlEntries = CachingList.create(
                                  RAFList.create(ch, new HtmlEntry.Serializer(this, ch), ch.position(), dictFileVersion, dictInfo + " html: "),
                                  CACHE_SIZE, false);
            } else {
                htmlEntries = Collections.emptyList();
            }
            if (dictFileVersion >= 7) {
                htmlData = RAFList.create(ch, new HtmlEntry.DataDeserializer(), ch.position(), dictFileVersion, dictInfo + " html: ");
            } else {
                htmlData = null;
            }
            indices = CachingList.createFullyCached(RAFList.create(ch, new IndexSerializer(ch),
                                                    ch.position(), dictFileVersion, dictInfo + " index: "));
        } catch (RuntimeException e) {
            throw new IOException("RuntimeException loading dictionary", e);
        }
        final String end = raf.readUTF();
        if (!end.equals(END_OF_DICTIONARY)) {
            throw new IOException("Dictionary seems corrupt: " + end);
        }
    }

    @Override
    public void write(DataOutput out) throws IOException {
        RandomAccessFile raf = (RandomAccessFile)out;
        if (dictFileVersion < 7) throw new RuntimeException("write function cannot write formats older than v7!");
        raf.writeInt(dictFileVersion);
        raf.writeLong(creationMillis);
        raf.writeUTF(dictInfo);
        System.out.println("sources start: " + raf.getFilePointer());
        RAFList.write(raf, sources, new EntrySource.Serializer(this));
        System.out.println("pair start: " + raf.getFilePointer());
        RAFList.write(raf, pairEntries, new PairEntry.Serializer(this), 64, true);
        System.out.println("text start: " + raf.getFilePointer());
        RAFList.write(raf, textEntries, new TextEntry.Serializer(this));
        System.out.println("html index start: " + raf.getFilePointer());
        RAFList.write(raf, htmlEntries, new HtmlEntry.Serializer(this, null), 64, true);
        System.out.println("html data start: " + raf.getFilePointer());
        assert htmlData == null;
        RAFList.write(raf, htmlEntries, new HtmlEntry.DataSerializer(), 128, true);
        System.out.println("indices start: " + raf.getFilePointer());
        RAFList.write(raf, indices, new IndexSerializer(null));
        System.out.println("end: " + raf.getFilePointer());
        raf.writeUTF(END_OF_DICTIONARY);
    }

    private void writev6Sources(RandomAccessFile out) throws IOException {
        out.writeInt(sources.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + sources.size() * 8 + 8);
        for (EntrySource s : sources) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeUTF(s.getName());
            out.writeInt(s.getNumEntries());
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6PairEntries(RandomAccessFile out) throws IOException {
        out.writeInt(pairEntries.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + pairEntries.size() * 8 + 8);
        for (PairEntry pe : pairEntries) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeShort(pe.entrySource.index());
            out.writeInt(pe.pairs.size());
            for (PairEntry.Pair p : pe.pairs) {
                out.writeUTF(p.lang1);
                out.writeUTF(p.lang2);
            }
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6TextEntries(RandomAccessFile out) throws IOException {
        out.writeInt(textEntries.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + textEntries.size() * 8 + 8);
        for (TextEntry t : textEntries) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeShort(t.entrySource.index());
            out.writeUTF(t.text);
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6HtmlEntries(RandomAccessFile out) throws IOException {
        out.writeInt(htmlEntries.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + htmlEntries.size() * 8 + 8);
        for (HtmlEntry h : htmlEntries) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeShort(h.entrySource.index());
            out.writeUTF(h.title);
            byte[] data = h.getHtml().getBytes("UTF-8");
            out.writeInt(data.length);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzout = new GZIPOutputStream(baos);
            gzout.write(data);
            gzout.close();
            out.writeInt(baos.size());
            out.write(baos.toByteArray());
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6HtmlIndices(RandomAccessFile out, List<HtmlEntry> entries) throws IOException {
        out.writeInt(entries.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + entries.size() * 8 + 8);
        for (HtmlEntry e : entries) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeInt(e.index());
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6IndexEntries(RandomAccessFile out, List<Index.IndexEntry> entries) throws IOException {
        out.writeInt(entries.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + entries.size() * 8 + 8);
        for (Index.IndexEntry e : entries) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeUTF(e.token);
            out.writeInt(e.startRow);
            out.writeInt(e.numRows);
            final boolean hasNormalizedForm = !e.token.equals(e.normalizedToken());
            out.writeBoolean(hasNormalizedForm);
            if (hasNormalizedForm) out.writeUTF(e.normalizedToken());
            writev6HtmlIndices(out, e.htmlEntries);
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    private void writev6Index(RandomAccessFile out) throws IOException {
        out.writeInt(indices.size());
        long tocPos = out.getFilePointer();
        out.seek(tocPos + indices.size() * 8 + 8);
        for (Index idx : indices) {
            long dataPos = out.getFilePointer();
            out.seek(tocPos);
            out.writeLong(dataPos);
            tocPos += 8;
            out.seek(dataPos);
            out.writeUTF(idx.shortName);
            out.writeUTF(idx.longName);
            out.writeUTF(idx.sortLanguage.getIsoCode());
            out.writeUTF(idx.normalizerRules);
            out.writeBoolean(idx.swapPairEntries);
            out.writeInt(idx.mainTokenCount);
            writev6IndexEntries(out, idx.sortedIndexEntries);

            // write stoplist, serializing the whole Set *shudder*
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(idx.stoplist);
            oos.close();
            final byte[] bytes = baos.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);

            out.writeInt(idx.rows.size());
            out.writeInt(5);
            for (RowBase r : idx.rows) {
                int type = 0;
                if (r instanceof PairEntry.Row) {
                    type = 0;
                } else if (r instanceof TokenRow) {
                    final TokenRow tokenRow = (TokenRow)r;
                    type = tokenRow.hasMainEntry ? 1 : 3;
                } else if (r instanceof TextEntry.Row) {
                    type = 2;
                } else if (r instanceof HtmlEntry.Row) {
                    type = 4;
                } else {
                    throw new RuntimeException("Row type not supported for v6");
                }
                out.writeByte(type);
                out.writeInt(r.referenceIndex);
            }
        }
        long dataPos = out.getFilePointer();
        out.seek(tocPos);
        out.writeLong(dataPos);
        out.seek(dataPos);
    }

    public void writev6(DataOutput out) throws IOException {
        RandomAccessFile raf = (RandomAccessFile)out;
        raf.writeInt(6);
        raf.writeLong(creationMillis);
        raf.writeUTF(dictInfo);
        System.out.println("sources start: " + raf.getFilePointer());
        writev6Sources(raf);
        System.out.println("pair start: " + raf.getFilePointer());
        writev6PairEntries(raf);
        System.out.println("text start: " + raf.getFilePointer());
        writev6TextEntries(raf);
        System.out.println("html index start: " + raf.getFilePointer());
        writev6HtmlEntries(raf);
        System.out.println("indices start: " + raf.getFilePointer());
        writev6Index(raf);
        System.out.println("end: " + raf.getFilePointer());
        raf.writeUTF(END_OF_DICTIONARY);
    }

    private final class IndexSerializer implements RAFListSerializer<Index> {
        private final FileChannel ch;

        IndexSerializer(FileChannel ch) {
            this.ch = ch;
        }

        @Override
        public Index read(DataInput raf, final int readIndex) throws IOException {
            return new Index(Dictionary.this, ch, raf);
        }

        @Override
        public void write(DataOutput raf, Index t) throws IOException {
            t.write(raf);
        }
    }

    final RAFListSerializer<HtmlEntry> htmlEntryIndexSerializer = new RAFListSerializer<HtmlEntry>() {
        @Override
        public void write(DataOutput raf, HtmlEntry t) {
            assert false;
        }

        @Override
        public HtmlEntry read(DataInput raf, int readIndex) throws IOException {
            return htmlEntries.get(raf.readInt());
        }
    };

    public void print(final PrintStream out) {
        out.println("dictInfo=" + dictInfo);
        for (final EntrySource entrySource : sources) {
            out.printf("EntrySource: %s %d\n", entrySource.name, entrySource.numEntries);
        }
        out.println();
        for (final Index index : indices) {
            out.printf("Index: %s %s\n", index.shortName, index.longName);
            index.print(out);
            out.println();
        }
    }

    public DictionaryInfo getDictionaryInfo() {
        final DictionaryInfo result = new DictionaryInfo();
        result.creationMillis = this.creationMillis;
        result.dictInfo = this.dictInfo;
        for (final Index index : indices) {
            result.indexInfos.add(index.getIndexInfo());
        }
        return result;
    }

    public static DictionaryInfo getDictionaryInfo(final File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            final Dictionary dict = new Dictionary(raf.getChannel());
            final DictionaryInfo dictionaryInfo = dict.getDictionaryInfo();
            dictionaryInfo.uncompressedFilename = file.getName();
            dictionaryInfo.uncompressedBytes = file.length();
            raf.close();
            return dictionaryInfo;
        } catch (IOException e) {
            final DictionaryInfo dictionaryInfo = new DictionaryInfo();
            dictionaryInfo.uncompressedFilename = file.getName();
            dictionaryInfo.uncompressedBytes = file.length();
            return dictionaryInfo;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
