package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hughes.util.raf.RAFFactory;
import com.hughes.util.raf.RAFSerializable;

public final class Entry implements RAFSerializable<Entry> {

  static final byte LANG1 = 0;
  static final byte LANG2 = 1;

  static final Pattern lineSplitPattern = Pattern.compile("\\s::\\s");
  static final Pattern sublineSplitPattern = Pattern.compile("\\s\\|\\s");

  final String[] lang1;
  final String[] lang2;
  
//  public Entry(final String lang1, final String lang2) {
//    this.lang1 = new String[] {lang1};
//    this.lang2 = new String[] {lang2};
//  }

  Entry(final String[] lang1, final String[] lang2) {
    this.lang1 = lang1;
    this.lang2 = lang2;
  }

  public static final RAFFactory<Entry> RAF_FACTORY = new RAFFactory<Entry>() {
    public Entry create(RandomAccessFile raf) throws IOException {
      final int rows = raf.readByte();
      final String[] lang1 = new String[rows];
      final String[] lang2 = new String[rows];
      for (int i = 0; i < lang1.length; ++i) {
        lang1[i] = raf.readUTF();
        lang2[i] = raf.readUTF();
      }
      return new Entry(lang1, lang2);
    }};
  public void write(RandomAccessFile raf) throws IOException {
    assert lang1.length == (byte) lang1.length;
    raf.writeByte(lang1.length);
    for (int i = 0; i < lang1.length; ++i) {
      raf.writeUTF(lang1[i]);
      raf.writeUTF(lang2[i]);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Entry)) {
      return false;
    }
    final Entry that = (Entry) o;
    return Arrays.deepEquals(this.lang1, that.lang1) && Arrays.deepEquals(this.lang2, that.lang2); 
  }

  @Override
  public int hashCode() {
    return Arrays.deepHashCode(lang1) + Arrays.deepHashCode(lang2);
  }

  @Override
  public String toString() {
    return getRawText();
  }

  public int getRowCount() {
    assert lang1.length == lang2.length;
    return lang1.length;
  }

  String[] getAllText(final byte lang) {
    if (lang == LANG1) {
      return lang1;
    }
    assert lang == LANG2;
    return lang2;
  }
  
  String getRawText() {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < lang1.length; ++i) {
      result.append(i == 0 ? "" : " | ").append(lang1[i]);
    }
    result.append(" :: ");
    for (int i = 0; i < lang2.length; ++i) {
      result.append(i == 0 ? "" : " | ").append(lang2[i]);
    }
    return result.toString();
  }
  
  static byte otherLang(final byte lang) {
    assert lang == LANG1 || lang == LANG2;
    return lang == LANG1 ? LANG2 : LANG1;
  }
  

  static Entry parseFromLine(String line, final boolean hasMultipleSubentries) {
    line = line.replaceAll("&lt;", "<");
    line = line.replaceAll("&gt;", ">");
    final String[] parts = lineSplitPattern.split(line);
    if (parts.length != 2) {
      System.err.println("Entry:" + "Invalid line: " + line);
      return null;
    }
    if (!hasMultipleSubentries) {
      return new Entry(new String[] {parts[0].trim()}, new String[] {parts[1].trim()});
    }
    
    final String[] lang1 = sublineSplitPattern.split(" " + parts[0].trim() + " ");
    final String[] lang2 = sublineSplitPattern.split(" " + parts[1].trim() + " ");
    if (lang1.length != lang2.length) {
      System.err.println("Entry:" + "Invalid subline: " + line);
      return null;
    }
    for (int i = 0; i < lang1.length; ++i) {
      lang1[i] = lang1[i].trim();
      lang2[i] = lang2[i].trim();
    }
    return new Entry(lang1, lang2);
  }
  
  static final Map<String, String> bracketToClose = new LinkedHashMap<String, String>();
  static {
    bracketToClose.put("\"", "\"");
    bracketToClose.put(" '", "' ");
  }
  
  static final Pattern WHITESPACE = Pattern.compile("\\s+");
  
  public Set<String> getIndexableTokens(final byte lang) {
    final Set<String> result = new LinkedHashSet<String>();
    String text = " ";
    for (final String subentry : getAllText(lang)) {
      text += subentry + " ";
    }

    text = text.replaceAll("fig\\.", " ");
    text = text.replaceAll("\\{[^\\}]+}", " ");
    text = text.replaceAll("\"-", "-");
    text = text.replaceAll("-\"", "-");
    text = text.replaceAll("[\"/\\()<>\\[\\],;?!.]", " ");
    text = text.replaceAll("[:] ", " ");
    text = text.replaceAll(" [:]", " ");
    
    // Now be really conservative about what we allow inside a token:
    // See: http://unicode.org/Public/UNIDATA/UCD.html#General_Category_Values
    text = text.replaceAll("[^-:\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nd}\\p{Nl}\\p{No}]", " ");
    
    result.addAll(Arrays.asList(WHITESPACE.split(text)));

    text = text.replaceAll("[-]", " ");
    result.addAll(Arrays.asList(WHITESPACE.split(text)));
    
    final Set<String> result2 = new LinkedHashSet<String>();
    for (final String token : result) {
      if (isIndexable(token)) {
        result2.add(token);
      }
    }
    return result2;
  }

  static boolean isIndexable(final String text) {
    // Does it have an alpha-numeric anywhere?
    return text.matches(".*\\w.*");
  }
  
  static List<String> getTextInside(final String text, final String start, final String end) {
    final List<String> result = new ArrayList<String>();
    int startPos = 0;
    while ((startPos = text.indexOf(start)) != -1) {
      final int endPos = text.indexOf(end, startPos + 1);
      result.add(text.substring(startPos + 1, endPos));
      startPos = endPos + 1;
    }
    return result;
  }

}