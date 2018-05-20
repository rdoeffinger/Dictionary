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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
