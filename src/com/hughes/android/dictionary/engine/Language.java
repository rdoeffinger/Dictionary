// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
