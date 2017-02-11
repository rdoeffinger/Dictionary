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

package com.hughes.android.dictionary;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Language;
import com.hughes.android.dictionary.engine.Language.LanguageResources;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.PersistentObjectCache;
import com.hughes.util.ListUtil;
import java.text.Collator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DictionaryApplication extends Application {

    static final String LOG = "QuickDicApp";

    // If set to false, avoid use of ICU collator
    // Works well enough for most european languages,
    // gives faster startup and avoids crashes on some
    // devices due to Dalvik bugs (e.g. ARMv6, S5570i, CM11)
    // when using ICU4J.
    // Leave it enabled by default for correctness except
    // for my known broken development/performance test device config.
    //static public final boolean USE_COLLATOR = !android.os.Build.FINGERPRINT.equals("Samsung/cm_tassve/tassve:4.4.4/KTU84Q/20150211:userdebug/release-keys");
    static public final boolean USE_COLLATOR = true;

    // Static, determined by resources (and locale).
    // Unordered.
    static Map<String, DictionaryInfo> DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO = null;

    enum Theme {
        DEFAULT(R.style.Theme_Default,
        R.style.Theme_Default_TokenRow_Fg,
        R.color.theme_default_token_row_fg,
        R.drawable.theme_default_token_row_main_bg,
        R.drawable.theme_default_token_row_other_bg,
        R.drawable.theme_default_normal_row_bg),

        LIGHT(R.style.Theme_Light,
        R.style.Theme_Light_TokenRow_Fg,
        R.color.theme_light_token_row_fg,
        R.drawable.theme_light_token_row_main_bg,
        R.drawable.theme_light_token_row_other_bg,
        R.drawable.theme_light_normal_row_bg);

        Theme(final int themeId, final int tokenRowFg,
        final int tokenRowFgColor,
        final int tokenRowMainBg, final int tokenRowOtherBg,
        final int normalRowBg) {
            this.themeId = themeId;
            this.tokenRowFg = tokenRowFg;
            this.tokenRowFgColor = tokenRowFgColor;
            this.tokenRowMainBg = tokenRowMainBg;
            this.tokenRowOtherBg = tokenRowOtherBg;
            this.normalRowBg = normalRowBg;
        }

        final int themeId;
        final int tokenRowFg;
        final int tokenRowFgColor;
        final int tokenRowMainBg;
        final int tokenRowOtherBg;
        final int normalRowBg;
    }

    // Useful:
    // http://www.loc.gov/standards/iso639-2/php/code_list.php
    public static final Map<String, LanguageResources> isoCodeToResources = new HashMap<String, LanguageResources>();
    static {
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

    public static final class DictionaryConfig implements Serializable {
        private static final long serialVersionUID = -1444177164708201263L;
        // User-ordered list, persisted, just the ones that are/have been
        // present.
        final List<String> dictionaryFilesOrdered = new ArrayList<String>();

        final Map<String, DictionaryInfo> uncompressedFilenameToDictionaryInfo = new HashMap<String, DictionaryInfo>();

        /**
         * Sometimes a deserialized version of this data structure isn't valid.
         * @return
         */
        boolean isValid() {
            return uncompressedFilenameToDictionaryInfo != null && dictionaryFilesOrdered != null;
        }
    }

    DictionaryConfig dictionaryConfig = null;

    int languageButtonPixels = -1;

    static synchronized void staticInit(final Context context) {
        if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO != null) {
            return;
        }
        DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO = new HashMap<String, DictionaryInfo>();
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(context.getResources().openRawResource(R.raw.dictionary_info)));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                }
                final DictionaryInfo dictionaryInfo = new DictionaryInfo(line);
                DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.put(
                    dictionaryInfo.uncompressedFilename, dictionaryInfo);
            }
        } catch (IOException e) {
            Log.e(LOG, "Failed to load downloadable dictionary lists.", e);
        }
        try {
            reader.close();
        } catch (IOException e) {}
    }

    private File dictDir;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("QuickDic", "Application: onCreate");
        TransliteratorManager.init(null);
        staticInit(getApplicationContext());

        languageButtonPixels = (int) TypedValue.applyDimension(
                                   TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());

        // Load the dictionaries we know about.
        dictionaryConfig = PersistentObjectCache.init(getApplicationContext()).read(
                               C.DICTIONARY_CONFIGS, DictionaryConfig.class);
        if (dictionaryConfig == null) {
            dictionaryConfig = new DictionaryConfig();
        }
        if (!dictionaryConfig.isValid()) {
            dictionaryConfig = new DictionaryConfig();
        }

        // Theme stuff.
        setTheme(getSelectedTheme().themeId);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                  String key) {
                Log.d("QuickDic", "prefs changed: " + key);
                if (key.equals(getString(R.string.themeKey))) {
                    setTheme(getSelectedTheme().themeId);
                }
            }
        });
    }

    public void onCreateGlobalOptionsMenu(
        final Context context, final Menu menu) {
        final MenuItem about = menu.add(getString(R.string.about));
        MenuItemCompat.setShowAsAction(about, MenuItem.SHOW_AS_ACTION_NEVER);
        about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                final Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
                context.startActivity(intent);
                return false;
            }
        });

        final MenuItem help = menu.add(getString(R.string.help));
        MenuItemCompat.setShowAsAction(help, MenuItem.SHOW_AS_ACTION_NEVER);
        help.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                context.startActivity(HtmlDisplayActivity.getHelpLaunchIntent(getApplicationContext()));
                return false;
            }
        });

        final MenuItem preferences = menu.add(getString(R.string.settings));
        MenuItemCompat.setShowAsAction(preferences, MenuItem.SHOW_AS_ACTION_NEVER);
        preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                PreferenceActivity.prefsMightHaveChanged = true;
                final Intent intent = new Intent(getApplicationContext(), PreferenceActivity.class);
                context.startActivity(intent);
                return false;
            }
        });

        final MenuItem reportIssue = menu.add(getString(R.string.reportIssue));
        MenuItemCompat.setShowAsAction(reportIssue, MenuItem.SHOW_AS_ACTION_NEVER);
        reportIssue.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri
                               .parse("http://github.com/rdoeffinger/Dictionary/issues"));
                context.startActivity(intent);
                return false;
            }
        });
    }

    private String selectDefaultDir() {
        final File defaultDictDir = new File(Environment.getExternalStorageDirectory(), "quickDic");
        String dir = defaultDictDir.getAbsolutePath();
        File dictDir = new File(dir);
        String[] fileList = dictDir.isDirectory() ? dictDir.list() : null;
        if (fileList != null && fileList.length > 0) {
            return dir;
        }
        File efd = null;
        try {
            efd = getApplicationContext().getExternalFilesDir(null);
        } catch (Exception e) {
        }
        if (efd != null) {
            efd.mkdirs();
            if (!dictDir.isDirectory() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getApplicationContext().getExternalFilesDirs(null);
            }
            if (efd.isDirectory() && efd.canWrite() && checkFileCreate(efd)) {
                return efd.getAbsolutePath();
            }
        }
        if (!dictDir.isDirectory() && !dictDir.mkdirs()) {
            return getApplicationContext().getFilesDir().getAbsolutePath();
        }
        return dir;
    }

    public synchronized File getDictDir() {
        // This metaphor doesn't work, because we've already reset
        // prefsMightHaveChanged.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String dir = prefs.getString(getString(R.string.quickdicDirectoryKey), "");
        if (dir.isEmpty()) {
            dir = selectDefaultDir();
        }
        dictDir = new File(dir);
        dictDir.mkdirs();
        if (!dictDir.isDirectory() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getApplicationContext().getExternalFilesDirs(null);
        }
        return dictDir;
    }

    static public boolean checkFileCreate(File dir) {
        boolean res = false;
        File testfile = new File(dir, "quickdic_writetest");
        try {
            testfile.delete();
            res = testfile.createNewFile() & testfile.delete();
        } catch (Exception e) {
        }
        return res;
    }

    public File getWordListFile() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String file = prefs.getString(getString(R.string.wordListFileKey), "");
        if (file.isEmpty()) {
            return new File(getDictDir(), "wordList.txt");
        }
        return new File(file);
    }

    public Theme getSelectedTheme() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String theme = prefs.getString(getString(R.string.themeKey), "themeLight");
        if (theme.equals("themeLight")) {
            return Theme.LIGHT;
        } else {
            return Theme.DEFAULT;
        }
    }

    public File getPath(String uncompressedFilename) {
        return new File(getDictDir(), uncompressedFilename);
    }

    String defaultLangISO2 = Locale.getDefault().getLanguage().toLowerCase();
    String defaultLangName = null;
    final Map<String, String> fileToNameCache = new HashMap<String, String>();

    public String isoCodeToLocalizedLanguageName(final String isoCode) {
        String lang = new Locale(isoCode).getDisplayLanguage();
        if (!lang.equals("") && !lang.equals(isoCode))
        {
            return lang;
        }
        final Language.LanguageResources languageResources = isoCodeToResources
                .get(isoCode);
        if (languageResources != null)
        {
            lang = getApplicationContext().getString(languageResources.nameId);
        }
        return lang;
    }

    public List<IndexInfo> sortedIndexInfos(List<IndexInfo> indexInfos) {
        // Hack to put the default locale first in the name.
        if (indexInfos.size() > 1 &&
                indexInfos.get(1).shortName.toLowerCase().equals(defaultLangISO2)) {
            List<IndexInfo> result = new ArrayList<DictionaryInfo.IndexInfo>(indexInfos);
            ListUtil.swap(result, 0, 1);
            return result;
        }
        return indexInfos;
    }

    public synchronized String getDictionaryName(final String uncompressedFilename) {
        final String currentLocale = Locale.getDefault().getLanguage().toLowerCase();
        if (!currentLocale.equals(defaultLangISO2)) {
            defaultLangISO2 = currentLocale;
            fileToNameCache.clear();
            defaultLangName = null;
        }
        if (defaultLangName == null) {
            defaultLangName = isoCodeToLocalizedLanguageName(defaultLangISO2);
        }

        String name = fileToNameCache.get(uncompressedFilename);
        if (name != null) {
            return name;
        }

        final DictionaryInfo dictionaryInfo = DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                                              .get(uncompressedFilename);
        if (dictionaryInfo != null) {
            final StringBuilder nameBuilder = new StringBuilder();

            List<IndexInfo> sortedIndexInfos = sortedIndexInfos(dictionaryInfo.indexInfos);
            for (int i = 0; i < sortedIndexInfos.size(); ++i) {
                if (i > 0) {
                    nameBuilder.append("-");
                }
                nameBuilder
                .append(isoCodeToLocalizedLanguageName(sortedIndexInfos.get(i).shortName));
            }
            name = nameBuilder.toString();
        } else {
            name = uncompressedFilename.replace(".quickdic", "");
        }
        fileToNameCache.put(uncompressedFilename, name);
        return name;
    }

    public View createButton(final Context context, final DictionaryInfo dictionaryInfo,
                             final IndexInfo indexInfo) {
        LanguageResources languageResources = isoCodeToResources.get(indexInfo.shortName);
        View result;

        if (languageResources == null || languageResources.flagId <= 0) {
            Button button = new Button(context);
            button.setText(indexInfo.shortName);
            result = button;
        } else {
            ImageButton button = new ImageButton(context);
            button.setImageResource(languageResources.flagId);
            button.setScaleType(ScaleType.FIT_CENTER);
            result = button;
        }
        result.setLayoutParams(new LinearLayout.LayoutParams(languageButtonPixels, languageButtonPixels * 2 / 3));
        return result;
    }

    public synchronized void moveDictionaryToTop(final DictionaryInfo dictionaryInfo) {
        dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename);
        dictionaryConfig.dictionaryFilesOrdered.add(0, dictionaryInfo.uncompressedFilename);
        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    public synchronized void sortDictionaries() {
        Collections.sort(dictionaryConfig.dictionaryFilesOrdered, uncompressedFilenameComparator);
        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    public synchronized void deleteDictionary(final DictionaryInfo dictionaryInfo) {
        while (dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename)) {
        }
        dictionaryConfig.uncompressedFilenameToDictionaryInfo
        .remove(dictionaryInfo.uncompressedFilename);
        getPath(dictionaryInfo.uncompressedFilename).delete();
        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    final Comparator collator = USE_COLLATOR ? Collator.getInstance() : String.CASE_INSENSITIVE_ORDER;
    final Comparator<String> uncompressedFilenameComparator = new Comparator<String>() {
        @Override
        public int compare(String uncompressedFilename1, String uncompressedFilename2) {
            final String name1 = getDictionaryName(uncompressedFilename1);
            final String name2 = getDictionaryName(uncompressedFilename2);
            if (defaultLangName.length() > 0) {
                if (name1.startsWith(defaultLangName + "-")
                        && !name2.startsWith(defaultLangName + "-")) {
                    return -1;
                } else if (name2.startsWith(defaultLangName + "-")
                           && !name1.startsWith(defaultLangName + "-")) {
                    return 1;
                }
            }
            return collator.compare(name1, name2);
        }
    };
    final Comparator<DictionaryInfo> dictionaryInfoComparator = new Comparator<DictionaryInfo>() {
        @Override
        public int compare(DictionaryInfo d1, DictionaryInfo d2) {
            // Single-index dictionaries first.
            if (d1.indexInfos.size() != d2.indexInfos.size()) {
                return d1.indexInfos.size() - d2.indexInfos.size();
            }
            return uncompressedFilenameComparator.compare(d1.uncompressedFilename,
                    d2.uncompressedFilename);
        }
    };

    public void backgroundUpdateDictionaries(final Runnable onUpdateFinished) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DictionaryConfig oldDictionaryConfig = new DictionaryConfig();
                synchronized (DictionaryApplication.this) {
                    oldDictionaryConfig.dictionaryFilesOrdered
                    .addAll(dictionaryConfig.dictionaryFilesOrdered);
                }
                final DictionaryConfig newDictionaryConfig = new DictionaryConfig();
                for (final String uncompressedFilename : oldDictionaryConfig.dictionaryFilesOrdered) {
                    final File dictFile = getPath(uncompressedFilename);
                    final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(dictFile);
                    if (dictionaryInfo.isValid() || dictFile.exists()) {
                        newDictionaryConfig.dictionaryFilesOrdered.add(uncompressedFilename);
                        newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                            uncompressedFilename, dictionaryInfo);
                    }
                }

                // Are there dictionaries on the device that we didn't know
                // about already?
                // Pick them up and put them at the end of the list.
                final List<String> toAddSorted = new ArrayList<String>();
                final File[] dictDirFiles = getDictDir().listFiles();
                if (dictDirFiles != null) {
                    for (final File file : dictDirFiles) {
                        if (file.getName().endsWith(".zip")) {
                            if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                                    .containsKey(file.getName().replace(".zip", ""))) {
                                file.delete();
                            }
                        }
                        if (!file.getName().endsWith(".quickdic")) {
                            continue;
                        }
                        if (newDictionaryConfig.uncompressedFilenameToDictionaryInfo
                                .containsKey(file.getName())) {
                            // We have it in our list already.
                            continue;
                        }
                        final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(file);
                        if (!dictionaryInfo.isValid()) {
                            Log.e(LOG, "Unable to parse dictionary: " + file.getPath());
                        }

                        toAddSorted.add(file.getName());
                        newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                            file.getName(), dictionaryInfo);
                    }
                } else {
                    Log.w(LOG, "dictDir is not a directory: " + getDictDir().getPath());
                }
                if (!toAddSorted.isEmpty()) {
                    Collections.sort(toAddSorted, uncompressedFilenameComparator);
                    newDictionaryConfig.dictionaryFilesOrdered.addAll(toAddSorted);
                }

                try {
                    PersistentObjectCache.getInstance()
                    .write(C.DICTIONARY_CONFIGS, newDictionaryConfig);
                } catch (Exception e) {
                    Log.e(LOG, "Failed persisting dictionary configs", e);
                }

                synchronized (DictionaryApplication.this) {
                    dictionaryConfig = newDictionaryConfig;
                }

                try {
                    onUpdateFinished.run();
                } catch (Exception e) {
                    Log.e(LOG, "Exception running callback.", e);
                }
            }
        }).start();
    }

    public boolean matchesFilters(final DictionaryInfo dictionaryInfo, final String[] filters) {
        if (filters == null) {
            return true;
        }
        for (final String filter : filters) {
            if (!getDictionaryName(dictionaryInfo.uncompressedFilename).toLowerCase().contains(
                        filter)) {
                return false;
            }
        }
        return true;
    }

    public synchronized List<DictionaryInfo> getDictionariesOnDevice(String[] filters) {
        final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(
            dictionaryConfig.dictionaryFilesOrdered.size());
        for (final String uncompressedFilename : dictionaryConfig.dictionaryFilesOrdered) {
            final DictionaryInfo dictionaryInfo = dictionaryConfig.uncompressedFilenameToDictionaryInfo
                                                  .get(uncompressedFilename);
            if (dictionaryInfo != null && matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo);
            }
        }
        return result;
    }

    public List<DictionaryInfo> getDownloadableDictionaries(String[] filters) {
        final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(
            dictionaryConfig.dictionaryFilesOrdered.size());

        final Map<String, DictionaryInfo> remaining = new HashMap<String, DictionaryInfo>(
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO);
        remaining.keySet().removeAll(dictionaryConfig.dictionaryFilesOrdered);
        for (final DictionaryInfo dictionaryInfo : remaining.values()) {
            if (matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo);
            }
        }
        Collections.sort(result, dictionaryInfoComparator);
        return result;
    }

    public synchronized boolean isDictionaryOnDevice(String uncompressedFilename) {
        return dictionaryConfig.uncompressedFilenameToDictionaryInfo.get(uncompressedFilename) != null;
    }

    public boolean updateAvailable(final DictionaryInfo dictionaryInfo) {
        final DictionaryInfo downloadable =
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.get(
                dictionaryInfo.uncompressedFilename);
        return downloadable != null &&
               downloadable.creationMillis > dictionaryInfo.creationMillis;
    }

    public DictionaryInfo getDownloadable(final String uncompressedFilename) {
        final DictionaryInfo downloadable = DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                                            .get(uncompressedFilename);
        return downloadable;
    }

}
