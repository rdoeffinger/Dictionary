package com.hughes.android.dictionary.engine;

import com.hughes.util.raf.RAFListSerializer;
import com.hughes.util.raf.RAFSerializable;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.regex.Pattern;

public class HtmlEntry extends AbstractEntry implements RAFSerializable<HtmlEntry>, Comparable<HtmlEntry> {
  
  // Both are HTML escaped already.
  public final String title;
  public String html;
  
  public HtmlEntry(final EntrySource entrySource, String title) {
    super(entrySource);
    this.title = title;
  }
  
  public HtmlEntry(Dictionary dictionary, RandomAccessFile raf, final int index) throws IOException {
    super(dictionary, raf, index);
    title = raf.readUTF();
    html = raf.readUTF();
  }
  @Override
  public void write(RandomAccessFile raf) throws IOException {
    super.write(raf);
    raf.writeUTF(title);
    raf.writeUTF(html);
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
    return title + ":\n" + html;
  }

  
  @Override
  public int compareTo(HtmlEntry another) {
    if (title.compareTo(another.title) != 0) {
      return title.compareTo(another.title);
    }
    return html.compareTo(another.html);
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
      out.println("HtmlEntry (shortened): " + entry.title);
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

    public static String htmlBody(final List<HtmlEntry> htmlEntries) {
        final StringBuilder result = new StringBuilder();
        for (final HtmlEntry htmlEntry : htmlEntries) {
            result.append(String.format("<h1>%s</h1>%s\n", htmlEntry.title, htmlEntry.html));
        }
        return result.toString();
    }

}
