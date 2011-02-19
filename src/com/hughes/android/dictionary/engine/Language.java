package com.hughes.android.dictionary.engine;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.ibm.icu.text.Collator;

public class Language {

  static final Map<String, Language> symbolToLangauge = new LinkedHashMap<String, Language>();

  final String symbol;
  final Locale locale;
  
  private Collator collator;

  public Language(final Locale locale) {
    this.symbol = locale.getLanguage();
    this.locale = locale;

    symbolToLangauge.put(symbol.toLowerCase(), this);
  }

  @Override
  public String toString() {
    return locale.toString();
  }
  
  public String getSymbol() {
    return symbol;
  }
  
  public synchronized Collator getCollator() {
    if (collator == null) {
      this.collator = Collator.getInstance(locale);
      this.collator.setStrength(Collator.IDENTICAL);
    }
    return collator;
  }
  
  public String getDefaultNormalizerRules() {
    return ":: Any-Latin; :: Lower; :: NFD; :: [:Nonspacing Mark:] Remove; :: NFC ;";
  }
  // ----------------------------------------------------------------

  public static final Language en = new Language(Locale.ENGLISH);
  public static final Language fr = new Language(Locale.FRENCH);
  public static final Language it = new Language(Locale.ITALIAN);

  public static final Language de = new Language(Locale.GERMAN) {
    @Override
    public String getDefaultNormalizerRules() {
      return ":: Lower; 'ae' > 'ä'; 'oe' > 'ö'; 'ue' > 'ü'; 'ß' > 'ss'; ";
    }
  };
  
  // ----------------------------------------------------------------

  public static synchronized Language lookup(final String symbol) {
    Language lang = symbolToLangauge.get(symbol.toLowerCase());
    if (lang == null) {
      lang = new Language(new Locale(symbol));
    }
    return lang;
  }

}
