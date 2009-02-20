package com.hughes.android.dictionary;

import java.util.Comparator;

import com.hughes.util.StringUtil;

public class EntryFactory {
  
  static final EntryFactory entryFactory = new EntryFactory();

  public String normalizeToken(final String token) {
    return token.toLowerCase().replaceAll("ß", "ss").replaceAll("ä", "a")
        .replaceAll("ae", "a").replaceAll("ö", "o").replaceAll("oe", "o")
        .replaceAll("ü", "u").replaceAll("ue", "u").replaceAll("[^A-Za-z0-9]",
            "");
  }
  
  public Comparator<String> getEntryComparator() {
    return new Comparator<String>() {
      public int compare(final String s1, final String s2) {
        final String norm1 = normalizeToken(s1);
        final String norm2 = normalizeToken(s2);
        final int c = norm1.compareTo(norm2);
        if (c != 0) {
          return c;
        }
        return StringUtil.reverse(s1).compareTo(StringUtil.reverse(s2));
      }};
  }

}
