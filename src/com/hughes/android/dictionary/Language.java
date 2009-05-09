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
        return StringUtil.flipCase(StringUtil.reverse(s1)).compareTo(StringUtil.flipCase(StringUtil.reverse(s2)));
      }};
  }
  
  @Override
  public String toString() {
    return symbol;
  }

  abstract String normalizeTokenForSort(final String token);


  // ----------------------------------------------------------------
  
  static final String normalizeTokenForSort(final String token, final boolean vowelETranslation) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < token.length(); ++i) {
      Character c = token.charAt(i);
      c = Character.toLowerCase(c);
      // only check for lowercase 'e' in subsequent position means don't treat acronyms as umlauted: SAE.
      if (vowelETranslation && (c == 'a' || c == 'o' || c == 'u') && i + 1 < token.length() && token.charAt(i + 1) == 'e') {
        if (c == 'a') {
          result.append('ä');
        } else if (c == 'o') {
          result.append('ö');
        } else if (c == 'u') {
          result.append('ü');
        }
        ++i;
      } else if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
        result.append(c);
      } else if (c == 'ß') {
        result.append("ss");
      } else if (c == 'ä') {
        result.append(c);
      } else if (c == 'ö') {
        result.append(c);
      } else if (c == 'ü') {
        result.append(c);
      }
    }
    return result.toString();
  }

  public static final Language EN = new Language("EN") {
    @Override
    public String normalizeTokenForSort(final String token) {
      return Language.normalizeTokenForSort(token, false);
    }
  };
    
  public static final Language DE = new Language("DE") {
    @Override
    String normalizeTokenForSort(final String token) {
      return Language.normalizeTokenForSort(token, true);
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
