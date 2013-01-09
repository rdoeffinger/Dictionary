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

import com.hughes.android.dictionary.R;
import com.ibm.icu.text.Collator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class Language {
  
  public static final class LanguageResources {
    public final String englishName;
    public final int nameId;
    public final int flagId;
    
    private LanguageResources(final String englishName, int nameId, int flagId) {
      this.englishName = englishName;
      this.nameId = nameId;
      this.flagId = flagId;
    }

    private LanguageResources(final String englishName, int nameId) {
      this(englishName, nameId, 0);
    }
}

  public static final Map<String,LanguageResources> isoCodeToResources = new LinkedHashMap<String,LanguageResources>();
  static {
    isoCodeToResources.put("AF", new LanguageResources("Afrikaans", R.string.AF));
    isoCodeToResources.put("SQ", new LanguageResources("Albanian", R.string.SQ));
    isoCodeToResources.put("AR", new LanguageResources("Arabic", R.string.AR));
    isoCodeToResources.put("HY", new LanguageResources("Armenian", R.string.HY));
    isoCodeToResources.put("BE", new LanguageResources("Belarusian", R.string.BE));
    isoCodeToResources.put("BN", new LanguageResources("Bengali", R.string.BN));
    isoCodeToResources.put("BS", new LanguageResources("Bosnian", R.string.BS));
    isoCodeToResources.put("BG", new LanguageResources("Bulgarian", R.string.BG));
    isoCodeToResources.put("MY", new LanguageResources("Burmese", R.string.MY));
    isoCodeToResources.put("ZH", new LanguageResources("Chinese", R.string.ZH));
    isoCodeToResources.put("cmn", new LanguageResources("Mandarin", R.string.cmn));
    isoCodeToResources.put("yue", new LanguageResources("Cantonese", R.string.yue));
    isoCodeToResources.put("CA", new LanguageResources("Catalan", R.string.CA));
    isoCodeToResources.put("HR", new LanguageResources("Croatian", R.string.HR));
    isoCodeToResources.put("CS", new LanguageResources("Czech", R.string.CS));
    isoCodeToResources.put("DA", new LanguageResources("Danish", R.string.DA));
    isoCodeToResources.put("NL", new LanguageResources("Dutch", R.string.NL));
    isoCodeToResources.put("EN", new LanguageResources("English", R.string.EN));
    isoCodeToResources.put("EO", new LanguageResources("Esperanto", R.string.EO));
    isoCodeToResources.put("ET", new LanguageResources("Estonian", R.string.ET));
    isoCodeToResources.put("FI", new LanguageResources("Finnish", R.string.FI));
    isoCodeToResources.put("FR", new LanguageResources("French", R.string.FR));
    isoCodeToResources.put("DE", new LanguageResources("German", R.string.DE));
    isoCodeToResources.put("EL", new LanguageResources("Greek", R.string.EL));
    isoCodeToResources.put("grc", new LanguageResources("Ancient Greek", R.string.grc));
    isoCodeToResources.put("haw", new LanguageResources("Hawaiian", R.string.haw));
    isoCodeToResources.put("HE", new LanguageResources("Hebrew", R.string.HE));
    isoCodeToResources.put("HI", new LanguageResources("Hindi", R.string.HI));
    isoCodeToResources.put("HU", new LanguageResources("Hungarian", R.string.HU));
    isoCodeToResources.put("IS", new LanguageResources("Icelandic", R.string.IS));
    isoCodeToResources.put("ID", new LanguageResources("Indonesian", R.string.ID));
    isoCodeToResources.put("GA", new LanguageResources("Irish", R.string.GA));
    isoCodeToResources.put("GD", new LanguageResources("Scottish Gaelic", R.string.GD));
    isoCodeToResources.put("IT", new LanguageResources("Italian", R.string.IT));
    isoCodeToResources.put("LA", new LanguageResources("Latin", R.string.LA));
    isoCodeToResources.put("LV", new LanguageResources("Latvian", R.string.LV));
    isoCodeToResources.put("LT", new LanguageResources("Lithuanian", R.string.LT));
    isoCodeToResources.put("JA", new LanguageResources("Japanese", R.string.JA));
    isoCodeToResources.put("KO", new LanguageResources("Korean", R.string.KO));
    isoCodeToResources.put("KU", new LanguageResources("Kurdish", R.string.KU));
    isoCodeToResources.put("MS", new LanguageResources("Malay", R.string.MS));
    isoCodeToResources.put("MI", new LanguageResources("Maori", R.string.MI));
    isoCodeToResources.put("MN", new LanguageResources("Mongolian", R.string.MN));
    isoCodeToResources.put("NE", new LanguageResources("Nepali", R.string.NE));
    isoCodeToResources.put("NO", new LanguageResources("Norwegian", R.string.NO));
    isoCodeToResources.put("FA", new LanguageResources("Persian", R.string.FA));
    isoCodeToResources.put("PL", new LanguageResources("Polish", R.string.PL));
    isoCodeToResources.put("PT", new LanguageResources("Portuguese", R.string.PT));
    isoCodeToResources.put("PA", new LanguageResources("Punjabi", R.string.PA));
    isoCodeToResources.put("RO", new LanguageResources("Romanian", R.string.RO));
    isoCodeToResources.put("RU", new LanguageResources("Russian", R.string.RU));
    isoCodeToResources.put("SA", new LanguageResources("Sanskrit", R.string.SA));
    isoCodeToResources.put("SR", new LanguageResources("Serbian", R.string.SR));
    isoCodeToResources.put("SK", new LanguageResources("Slovak", R.string.SK));
    isoCodeToResources.put("SL", new LanguageResources("Slovenian", R.string.SL));
    isoCodeToResources.put("SO", new LanguageResources("Somali", R.string.SO));
    isoCodeToResources.put("ES", new LanguageResources("Spanish", R.string.ES));
    isoCodeToResources.put("SW", new LanguageResources("Swahili", R.string.SW));
    isoCodeToResources.put("SV", new LanguageResources("Swedish", R.string.SV));
    isoCodeToResources.put("TL", new LanguageResources("Tagalog", R.string.TL));
    isoCodeToResources.put("TG", new LanguageResources("Tajik", R.string.TG));
    isoCodeToResources.put("TH", new LanguageResources("Thai", R.string.TH));
    isoCodeToResources.put("BO", new LanguageResources("Tibetan", R.string.BO));
    isoCodeToResources.put("TR", new LanguageResources("Turkish", R.string.TR));
    isoCodeToResources.put("UK", new LanguageResources("Ukrainian", R.string.UK));
    isoCodeToResources.put("UR", new LanguageResources("Urdu", R.string.UR));
    isoCodeToResources.put("VI", new LanguageResources("Vietnamese", R.string.VI));
    isoCodeToResources.put("CI", new LanguageResources("Welsh", R.string.CI));
    isoCodeToResources.put("YI", new LanguageResources("Yiddish", R.string.YI));
    isoCodeToResources.put("ZU", new LanguageResources("Zulu", R.string.ZU));
    isoCodeToResources.put("AZ", new LanguageResources("Azeri", R.string.AZ));
    isoCodeToResources.put("EU", new LanguageResources("Basque", R.string.EU));
    isoCodeToResources.put("BR", new LanguageResources("Breton", R.string.BR));
    isoCodeToResources.put("MR", new LanguageResources("Burmese", R.string.MR));
    isoCodeToResources.put("FO", new LanguageResources("Faroese", R.string.FO));
    isoCodeToResources.put("GL", new LanguageResources("Galician", R.string.GL));
    isoCodeToResources.put("KA", new LanguageResources("Georgian", R.string.KA));
    isoCodeToResources.put("HT", new LanguageResources("Haitian Creole", R.string.HT));
    isoCodeToResources.put("LB", new LanguageResources("Luxembourgish", R.string.LB));
    isoCodeToResources.put("MK", new LanguageResources("Macedonian", R.string.MK));
    isoCodeToResources.put("LO", new LanguageResources("Lao", R.string.LO));
    isoCodeToResources.put("ML", new LanguageResources("Malayalam", R.string.ML));
    isoCodeToResources.put("SL", new LanguageResources("Slovenian", R.string.SL));
    isoCodeToResources.put("TA", new LanguageResources("Tamil", R.string.TA));
    isoCodeToResources.put("SH", new LanguageResources("Serbo-Croations", R.string.SH));

    // Hack to allow lower-case ISO codes to work:
    for (final String isoCode : new ArrayList<String>(isoCodeToResources.keySet())) {
      isoCodeToResources.put(isoCode.toLowerCase(), isoCodeToResources.get(isoCode));
    }

  }


  private static final Map<String, Language> registry = new LinkedHashMap<String, Language>();

  final String isoCode;
  final Locale locale;
  
  private Collator collator;

  private Language(final Locale locale, final String isoCode) {
    this.locale = locale;
    this.isoCode = isoCode;

    registry.put(isoCode.toLowerCase(), this);
  }

  @Override
  public String toString() {
    return locale.toString();
  }
  
  public String getIsoCode() {
    return isoCode;
  }
  
  public synchronized Collator getCollator() {
    // Don't think this is thread-safe...
//    if (collator == null) {
      this.collator = Collator.getInstance(locale);
      this.collator.setStrength(Collator.IDENTICAL);
//    }
    return collator;
  }
  
  public String getDefaultNormalizerRules() {
    return ":: Any-Latin; ' ' > ; :: Lower; :: NFD; :: [:Nonspacing Mark:] Remove; :: NFC ;";
  }
  
  /**
   * A practical pattern to identify strong RTL characters. This pattern is not
   * completely correct according to the Unicode standard. It is simplified for
   * performance and small code size.
   */
  private static final String rtlChars =
      "\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC";

  private static final String puncChars =
      "\\[\\]\\(\\)\\{\\}\\=";

  private static final Pattern RTL_LEFT_BOUNDARY = Pattern.compile("(["+ puncChars +"])([" + rtlChars + "])");
  private static final Pattern RTL_RIGHT_BOUNDARY = Pattern.compile("([" + rtlChars + "])(["+ puncChars +"])");
  
  public static String fixBidiText(String text) {
//    text = RTL_LEFT_BOUNDARY.matcher(text).replaceAll("$1\u200e $2");
//    text = RTL_RIGHT_BOUNDARY.matcher(text).replaceAll("$1 \u200e$2");
    return text;
  }
  
  // ----------------------------------------------------------------

  public static final Language en = new Language(Locale.ENGLISH, "EN");
  public static final Language fr = new Language(Locale.FRENCH, "FR");
  public static final Language it = new Language(Locale.ITALIAN, "IT");

  public static final Language de = new Language(Locale.GERMAN, "DE") {
    @Override
    public String getDefaultNormalizerRules() {
      return ":: Lower; 'ae' > 'ä'; 'oe' > 'ö'; 'ue' > 'ü'; 'ß' > 'ss'; ";
    }
  };
  
  // ----------------------------------------------------------------

  public static synchronized Language lookup(final String isoCode) {
    Language lang = registry.get(isoCode.toLowerCase());
    if (lang == null) {
      lang = new Language(new Locale(isoCode), isoCode);
    }
    return lang;
  }

}
