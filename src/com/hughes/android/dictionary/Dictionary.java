package com.hughes.android.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.hughes.util.FileUtil;
import com.hughes.util.LRUCacheMap;

public final class Dictionary {

  public static final String INDEX_FORMAT = "%s_index_%d";

  private final byte lang;
  private final File dictionaryFile;
  private final RandomAccessFile dictionaryRaf;
  private final Index index;
  
  private final LRUCacheMap<Integer,Row> positionToRow = new LRUCacheMap<Integer, Row>(2000);

  public Dictionary(final String dictionaryFileName, final byte lang)
      throws IOException {
    this.lang = lang;
    this.dictionaryFile = new File(dictionaryFileName);

    dictionaryRaf = new RandomAccessFile(dictionaryFile, "r");
    index = new Index(String.format(INDEX_FORMAT, dictionaryFile
        .getAbsolutePath(), lang));
  }
  
  public int numRows() {
    return index.root.getDescendantCount();
  }
  
  public Row getRow(final int position) throws IOException {
    Row row = positionToRow.get(position);
    if (row == null) {
      final Object o = index.root.getDescendant(position);
      if (o instanceof Integer) {
        final Entry entry = new Entry(FileUtil.readLine(dictionaryRaf, (Integer) o));
        row = new Row(entry.getFormattedEntry(lang), false);
      } else if (o instanceof String) {
        row = new Row((String) o, true);
      } else {
        throw new RuntimeException(o.toString());
      }
      positionToRow.put(position, row);
    }
    return row;
  }
  
  public static class Row {
    final String text;
    final boolean isWord;
    public Row(final String text, final boolean isWord) {
      this.text = text;
      this.isWord = isWord;
    }
  }

}
