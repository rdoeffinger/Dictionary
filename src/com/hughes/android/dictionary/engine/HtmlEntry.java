package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.regex.Pattern;

import com.hughes.android.dictionary.engine.PairEntry.Pair;
import com.hughes.util.raf.RAFSerializable;
import com.hughes.util.raf.RAFSerializer;
import com.ibm.icu.text.Transliterator;

public class HtmlEntry extends AbstractEntry implements RAFSerializable<HtmlEntry>, Comparable<HtmlEntry> {
  
  final String title;
  final String html;

  public HtmlEntry(Dictionary dictionary, RandomAccessFile raf) throws IOException {
    super(dictionary, raf);
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
  public int addToDictionary(Dictionary dictionary) {
    dictionary.htmlEntries.add(this);
    return dictionary.htmlEntries.size() - 1;
  }
  
  static final class Serializer implements RAFSerializer<HtmlEntry> {
    
    final Dictionary dictionary;
    
    Serializer(Dictionary dictionary) {
      this.dictionary = dictionary;
    }

    @Override
    public HtmlEntry read(RandomAccessFile raf) throws IOException {
      return new HtmlEntry(dictionary, raf);
    }

    @Override
    public void write(RandomAccessFile raf, HtmlEntry t) throws IOException {
      t.write(raf);
    }
  };

  public String getRawText(final boolean compact) {
    return title + ": " + html;
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
      out.println(entry);
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

}
