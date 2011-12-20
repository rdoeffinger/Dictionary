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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ibm.icu.text.Collator;

public class Language {

  public static final Map<String,String> isoCodeToWikiName = new LinkedHashMap<String,String>();
  static {
//    Albanian
//    Armenian
//    Belarusian
//    Bengali
//    Bosnian
//    Bulgarian
//    Catalan
//    Esperanto
//    Estonian
//    Hungarian
//    Indonesian
//    Kurdish
//    Latin
//    Lithuanian
//    Nepali
//    Punjabi
//    Swahili
    isoCodeToWikiName.put("AF", "Afrikaans");
    isoCodeToWikiName.put("AR", "Arabic");
    isoCodeToWikiName.put("HY", "Armenian");
    isoCodeToWikiName.put("HR", "Croatian");
    isoCodeToWikiName.put("CS", "Czech");
    isoCodeToWikiName.put("ZH", "Chinese|Mandarin|Cantonese");
    isoCodeToWikiName.put("DA", "Danish");
    isoCodeToWikiName.put("NL", "Dutch");
    isoCodeToWikiName.put("EN", "English");
    isoCodeToWikiName.put("FI", "Finnish");
    isoCodeToWikiName.put("FR", "French");
    isoCodeToWikiName.put("DE", "German");
    isoCodeToWikiName.put("EL", "Greek");
    isoCodeToWikiName.put("haw", "Hawaiian");
    isoCodeToWikiName.put("HE", "Hebrew");
    isoCodeToWikiName.put("HI", "Hindi");
    isoCodeToWikiName.put("IS", "Icelandic");
    isoCodeToWikiName.put("GA", "Irish");
    isoCodeToWikiName.put("IT", "Italian");
    isoCodeToWikiName.put("LT", "Lithuanian");
    isoCodeToWikiName.put("JA", "Japanese");
    isoCodeToWikiName.put("KO", "Korean");
    isoCodeToWikiName.put("KU", "Kurdish");
    isoCodeToWikiName.put("MS", "Malay");
    isoCodeToWikiName.put("MI", "Maori");
    isoCodeToWikiName.put("MN", "Mongolian");
    isoCodeToWikiName.put("NO", "Norwegian");
    isoCodeToWikiName.put("FA", "Persian");
    isoCodeToWikiName.put("PT", "Portuguese");
    isoCodeToWikiName.put("RO", "Romanian");
    isoCodeToWikiName.put("RU", "Russian");
    isoCodeToWikiName.put("SA", "Sanskrit");
    isoCodeToWikiName.put("SR", "Serbian");
    isoCodeToWikiName.put("SO", "Somali");
    isoCodeToWikiName.put("ES", "Spanish");
    isoCodeToWikiName.put("SV", "Swedish");
    isoCodeToWikiName.put("TG", "Tajik");
    isoCodeToWikiName.put("TH", "Thai");
    isoCodeToWikiName.put("BO", "Tibetan");
    isoCodeToWikiName.put("TR", "Turkish");
    isoCodeToWikiName.put("UK", "Ukrainian");
    isoCodeToWikiName.put("VI", "Vietnamese");
    isoCodeToWikiName.put("CI", "Welsh");
    isoCodeToWikiName.put("YI", "Yiddish");
    isoCodeToWikiName.put("ZU", "Zulu");
  }

  static final List<String> ISO_CODES_WITH_DICTS = Arrays.asList();

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
