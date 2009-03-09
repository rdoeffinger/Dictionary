package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.regex.Pattern;

import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;

public final class Entry implements RAFSerializable<Entry> {

  static final byte LANG1 = 0;
  static final byte LANG2 = 1;

  static final Pattern lineSplitPattern = Pattern.compile("\\s+::\\s+");

  final String lang1;
  final String lang2;
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Entry)) {
      return false;
    }
    final Entry that = (Entry) o;
    return that.lang1.equals(lang1) && that.lang2.equals(lang2);
  }

  @Override
  public int hashCode() {
    return lang1.hashCode() + lang2.hashCode();
  }

  @Override
  public String toString() {
    return getRawText();
  }

  public Entry(String lang1, String lang2) {
    this.lang1 = lang1;
    this.lang2 = lang2;
  }

  public static final RAFFactory<Entry> RAF_FACTORY = new RAFFactory<Entry>() {
    public Entry create(RandomAccessFile raf) throws IOException {
      final String lang1 = raf.readUTF();
      final String lang2 = raf.readUTF();
      return new Entry(lang1, lang2);
    }};
  public void write(RandomAccessFile raf) throws IOException {
    raf.writeUTF(lang1);
    raf.writeUTF(lang2);
  }

  String getAllText(final byte lang) {
    if (lang == LANG1) {
      return lang1;
    }
    assert lang == LANG2;
    return lang2;
  }
  
  String getIndexableText(final byte lang) {
    String text = getAllText(lang);
    text = text.replaceAll("[\"\\.!?,]", "");
    text = text.replaceAll("\\{[^}]+\\}", "");
    return text;
  }

  public String getFormattedEntry(final byte lang) {
    return getAllText(lang) + "\n" + getAllText(OtherLang(lang));
  }

  private byte OtherLang(final byte lang) {
    assert lang == LANG1 || lang == LANG2;
    return lang == LANG1 ? LANG2 : LANG1;
  }

  public String getRawText() {
    return getAllText(LANG1) + " :: " + getAllText(LANG2);
  }
  
  

  static Entry parseFromLine(final String line) {
    final String[] parts = lineSplitPattern.split(line);
    if (parts.length != 2) {
      System.err.println("Entry:" + "Invalid line: " + line);
      return null;
    }
    return new Entry(parts[0], parts[1]);
  }

}
