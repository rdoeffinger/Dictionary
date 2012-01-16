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
import java.util.regex.Pattern;

import com.hughes.android.dictionary.R;
import com.ibm.icu.text.Collator;

public class Language {

  public static final Map<String,Integer> isoCodeToResourceId = new LinkedHashMap<String,Integer>();
  static {
    isoCodeToResourceId.put("AF", R.string.AF);
    isoCodeToResourceId.put("SQ", R.string.SQ);
    isoCodeToResourceId.put("AR", R.string.AR);
    isoCodeToResourceId.put("HY", R.string.HY);
    isoCodeToResourceId.put("BE", R.string.BE);
    isoCodeToResourceId.put("BN", R.string.BN);
    isoCodeToResourceId.put("BS", R.string.BS);
    isoCodeToResourceId.put("BG", R.string.BG);
    isoCodeToResourceId.put("CA", R.string.CA);
    isoCodeToResourceId.put("HR", R.string.HR);
    isoCodeToResourceId.put("CS", R.string.CS);
    isoCodeToResourceId.put("ZH", R.string.ZH);
    isoCodeToResourceId.put("DA", R.string.DA);
    isoCodeToResourceId.put("NL", R.string.NL);
    isoCodeToResourceId.put("EN", R.string.EN);
    isoCodeToResourceId.put("EO", R.string.EO);
    isoCodeToResourceId.put("ET", R.string.ET);
    isoCodeToResourceId.put("FI", R.string.FI);
    isoCodeToResourceId.put("FR", R.string.FR);
    isoCodeToResourceId.put("DE", R.string.DE);
    isoCodeToResourceId.put("EL", R.string.EL);
    isoCodeToResourceId.put("haw", R.string.haw);
    isoCodeToResourceId.put("HE", R.string.HE);
    isoCodeToResourceId.put("HI", R.string.HI);
    isoCodeToResourceId.put("HU", R.string.HU);
    isoCodeToResourceId.put("IS", R.string.IS);
    isoCodeToResourceId.put("ID", R.string.ID);
    isoCodeToResourceId.put("GA", R.string.GA);
    isoCodeToResourceId.put("IT", R.string.IT);
    isoCodeToResourceId.put("LA", R.string.LA);
    isoCodeToResourceId.put("LV", R.string.LV);
    isoCodeToResourceId.put("LT", R.string.LT);
    isoCodeToResourceId.put("JA", R.string.JA);
    isoCodeToResourceId.put("KO", R.string.KO);
    isoCodeToResourceId.put("KU", R.string.KU);
    isoCodeToResourceId.put("MS", R.string.MS);
    isoCodeToResourceId.put("MI", R.string.MI);
    isoCodeToResourceId.put("MN", R.string.MN);
    isoCodeToResourceId.put("NE", R.string.NE);
    isoCodeToResourceId.put("NO", R.string.NO);
    isoCodeToResourceId.put("FA", R.string.FA);
    isoCodeToResourceId.put("PL", R.string.PL);
    isoCodeToResourceId.put("PT", R.string.PT);
    isoCodeToResourceId.put("PA", R.string.PA);
    isoCodeToResourceId.put("RO", R.string.RO);
    isoCodeToResourceId.put("RU", R.string.RU);
    isoCodeToResourceId.put("SA", R.string.SA);
    isoCodeToResourceId.put("SR", R.string.SR);
    isoCodeToResourceId.put("SK", R.string.SK);
    isoCodeToResourceId.put("SO", R.string.SO);
    isoCodeToResourceId.put("ES", R.string.ES);
    isoCodeToResourceId.put("SW", R.string.SW);
    isoCodeToResourceId.put("SV", R.string.SV);
    isoCodeToResourceId.put("TG", R.string.TG);
    isoCodeToResourceId.put("TH", R.string.TH);
    isoCodeToResourceId.put("BO", R.string.BO);
    isoCodeToResourceId.put("TR", R.string.TR);
    isoCodeToResourceId.put("UK", R.string.UK);
    isoCodeToResourceId.put("VI", R.string.VI);
    isoCodeToResourceId.put("CI", R.string.CI);
    isoCodeToResourceId.put("YI", R.string.YI);
    isoCodeToResourceId.put("ZU", R.string.ZU);
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
    if (collator == null) {
      this.collator = Collator.getInstance(locale);
      this.collator.setStrength(Collator.IDENTICAL);
    }
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
  private static final Pattern RTL_TOKEN = Pattern.compile("[" + rtlChars + "]+");
  
  public static String fixBidiText(final String text) {
    return RTL_TOKEN.matcher(text).replaceAll("\u200e $0 \u200e");
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
