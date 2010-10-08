package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hughes.util.raf.RAFFactory;

public final class SimpleEntry implements Entry {

  static final byte LANG1 = 0;
  static final byte LANG2 = 1;

  static final Pattern lineSplitPattern = Pattern.compile("\\s::\\s");
  static final Pattern sublineSplitPattern = Pattern.compile("\\s\\|\\s");

  final String[] lang1;
  final String[] lang2;
  
  SimpleEntry(final String[] lang1, final String[] lang2) {
    this.lang1 = lang1;
    this.lang2 = lang2;
  }

  public static final RAFFactory<SimpleEntry> RAF_FACTORY = new RAFFactory<SimpleEntry>() {
    public SimpleEntry create(RandomAccessFile raf) throws IOException {
      final int rows = raf.readByte();
      final String[] lang1 = new String[rows];
      final String[] lang2 = new String[rows];
      for (int i = 0; i < lang1.length; ++i) {
        lang1[i] = raf.readUTF();
        lang2[i] = raf.readUTF();
      }
      return new SimpleEntry(lang1, lang2);
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
    if (!(o instanceof SimpleEntry)) {
      return false;
    }
    final SimpleEntry that = (SimpleEntry) o;
    return Arrays.deepEquals(this.lang1, that.lang1) && Arrays.deepEquals(this.lang2, that.lang2); 
  }

  @Override
  public int hashCode() {
    return Arrays.deepHashCode(lang1) + Arrays.deepHashCode(lang2);
  }

  @Override
  public String toString() {
    return getRawText(false);
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
  
  String getRawText(boolean onlyFirstSubentry) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < (onlyFirstSubentry ? 1 : lang1.length); ++i) {
      result.append(i == 0 ? "" : " | ").append(lang1[i]);
    }
    result.append("\t");
    for (int i = 0; i < (onlyFirstSubentry ? 1 : lang2.length); ++i) {
      result.append(i == 0 ? "" : " | ").append(lang2[i]);
    }
    return result.toString();
  }
  
  static byte otherLang(final byte lang) {
    assert lang == LANG1 || lang == LANG2;
    return lang == LANG1 ? LANG2 : LANG1;
  }
  
/*
Lu     Letter, Uppercase
Ll  Letter, Lowercase
Lt  Letter, Titlecase
Lm  Letter, Modifier
Lo  Letter, Other
Mn  Mark, Nonspacing
Mc  Mark, Spacing Combining
Me  Mark, Enclosing
Nd  Number, Decimal Digit
Nl  Number, Letter
No  Number, Other
Pc  Punctuation, Connector
Pd  Punctuation, Dash
Ps  Punctuation, Open
Pe  Punctuation, Close
Pi  Punctuation, Initial quote (may behave like Ps or Pe depending on usage)
Pf  Punctuation, Final quote (may behave like Ps or Pe depending on usage)
Po  Punctuation, Other
Sm  Symbol, Math
Sc  Symbol, Currency
Sk  Symbol, Modifier
So  Symbol, Other
Zs  Separator, Space
Zl  Separator, Line
Zp  Separator, Paragraph
*/

  static Pattern htmlDecimalCode = Pattern.compile("&#([0-9]+);");
  static Pattern htmlCode = Pattern.compile("&#[^;]+;");
  
  static SimpleEntry parseFromLine(String line, final boolean hasMultipleSubentries) {
    
    line = line.replaceAll("&lt;", "<");
    line = line.replaceAll("&gt;", ">");
    Matcher matcher;
    while ((matcher = htmlDecimalCode.matcher(line)).find()) {
      final int intVal = Integer.parseInt(matcher.group(1));
      final String charCode = "" + ((char) intVal);
      System.out.println("Replacing " + matcher.group() + " with " + charCode);
      line = matcher.replaceAll(charCode);
    }
    if ((matcher = htmlCode.matcher(line)).find()) {
      System.err.println("HTML code: " + matcher.group());
    }
    
    final String[] parts = lineSplitPattern.split(line);
    if (parts.length != 2) {
      System.err.println("Entry:" + "Invalid line: " + line);
      return null;
    }
    if (!hasMultipleSubentries) {
      return new SimpleEntry(new String[] {parts[0].trim()}, new String[] {parts[1].trim()});
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
    return new SimpleEntry(lang1, lang2);
  }
  
  static final Map<String, String> bracketToClose = new LinkedHashMap<String, String>();
  static {
    bracketToClose.put("\"", "\"");
    bracketToClose.put(" '", "' ");
  }
  
  // This used to be called WHITESPACE.
  static final Pattern NON_TOKEN_CHAR = Pattern.compile("\\s+");
  
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
    text = text.replaceAll("[-:] ", " ");
    text = text.replaceAll(" [-:]", " ");
    
    // Now be really conservative about what we allow inside a token:
    // See: http://unicode.org/Public/UNIDATA/UCD.html#General_Category_Values
    text = text.replaceAll("[^-:\\p{L}\\p{N}\\p{S}]", " ");
    result.addAll(Arrays.asList(NON_TOKEN_CHAR.split(text)));

    text = text.replaceAll("[-]", " ");
    result.addAll(Arrays.asList(NON_TOKEN_CHAR.split(text)));
    
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
  
}