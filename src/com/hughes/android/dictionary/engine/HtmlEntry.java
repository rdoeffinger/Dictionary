package com.hughes.android.dictionary.engine;

import android.content.Intent;

import com.hughes.android.dictionary.C;
import com.hughes.util.StringUtil;
import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.RAFSerializable;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.regex.Pattern;

public class HtmlEntry extends AbstractEntry implements RAFSerializable<HtmlEntry>, Comparable<HtmlEntry> {
  
  // Title is not HTML escaped.
  public final String title;
  public final LazyHtmlLoader lazyHtmlLoader;
  public String html;
  
  public HtmlEntry(final EntrySource entrySource, String title) {
    super(entrySource);
    this.title = title;
    lazyHtmlLoader = null;
  }
  
  public HtmlEntry(Dictionary dictionary, RandomAccessFile raf, final int index) throws IOException {
    super(dictionary, raf, index);
    title = raf.readUTF();
    lazyHtmlLoader = new LazyHtmlLoader(raf);
    html = null;
  }
  
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    super.write(raf);
    raf.writeUTF(title);

    final byte[] bytes = getHtml().getBytes("UTF-8");
    final byte[] zipBytes = StringUtil.zipBytes(bytes);
    raf.writeInt(bytes.length);
    raf.writeInt(zipBytes.length);
    raf.write(zipBytes);
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

  static final class Serializer implements RAFListSerializer<HtmlEntry> {
    
    final Dictionary dictionary;
    
    Serializer(Dictionary dictionary) {
      this.dictionary = dictionary;
    }

    @Override
    public HtmlEntry read(RandomAccessFile raf, final int index) throws IOException {
      return new HtmlEntry(dictionary, raf, index);
    }

    @Override
    public void write(RandomAccessFile raf, HtmlEntry t) throws IOException {
      t.write(raf);
    }
  };

  public String getRawText(final boolean compact) {
    return title + ":\n" + getHtml();
  }

  
  @Override
  public int compareTo(HtmlEntry another) {
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
    
    boolean isExpanded = false;
    
    Row(final RandomAccessFile raf, final int thisRowIndex,
        final Index index) throws IOException {
      super(raf, thisRowIndex, index);
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
    public RowMatchType matches(final List<String> searchTokens, final Pattern orderedMatchPattern, final Transliterator normalizer, final boolean swapPairEntries) {
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
                    formatQuickdicUrl(indexShortName, titleEscaped), titleEscaped,
                    htmlEntry.getHtml()));
        }
        return result.toString();
    }
    
    public static String formatQuickdicUrl(final String indexShortName, final String text) {
        assert !indexShortName.contains(":");
        assert text.length() > 0;
        return String.format("q://d?%s&%s", indexShortName, StringUtil.encodeForUrl(text));
    }
    
    public static boolean isQuickdicUrl(String url) {
        return url.startsWith("q://d?");
    }
    
    public static void quickdicUrlToIntent(final String url, final Intent intent) {
        int firstColon = url.indexOf("?");
        if (firstColon == -1) return;
        int secondColon = url.indexOf("&", firstColon + 1);
        if (secondColon == -1) return;
        intent.putExtra(C.SEARCH_TOKEN, StringUtil.decodeFromUrl(url.substring(secondColon + 1)));
    }
    
    // --------------------------------------------------------------------
    
    public static final class LazyHtmlLoader {
        final RandomAccessFile raf;
        final long offset;
        final int numBytes;
        final int numZipBytes;
        
        // Not sure this volatile is right, but oh well.
        volatile SoftReference<String> htmlRef = new SoftReference<String>(null);
        
        private LazyHtmlLoader(final RandomAccessFile raf) throws IOException {
            this.raf = raf;
            numBytes = raf.readInt();
            numZipBytes = raf.readInt();
            offset = raf.getFilePointer();
            raf.skipBytes(numZipBytes);
        }
        
        public String getHtml() {
            String html = htmlRef.get();
            if (html != null) {
                return html;
            }
            System.out.println("Loading Html: numBytes=" + numBytes + ", numZipBytes=" + numZipBytes);
            final byte[] bytes = new byte[numBytes];
            final byte[] zipBytes = new byte[numZipBytes];
            synchronized (raf) {
                try {
                    raf.seek(offset);
                    raf.read(zipBytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                StringUtil.unzipFully(zipBytes, bytes);
                html = new String(bytes, "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            htmlRef = new SoftReference<String>(html);
            return html;
        }
    }

}
