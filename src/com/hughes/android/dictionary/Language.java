package com.hughes.android.dictionary;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.ibm.icu.text.Collator;

public class Language {

  final String symbol;
  final Locale locale;

  Collator sortCollator;
  final Comparator<String> sortComparator;

  private Collator findCollator;
  final Comparator<String> findComparator;

  public Language(final String symbol, final Locale locale) {
    this.symbol = symbol;
    this.locale = locale;

    this.sortComparator = new Comparator<String>() {
      public int compare(final String s1, final String s2) {
        return getSortCollator().compare(textNorm(s1), textNorm(s2));
      }
    };

    this.findComparator = new Comparator<String>() {
      public int compare(final String s1, final String s2) {
        return getFindCollator().compare(textNorm(s1), textNorm(s2));
      }
    };

  }

  public String textNorm(final String s) {
    return s;
  }

  @Override
  public String toString() {
    return symbol;
  }
  
  synchronized Collator getFindCollator() {
    if (findCollator == null) {
      findCollator = Collator.getInstance(locale);
      findCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
      findCollator.setStrength(Collator.SECONDARY);
    }
    return findCollator;
  }

  synchronized Collator getSortCollator() {
    if (sortCollator == null) {
      sortCollator = Collator.getInstance(locale);
      sortCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
      sortCollator.setStrength(Collator.IDENTICAL);
    }
    return sortCollator;
  }

  // ----------------------------------------------------------------

  public static final Language EN = new Language("EN", Locale.ENGLISH);

  public static final Language DE = new Language("DE", Locale.GERMAN) {
    @Override
    public String textNorm(String token) {
      boolean sub = false;
      for (int ePos = token.indexOf('e', 1); ePos != -1; ePos = token.indexOf(
          'e', ePos + 1)) {
        final char pre = Character.toLowerCase(token.charAt(ePos - 1));
        if (pre == 'a' || pre == 'o' || pre == 'u') {
          sub = true;
          break;
        }
      }
      if (!sub) {
        return token;
      }
      token = token.replaceAll("ae", "ä");
      token = token.replaceAll("oe", "ö");
      token = token.replaceAll("ue", "ü");

      token = token.replaceAll("Ae", "Ä");
      token = token.replaceAll("Oe", "Ö");
      token = token.replaceAll("Ue", "Ü");
      return token;
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
