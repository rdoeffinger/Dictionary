
package com.hughes.android.dictionary.engine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.SoftReference;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import com.hughes.util.StringUtil;
import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.RAFListSerializerSkippable;
import com.ibm.icu.text.Transliterator;

public class HtmlEntry extends AbstractEntry implements Comparable<HtmlEntry> {

    // Title is not HTML escaped.
    public final String title;
    private final LazyHtmlLoader lazyHtmlLoader;
    @SuppressWarnings("WeakerAccess")
    public String html;

    public HtmlEntry(final EntrySource entrySource, String title) {
        super(entrySource);
        this.title = title;
        lazyHtmlLoader = null;
    }

    public HtmlEntry(Dictionary dictionary, FileChannel ch, DataInput raf, final int index)
    throws IOException {
        super(dictionary, raf, index);
        title = raf.readUTF();
        lazyHtmlLoader = new LazyHtmlLoader(ch, raf, dictionary.htmlData, index);
        html = null;
    }

    private void writeBase(DataOutput raf) throws IOException {
        super.write(raf);
        raf.writeUTF(title);
    }

    private void writeData(DataOutput raf) throws IOException {
        final byte[] bytes = getHtml().getBytes(StandardCharsets.UTF_8);
        StringUtil.writeVarInt(raf, bytes.length);
        raf.write(bytes);
    }

    private static byte[] readData(DataInput raf) throws IOException {
        int len = StringUtil.readVarInt(raf);
        final byte[] bytes = new byte[Math.min(len, 20 * 1024 * 1024)];
        raf.readFully(bytes);
        return bytes;
    }

    String getHtml() {
        return html != null ? html : lazyHtmlLoader.getHtml();
    }

    @Override
    public void addToDictionary(Dictionary dictionary) {
        assert index == -1;
        dictionary.htmlEntries.add(this);
        index = dictionary.htmlEntries.size() - 1;
    }

    @Override
    public RowBase CreateRow(int rowIndex, Index dictionaryIndex) {
        return new Row(this.index, rowIndex, dictionaryIndex);
    }

    static final class Serializer implements RAFListSerializerSkippable<HtmlEntry> {

        final Dictionary dictionary;
        final FileChannel ch;

        Serializer(Dictionary dictionary, FileChannel ch) {
            this.dictionary = dictionary;
            this.ch = ch;
        }

        @Override
        public HtmlEntry read(DataInput raf, final int index) throws IOException {
            return new HtmlEntry(dictionary, ch, raf, index);
        }

        @Override
        public void skip(DataInput raf, final int index) throws IOException {
            if (dictionary.dictFileVersion >= 7)
            {
                StringUtil.readVarInt(raf);
            }
            else
            {
                raf.skipBytes(2);
            }
            int l = raf.readUnsignedShort();
            raf.skipBytes(l);
	}

        @Override
        public void write(DataOutput raf, HtmlEntry t) throws IOException {
            t.writeBase(raf);
        }
    }

    static final class DataSerializer implements RAFListSerializer<HtmlEntry> {
        @Override
        public HtmlEntry read(DataInput raf, final int index) {
            assert false;
            return null;
        }

        @Override
        public void write(DataOutput raf, HtmlEntry t) throws IOException {
            t.writeData(raf);
        }
    }

    static final class DataDeserializer implements RAFListSerializer<byte[]> {
        @Override
        public byte[] read(DataInput raf, final int index) throws IOException {
            return HtmlEntry.readData(raf);
        }

        @Override
        public void write(DataOutput raf, byte[] t) {
            assert false;
        }
    }

    private String getRawText(final boolean compact) {
        return title + ":\n" + getHtml();
    }

    @Override
    public int compareTo(/*@NonNull*/ HtmlEntry another) {
        if (title.compareTo(another.title) != 0) {
            return title.compareTo(another.title);
        }
        return getHtml().compareTo(another.getHtml());
    }

    @Override
    public String toString() {
        return getRawText(false);
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

        public HtmlEntry getEntry() {
            return index.dict.htmlEntries.get(referenceIndex);
        }

        @Override
        public void print(PrintStream out) {
            final HtmlEntry entry = getEntry();
            out.println("See also HtmlEntry:" + entry.title);
        }

        @Override
        public String getRawText(boolean compact) {
            final HtmlEntry entry = getEntry();
            return entry.getRawText(compact);
        }

        @Override
        public RowMatchType matches(final List<String> searchTokens,
                                    final Pattern orderedMatchPattern, final Transliterator normalizer,
                                    final boolean swapPairEntries) {
            final String text = normalizer.transform(getRawText(false));
            if (orderedMatchPattern.matcher(text).find()) {
                return RowMatchType.ORDERED_MATCH;
            }
            for (int i = searchTokens.size() - 1; i >= 0; --i) {
                final String searchToken = searchTokens.get(i);
                if (!text.contains(searchToken)) {
                    return RowMatchType.NO_MATCH;
                }
            }
            return RowMatchType.BAG_OF_WORDS_MATCH;
        }
    }

    public static String htmlBody(final List<HtmlEntry> htmlEntries, final String indexShortName) {
        final StringBuilder result = new StringBuilder();
        for (final HtmlEntry htmlEntry : htmlEntries) {
            final String titleEscaped = StringUtil.escapeUnicodeToPureHtml(htmlEntry.title);
            result.append(String.format("<h1><a href=\"%s\">%s</a></h1>\n<p>%s\n",
                                        formatQuickdicUrl(indexShortName, htmlEntry.title), titleEscaped,
                                        htmlEntry.getHtml()));
        }
        return result.toString();
    }

    @SuppressWarnings("WeakerAccess")
    public static String formatQuickdicUrl(final String indexShortName, final String text) {
        assert !indexShortName.contains(":");
        assert text.length() > 0;
        return String.format("q://d?%s&%s", indexShortName, StringUtil.encodeForUrl(text));
    }

    public static boolean isQuickdicUrl(String url) {
        return url.startsWith("q://d?");
    }

    // --------------------------------------------------------------------

    @SuppressWarnings("WeakerAccess")
    public static final class LazyHtmlLoader {
        final DataInput raf;
        final FileChannel ch;
        final long offset;
        final int numBytes;
        final int numZipBytes;
        final List<byte[]> data;
        final int index;

        // Not sure this volatile is right, but oh well.
        volatile SoftReference<String> htmlRef = new SoftReference<>(null);

        private LazyHtmlLoader(FileChannel ch, final DataInput inp, List<byte[]> data, int index) throws IOException {
            this.data = data;
            this.index = index;
            if (data != null) {
                this.raf = null;
                this.ch = null;
                this.offset = 0;
                this.numBytes = -1;
                this.numZipBytes = -1;
                return;
            }
            raf = inp;
            this.ch = ch;
            numBytes = Math.min(raf.readInt(), 20 * 1024 * 1024);
            numZipBytes = Math.min(raf.readInt(), 20 * 1024 * 1024);
            offset = ch.position();
            raf.skipBytes(numZipBytes);
        }

        String getHtml() {
            String html = htmlRef.get();
            if (html != null) {
                return html;
            }
            if (data != null) {
                html = new String(data.get(index), StandardCharsets.UTF_8);
                htmlRef = new SoftReference<>(html);
                return html;
            }
            System.out.println("Loading Html: numBytes=" + numBytes + ", numZipBytes="
                               + numZipBytes);
            final byte[] zipBytes = new byte[numZipBytes];
            synchronized (ch) {
                try {
                    ch.position(offset);
                    raf.readFully(zipBytes);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read HTML data from dictionary", e);
                }
            }
            try {
                final byte[] bytes = StringUtil.unzipFully(zipBytes, numBytes);
                html = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Dictionary HTML data corrupted", e);
            }
            htmlRef = new SoftReference<>(html);
            return html;
        }
    }

}
