package com.hughes.android.dictionary;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hughes.util.StringUtil;

public abstract class Language {

  final String symbol;
  final Comparator<String> tokenComparator;

  public Language(final String symbol) {
    this.symbol = symbol;
    this.tokenComparator = new Comparator<String>() {
      public int compare(final String s1, final String s2) {
        final String norm1 = normalizeTokenForSort(s1);
        final String norm2 = normalizeTokenForSort(s2);
        final int c = norm1.compareTo(norm2);
        if (c != 0) {
          return c;
        }
        return StringUtil.reverse(s1).compareTo(StringUtil.reverse(s2));
      }};
  }
  
  @Override
  public String toString() {
    return symbol;
  }

  abstract String normalizeTokenForSort(final String token);


  // ----------------------------------------------------------------

  public static final Language EN = new Language("EN") {
    @Override
    public String normalizeTokenForSort(final String token) {
      return token.toLowerCase().replaceAll("ß", "ss").replaceAll("ä", "a")
          .replaceAll("ö", "o").replaceAll("ü", "u").replaceAll("[^A-Za-z0-9]",
              "");
    }
  };

  public static final Language DE = new Language("DE") {
    @Override
    String normalizeTokenForSort(final String token) {
      return token.toLowerCase().replaceAll("ß", "ss").replaceAll("ä", "a")
          .replaceAll("ae", "a").replaceAll("ö", "o").replaceAll("oe", "o")
          .replaceAll("ü", "u").replaceAll("ue", "u").replaceAll(
              "[^A-Za-z0-9]", "");
    }
  };

  // ----------------------------------------------------------------

  private static final Map<String, Language> symbolToLangauge = new LinkedHashMap<String, Language>();

  static {
    symbolToLangauge.put(EN.symbol, EN);
    symbolToLangauge.put(DE.symbol, DE);
  }
  
  static Language lookup(final String symbol) {
    return symbolToLangauge.get(symbol);
  }


}
