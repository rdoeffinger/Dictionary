// Copyright 2011 Google Inc. All Rights Reserved.
// Copyright 2017 Reimar Döffinger. All Rights Reserved.
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

package com.hughes.android.dictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Language.LanguageResources;

public enum IsoUtils {
    INSTANCE;

    // Useful:
    // http://www.loc.gov/standards/iso639-2/php/code_list.php
    private final Map<String, LanguageResources> isoCodeToResources = new HashMap<String, LanguageResources>();
    IsoUtils() {
        isoCodeToResources.put("AF", new LanguageResources("Afrikaans", R.string.AF,
                               R.drawable.flag_of_south_africa));
        isoCodeToResources.put("SQ", new LanguageResources("Albanian", R.string.SQ,
                               R.drawable.flag_of_albania));
        isoCodeToResources.put("AR",
                               new LanguageResources("Arabic", R.string.AR, R.drawable.arabic));
        isoCodeToResources.put("HY", new LanguageResources("Armenian", R.string.HY,
                               R.drawable.flag_of_armenia));
        isoCodeToResources.put("BE", new LanguageResources("Belarusian", R.string.BE,
                               R.drawable.flag_of_belarus));
        isoCodeToResources.put("BN", new LanguageResources("Bengali", R.string.BN));
        isoCodeToResources.put("BS", new LanguageResources("Bosnian", R.string.BS,
                               R.drawable.flag_of_bosnia_and_herzegovina));
        isoCodeToResources.put("BG", new LanguageResources("Bulgarian", R.string.BG,
                               R.drawable.flag_of_bulgaria));
        isoCodeToResources.put("MY", new LanguageResources("Burmese", R.string.MY,
                               R.drawable.flag_of_myanmar));
        isoCodeToResources.put("ZH", new LanguageResources("Chinese", R.string.ZH,
                               R.drawable.flag_of_the_peoples_republic_of_china));
        isoCodeToResources.put("cmn", new LanguageResources("Mandarin", R.string.cmn,
                               R.drawable.flag_of_the_peoples_republic_of_china));
        isoCodeToResources.put("yue", new LanguageResources("Cantonese", R.string.yue,
                               R.drawable.flag_of_hong_kong));
        isoCodeToResources.put("CA", new LanguageResources("Catalan", R.string.CA));
        isoCodeToResources.put("HR", new LanguageResources("Croatian", R.string.HR,
                               R.drawable.flag_of_croatia));
        isoCodeToResources.put("CS", new LanguageResources("Czech", R.string.CS,
                               R.drawable.flag_of_the_czech_republic));
        isoCodeToResources.put("DA", new LanguageResources("Danish", R.string.DA,
                               R.drawable.flag_of_denmark));
        isoCodeToResources.put("NL", new LanguageResources("Dutch", R.string.NL,
                               R.drawable.flag_of_the_netherlands));
        isoCodeToResources.put("EN", new LanguageResources("English", R.string.EN,
                               R.drawable.flag_of_the_united_kingdom));
        isoCodeToResources.put("EO", new LanguageResources("Esperanto", R.string.EO,
                               R.drawable.flag_of_esperanto));
        isoCodeToResources.put("ET", new LanguageResources("Estonian", R.string.ET,
                               R.drawable.flag_of_estonia));
        isoCodeToResources.put("FI", new LanguageResources("Finnish", R.string.FI,
                               R.drawable.flag_of_finland));
        isoCodeToResources.put("FR", new LanguageResources("French", R.string.FR,
                               R.drawable.flag_of_france));
        isoCodeToResources.put("DE", new LanguageResources("German", R.string.DE,
                               R.drawable.flag_of_germany));
        isoCodeToResources.put("EL", new LanguageResources("Greek", R.string.EL,
                               R.drawable.flag_of_greece));
        isoCodeToResources.put("grc", new LanguageResources("Ancient Greek", R.string.grc));
        isoCodeToResources.put("haw", new LanguageResources("Hawaiian", R.string.haw,
                               R.drawable.flag_of_hawaii));
        isoCodeToResources.put("HE", new LanguageResources("Hebrew", R.string.HE,
                               R.drawable.flag_of_israel));
        isoCodeToResources.put("HI", new LanguageResources("Hindi", R.string.HI, R.drawable.hindi));
        isoCodeToResources.put("HU", new LanguageResources("Hungarian", R.string.HU,
                               R.drawable.flag_of_hungary));
        isoCodeToResources.put("IS", new LanguageResources("Icelandic", R.string.IS,
                               R.drawable.flag_of_iceland));
        isoCodeToResources.put("ID", new LanguageResources("Indonesian", R.string.ID,
                               R.drawable.flag_of_indonesia));
        isoCodeToResources.put("GA", new LanguageResources("Irish", R.string.GA,
                               R.drawable.flag_of_ireland));
        isoCodeToResources.put("GD", new LanguageResources("Scottish Gaelic", R.string.GD,
                               R.drawable.flag_of_scotland));
        isoCodeToResources.put("GV", new LanguageResources("Manx", R.string.GV,
                               R.drawable.flag_of_the_isle_of_man));
        isoCodeToResources.put("IT", new LanguageResources("Italian", R.string.IT,
                               R.drawable.flag_of_italy));
        isoCodeToResources.put("LA", new LanguageResources("Latin", R.string.LA));
        isoCodeToResources.put("LV", new LanguageResources("Latvian", R.string.LV,
                               R.drawable.flag_of_latvia));
        isoCodeToResources.put("LT", new LanguageResources("Lithuanian", R.string.LT,
                               R.drawable.flag_of_lithuania));
        isoCodeToResources.put("JA", new LanguageResources("Japanese", R.string.JA,
                               R.drawable.flag_of_japan));
        isoCodeToResources.put("KO", new LanguageResources("Korean", R.string.KO,
                               R.drawable.flag_of_south_korea));
        isoCodeToResources.put("KU", new LanguageResources("Kurdish", R.string.KU));
        isoCodeToResources.put("MS", new LanguageResources("Malay", R.string.MS,
                               R.drawable.flag_of_malaysia));
        isoCodeToResources.put("MI", new LanguageResources("Maori", R.string.MI,
                               R.drawable.flag_of_new_zealand));
        isoCodeToResources.put("MN", new LanguageResources("Mongolian", R.string.MN,
                               R.drawable.flag_of_mongolia));
        isoCodeToResources.put("NE", new LanguageResources("Nepali", R.string.NE,
                               R.drawable.flag_of_nepal));
        isoCodeToResources.put("NO", new LanguageResources("Norwegian", R.string.NO,
                               R.drawable.flag_of_norway));
        isoCodeToResources.put("FA", new LanguageResources("Persian", R.string.FA,
                               R.drawable.flag_of_iran));
        isoCodeToResources.put("PL", new LanguageResources("Polish", R.string.PL,
                               R.drawable.flag_of_poland));
        isoCodeToResources.put("PT", new LanguageResources("Portuguese", R.string.PT,
                               R.drawable.flag_of_portugal));
        isoCodeToResources.put("PA", new LanguageResources("Punjabi", R.string.PA));
        isoCodeToResources.put("RO", new LanguageResources("Romanian", R.string.RO,
                               R.drawable.flag_of_romania));
        isoCodeToResources.put("RU", new LanguageResources("Russian", R.string.RU,
                               R.drawable.flag_of_russia));
        isoCodeToResources.put("SA", new LanguageResources("Sanskrit", R.string.SA));
        isoCodeToResources.put("SR", new LanguageResources("Serbian", R.string.SR,
                               R.drawable.flag_of_serbia));
        isoCodeToResources.put("SK", new LanguageResources("Slovak", R.string.SK,
                               R.drawable.flag_of_slovakia));
        isoCodeToResources.put("SL", new LanguageResources("Slovenian", R.string.SL,
                               R.drawable.flag_of_slovenia));
        isoCodeToResources.put("SO", new LanguageResources("Somali", R.string.SO,
                               R.drawable.flag_of_somalia));
        isoCodeToResources.put("ES", new LanguageResources("Spanish", R.string.ES,
                               R.drawable.flag_of_spain));
        isoCodeToResources.put("SW", new LanguageResources("Swahili", R.string.SW));
        isoCodeToResources.put("SV", new LanguageResources("Swedish", R.string.SV,
                               R.drawable.flag_of_sweden));
        isoCodeToResources.put("TL", new LanguageResources("Tagalog", R.string.TL));
        isoCodeToResources.put("TG", new LanguageResources("Tajik", R.string.TG,
                               R.drawable.flag_of_tajikistan));
        isoCodeToResources.put("TH", new LanguageResources("Thai", R.string.TH,
                               R.drawable.flag_of_thailand));
        isoCodeToResources.put("BO", new LanguageResources("Tibetan", R.string.BO));
        isoCodeToResources.put("TR", new LanguageResources("Turkish", R.string.TR,
                               R.drawable.flag_of_turkey));
        isoCodeToResources.put("UK", new LanguageResources("Ukrainian", R.string.UK,
                               R.drawable.flag_of_ukraine));
        isoCodeToResources.put("UR", new LanguageResources("Urdu", R.string.UR));
        isoCodeToResources.put("VI", new LanguageResources("Vietnamese", R.string.VI,
                               R.drawable.flag_of_vietnam));
        isoCodeToResources.put("CI", new LanguageResources("Welsh", R.string.CI,
                               R.drawable.flag_of_wales_2));
        isoCodeToResources.put("YI", new LanguageResources("Yiddish", R.string.YI));
        isoCodeToResources.put("ZU", new LanguageResources("Zulu", R.string.ZU));
        isoCodeToResources.put("AZ", new LanguageResources("Azeri", R.string.AZ,
                               R.drawable.flag_of_azerbaijan));
        isoCodeToResources.put("EU", new LanguageResources("Basque", R.string.EU,
                               R.drawable.flag_of_the_basque_country));
        isoCodeToResources.put("BR", new LanguageResources("Breton", R.string.BR));
        isoCodeToResources.put("MR", new LanguageResources("Marathi", R.string.MR));
        isoCodeToResources.put("FO", new LanguageResources("Faroese", R.string.FO));
        isoCodeToResources.put("GL", new LanguageResources("Galician", R.string.GL,
                               R.drawable.flag_of_galicia));
        isoCodeToResources.put("KA", new LanguageResources("Georgian", R.string.KA,
                               R.drawable.flag_of_georgia));
        isoCodeToResources.put("HT", new LanguageResources("Haitian Creole", R.string.HT,
                               R.drawable.flag_of_haiti));
        isoCodeToResources.put("LB", new LanguageResources("Luxembourgish", R.string.LB,
                               R.drawable.flag_of_luxembourg));
        isoCodeToResources.put("MK", new LanguageResources("Macedonian", R.string.MK,
                               R.drawable.flag_of_macedonia));
        isoCodeToResources.put("LO", new LanguageResources("Lao", R.string.LO,
                               R.drawable.flag_of_laos));
        isoCodeToResources.put("ML", new LanguageResources("Malayalam", R.string.ML));
        isoCodeToResources.put("SL", new LanguageResources("Slovenian", R.string.SL,
                               R.drawable.flag_of_slovenia));
        isoCodeToResources.put("TA", new LanguageResources("Tamil", R.string.TA));
        isoCodeToResources.put("SH", new LanguageResources("Serbo-Croatian", R.string.SH));
        isoCodeToResources.put("SD", new LanguageResources("Sindhi", R.string.SD));

        // Hack to allow lower-case ISO codes to work:
        for (final String isoCode : new ArrayList<String>(isoCodeToResources.keySet())) {
            isoCodeToResources.put(isoCode.toLowerCase(), isoCodeToResources.get(isoCode));
        }
    }

    public int getFlagIdForIsoCode(final String isoCode) {
        LanguageResources res = isoCodeToResources.get(isoCode);
        return res == null ? 0 : res.flagId;
    }

    public String isoCodeToLocalizedLanguageName(final Context context, final String isoCode) {
        String lang = new Locale(isoCode).getDisplayLanguage();
        if (!lang.equals("") && !lang.equals(isoCode))
        {
            return lang;
        }
        final LanguageResources languageResources = isoCodeToResources.get(isoCode);
        if (languageResources != null)
        {
            lang = context.getString(languageResources.nameId);
        }
        return lang;
    }

    public View createButton(final Context context, final DictionaryInfo dictionaryInfo,
                             final IndexInfo indexInfo, int size) {
        LanguageResources languageResources = isoCodeToResources.get(indexInfo.shortName);
        View result;

        if (languageResources == null || languageResources.flagId <= 0) {
            Button button = new Button(context);
            button.setText(indexInfo.shortName);
            result = button;
        } else {
            ImageButton button = new ImageButton(context);
            button.setImageResource(languageResources.flagId);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            result = button;
        }
        result.setLayoutParams(new LinearLayout.LayoutParams(size, size * 2 / 3));
        return result;
    }

    public View setupButton(Button textButton, ImageButton imageButton,
                            final DictionaryInfo dictionaryInfo,
                            final IndexInfo indexInfo, int size) {
        LanguageResources languageResources = isoCodeToResources.get(indexInfo.shortName);
        View result;

        if (languageResources == null || languageResources.flagId <= 0) {
            textButton.setText(indexInfo.shortName);
            textButton.setVisibility(View.VISIBLE);
            imageButton.setVisibility(View.GONE);
            result = textButton;
        } else {
            imageButton.setImageResource(languageResources.flagId);
            imageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            textButton.setVisibility(View.GONE);
            imageButton.setVisibility(View.VISIBLE);
            result = imageButton;
        }
        // Assume dimensions have been set before in Layout
        return result;
    }
}
