package com.hughes.android.dictionary;

import java.util.regex.Pattern;

public final class Entry {

  static final byte LANG1 = 0;
  static final byte LANG2 = 1;

  static final Pattern lineSplitPattern = Pattern.compile("\\s+::\\s+");

  String lang1 = "";
  String lang2 = "";

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

  public String normalizeToken(final String token, final byte lang) {
    return token.toLowerCase().replaceAll("ß", "ss").replaceAll("ä", "ae")
        .replaceAll("ö", "oe").replaceAll("ü", "ue")
        .replaceAll("[^A-Za-z]", "");
  }

}
