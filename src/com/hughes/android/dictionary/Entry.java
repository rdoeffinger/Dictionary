package com.hughes.android.dictionary;

import java.util.regex.Pattern;

public final class Entry {

  static final byte LANG1 = 0;
  static final byte LANG2 = 1;

  static final Pattern lineSplitPattern = Pattern.compile("\\s+::\\s+");

  String lang1 = "";
  String lang2 = "";

  Entry() {}

  Entry(final String line) {
    final boolean parsed = parseFromLine(line);
    assert parsed;
  }

  boolean parseFromLine(final String line) {
    final String[] parts = lineSplitPattern.split(line);
    if (parts.length != 2) {
      System.err.println("Entry:" + "Invalid line: " + line);
      return false;
    }
    lang1 = parts[0];
    lang2 = parts[1];
    return true;
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

  public Object getFormattedEntry(final byte lang) {
    return getAllText(lang) + "\n" + getAllText(OtherLang(lang));
  }

  private byte OtherLang(final byte lang) {
    assert lang == LANG1 || lang == LANG2;
    return lang == LANG1 ? LANG2 : LANG1;
  }

  public String getRawText() {
    return getAllText(LANG1) + " :: " + getAllText(LANG2);
  }

}
