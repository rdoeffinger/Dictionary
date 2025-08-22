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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import com.hughes.android.dictionary.engine.DictionaryInfo;
import com.hughes.android.dictionary.engine.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.PersistentObjectCache;
import com.hughes.util.ListUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum DictionaryApplication {
    INSTANCE;

    private Context appContext;

    static final String LOG = "QuickDicApp";

    // If set to false, avoid use of ICU collator
    // Works well enough for most european languages,
    // gives faster startup and avoids crashes on some
    // devices due to Dalvik bugs (e.g. ARMv6, S5570i, CM11)
    // when using ICU4J.
    // Leave it enabled by default for correctness except
    // for my known broken development/performance test device config.
    //static public final boolean USE_COLLATOR = !android.os.Build.FINGERPRINT.equals("Samsung/cm_tassve/tassve:4.4.4/KTU84Q/20150211:userdebug/release-keys");
    public static final boolean USE_COLLATOR = true;

    public static final TransliteratorManager.ThreadSetup threadBackground = () -> {
        // THREAD_PRIORITY_BACKGROUND seemed like a good idea, but it
        // can make Transliterator go from 20 seconds to 3 minutes (!)
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE);
    };

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

    public static final class DictionaryConfig implements Serializable {
        private static final long serialVersionUID = -1444177164708201263L;
        // User-ordered list, persisted, just the ones that are/have been
        // present.
        final List<String> dictionaryFilesOrdered = new ArrayList<>();

        final Map<String, DictionaryInfo> uncompressedFilenameToDictionaryInfo = new HashMap<>();

        /**
         * Sometimes a deserialized version of this data structure isn't valid.
         */
        @SuppressWarnings("ConstantConditions")
        boolean isValid() {
            return uncompressedFilenameToDictionaryInfo != null && dictionaryFilesOrdered != null;
        }
    }

    DictionaryConfig dictionaryConfig = null;

    public int languageButtonPixels = -1;

    static synchronized void staticInit(final Context context) {
        if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO != null) {
            return;
        }
        DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO = new HashMap<>();
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(context.getResources().openRawResource(R.raw.dictionary_info)));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
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
        } catch (IOException ignored) {}
    }

    public synchronized void init(Context c) {
        if (appContext != null) {
            assert c == appContext;
            return;
        }
        appContext = c;
        Log.d("QuickDic", "Application: onCreate");
        TransliteratorManager.init(null, threadBackground);
        staticInit(appContext);

        languageButtonPixels = (int) TypedValue.applyDimension(
                                   TypedValue.COMPLEX_UNIT_DIP, 60, appContext.getResources().getDisplayMetrics());

        // Load the dictionaries we know about.
        dictionaryConfig = PersistentObjectCache.init(appContext).read(
                               C.DICTIONARY_CONFIGS, DictionaryConfig.class);
        if (dictionaryConfig == null) {
            dictionaryConfig = new DictionaryConfig();
        }
        if (!dictionaryConfig.isValid()) {
            dictionaryConfig = new DictionaryConfig();
        }

        // Theme stuff.
        appContext.setTheme(getSelectedTheme().themeId);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        prefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            Log.d("QuickDic", "prefs changed: " + key);
            if (key.equals(appContext.getString(R.string.themeKey))) {
                appContext.setTheme(getSelectedTheme().themeId);
            }
        });
    }

    public static void onCreateGlobalOptionsMenu(
        final Context context, final Menu menu) {
        final Context c = context.getApplicationContext();

        final MenuItem preferences = menu.add(c.getString(R.string.settings));
        preferences.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        preferences.setOnMenuItemClickListener(menuItem -> {
            PreferenceActivity.prefsMightHaveChanged = true;
            final Intent intent = new Intent(c, PreferenceActivity.class);
            context.startActivity(intent);
            return false;
        });

        final MenuItem help = menu.add(c.getString(R.string.help));
        help.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        help.setOnMenuItemClickListener(menuItem -> {
            context.startActivity(HtmlDisplayActivity.getHelpLaunchIntent(c));
            return false;
        });

        final MenuItem reportIssue = menu.add(c.getString(R.string.reportIssue));
        reportIssue.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        reportIssue.setOnMenuItemClickListener(menuItem -> {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri
                           .parse("https://github.com/rdoeffinger/Dictionary/issues"));
            context.startActivity(intent);
            return false;
        });

        final MenuItem about = menu.add(c.getString(R.string.about));
        about.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        about.setOnMenuItemClickListener(menuItem -> {
            final Intent intent = new Intent(c, AboutActivity.class);
            context.startActivity(intent);
            return false;
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
            efd = appContext.getExternalFilesDir(null);
        } catch (Exception ignored) {
        }
        if (efd != null) {
            efd.mkdirs();
            if (!dictDir.isDirectory()) {
                appContext.getExternalFilesDirs(null);
            }
            if (efd.isDirectory() && efd.canWrite() && checkFileCreate(DocumentFile.fromFile(efd))) {
                return efd.getAbsolutePath();
            }
        }
        if (!dictDir.isDirectory() && !dictDir.mkdirs()) {
            return appContext.getFilesDir().getAbsolutePath();
        }
        return dir;
    }

    public synchronized DocumentFile getDictDir() {
        // This metaphor doesn't work, because we've already reset
        // prefsMightHaveChanged.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        String dir = prefs.getString(appContext.getString(R.string.quickdicDirectoryKey), "");
        if (dir.isEmpty()) {
            dir = selectDefaultDir();
        }
        DocumentFile dictDir;
        Uri dirUri = Uri.parse(dir);
        if ("content".equals(dirUri.getScheme())) {
            dictDir = DocumentFile.fromTreeUri(appContext, dirUri);
        } else {
            File df = new File(dir);
            df.mkdirs();
            dictDir = DocumentFile.fromFile(df);
        }
        if (!dictDir.isDirectory()) {
            appContext.getExternalFilesDirs(null);
        }
        return dictDir;
    }

    public static boolean checkFileCreate(DocumentFile dir) {
        DocumentFile testfile = dir.findFile("quickdic_writetest");
        if (testfile != null) testfile.delete();
        if (testfile != null && testfile.exists()) return false;
        testfile = dir.createFile("", "quickdic_writetest");
        if (testfile == null) return false;
        return testfile.exists() & testfile.delete();
    }

    private DocumentFile defaultWordListFile() {
        DocumentFile d = getDictDir();
        DocumentFile f = d.findFile("wordList.txt");
        return f != null ? f : d.createFile("" , "wordList.txt");
    }

    public DocumentFile getWordListFile() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        String file = prefs.getString(appContext.getString(R.string.wordListFileKey), "");
        if (file.isEmpty()) return defaultWordListFile();
        Uri u = Uri.parse(file);
        if ("content".equals(u.getScheme())) return DocumentFile.fromSingleUri(appContext, u);
        if (u.getPath() == null) return defaultWordListFile();
        return DocumentFile.fromFile(new File(u.getPath()));
    }

    public Theme getSelectedTheme() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        final String theme = prefs.getString(appContext.getString(R.string.themeKey), "themeSystem");
        if (theme.equals("themeLight")) {
            return Theme.LIGHT;
        } else if (theme.equals("themeSystem")) {
            int mode = (appContext.getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK);
            return ((mode == Configuration.UI_MODE_NIGHT_NO) ?
                    Theme.LIGHT:
                    Theme.DEFAULT);
        } else {
            return Theme.DEFAULT;
        }
    }

    public DocumentFile getPath(String uncompressedFilename) {
        DocumentFile res = getDictDir().findFile(uncompressedFilename);
        return res != null ? res : DocumentFile.fromFile(new File(uncompressedFilename));
    }

    String defaultLangISO2 = Locale.getDefault().getLanguage().toLowerCase();
    String defaultLangName = null;
    final Map<String, String> fileToNameCache = new HashMap<>();

    public List<IndexInfo> sortedIndexInfos(List<IndexInfo> indexInfos) {
        // Hack to put the default locale first in the name.
        if (indexInfos.size() > 1 &&
                indexInfos.get(1).shortName.toLowerCase().equals(defaultLangISO2)) {
            List<IndexInfo> result = new ArrayList<>(indexInfos);
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
            defaultLangName = IsoUtils.INSTANCE.isoCodeToLocalizedLanguageName(appContext, defaultLangISO2);
        }

        String name = fileToNameCache.get(uncompressedFilename);
        if (name != null) {
            return name;
        }

        final DictionaryInfo dictionaryInfo = DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                                              .get(uncompressedFilename);
        if (dictionaryInfo != null) {

            List<IndexInfo> sortedIndexInfos = sortedIndexInfos(dictionaryInfo.indexInfos);
            name = sortedIndexInfos.stream()
                   .map(e -> IsoUtils.INSTANCE.isoCodeToLocalizedLanguageName(appContext, e.shortName))
                   .collect(Collectors.joining("-"));
        } else {
            name = uncompressedFilename.replace(".quickdic", "");
        }
        fileToNameCache.put(uncompressedFilename, name);
        return name;
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

    final Comparator<Object> collator = USE_COLLATOR ? CollatorWrapper.getInstance() : null;
    final Comparator<String> uncompressedFilenameComparator = (uncompressedFilename1, uncompressedFilename2) -> {
        final String name1 = getDictionaryName(uncompressedFilename1);
        final String name2 = getDictionaryName(uncompressedFilename2);
        if (!defaultLangName.isEmpty()) {
            if (name1.startsWith(defaultLangName + "-")
                    && !name2.startsWith(defaultLangName + "-")) {
                return -1;
            } else if (name2.startsWith(defaultLangName + "-")
                    && !name1.startsWith(defaultLangName + "-")) {
                return 1;
            }
        }
        return collator != null ? collator.compare(name1, name2) : name1.compareToIgnoreCase(name2);
    };
    final Comparator<DictionaryInfo> dictionaryInfoComparator = (d1, d2) -> {
        // Single-index dictionaries first.
        if (d1.indexInfos.size() != d2.indexInfos.size()) {
            return d1.indexInfos.size() - d2.indexInfos.size();
        }
        return uncompressedFilenameComparator.compare(d1.uncompressedFilename,
                d2.uncompressedFilename);
    };

    // get DictionaryInfo for case when Dictionary cannot be opened
    private static DictionaryInfo getErrorDictionaryInfo(final DocumentFile file) {
        final DictionaryInfo dictionaryInfo = new DictionaryInfo();
        dictionaryInfo.uncompressedFilename = file.getName();
        dictionaryInfo.uncompressedBytes = file.length();
        return dictionaryInfo;
    }

    public static DictionaryInfo getDictionaryInfo(final DocumentFile file, final ContentResolver r) {
        try (FileInputStream s = r.openAssetFileDescriptor(file.getUri(), "r").createInputStream()) {
            final Dictionary dict = new Dictionary(s.getChannel());
            final DictionaryInfo dictionaryInfo = dict.getDictionaryInfo();
            dictionaryInfo.uncompressedFilename = file.getName();
            dictionaryInfo.uncompressedBytes = file.length();
            s.close();
            return dictionaryInfo;
        } catch (IOException e) {
            return getErrorDictionaryInfo(file);
        } catch (IllegalArgumentException e) {
            // Most likely due to a Buffer.limit beyond size of file,
            // do not crash just because of a truncated dictionary file
            return getErrorDictionaryInfo(file);
        } catch (BufferUnderflowException e) {
            // Most likely due to a read beyond the buffer limit set,
            // do not crash just because of a truncated or corrupt dictionary file
            return getErrorDictionaryInfo(file);
        }
    }

    public void backgroundUpdateDictionaries(final Runnable onUpdateFinished) {
        new Thread(() -> {
            final DictionaryConfig oldDictionaryConfig = new DictionaryConfig();
            synchronized (DictionaryApplication.this) {
                oldDictionaryConfig.dictionaryFilesOrdered
                .addAll(dictionaryConfig.dictionaryFilesOrdered);
            }
            final DictionaryConfig newDictionaryConfig = new DictionaryConfig();
            for (final String uncompressedFilename : oldDictionaryConfig.dictionaryFilesOrdered) {
                final DocumentFile dictFile = getPath(uncompressedFilename);
                final DictionaryInfo dictionaryInfo = getDictionaryInfo(dictFile, appContext.getContentResolver());
                if (dictionaryInfo.isValid() || dictFile.exists()) {
                    newDictionaryConfig.dictionaryFilesOrdered.add(uncompressedFilename);
                    newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                        uncompressedFilename, dictionaryInfo);
                }
            }

            // Are there dictionaries on the device that we didn't know
            // about already?
            // Pick them up and put them at the end of the list.
            final List<String> toAddSorted = new ArrayList<>();
            final DocumentFile[] dictDirFiles = getDictDir().listFiles();
            if (dictDirFiles != null) {
                for (final DocumentFile file : dictDirFiles) {
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
                    final DictionaryInfo dictionaryInfo = getDictionaryInfo(file, appContext.getContentResolver());
                    if (!dictionaryInfo.isValid()) {
                        Log.e(LOG, "Unable to parse dictionary: " + file.getUri().getPath());
                    }

                    toAddSorted.add(file.getName());
                    newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                        file.getName(), dictionaryInfo);
                }
            } else {
                Log.w(LOG, "dictDir is not a directory: " + getDictDir().getUri().getPath());
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
        }).start();
    }

    private boolean matchesFilters(final DictionaryInfo dictionaryInfo, final String[] filters) {
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
        final List<DictionaryInfo> result = new ArrayList<>(
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
        final List<DictionaryInfo> result = new ArrayList<>(
                dictionaryConfig.dictionaryFilesOrdered.size());

        final Map<String, DictionaryInfo> remaining = new HashMap<>(
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

    public boolean updateAvailable(final DictionaryInfo dictionaryInfo) {
        final DictionaryInfo downloadable =
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.get(
                dictionaryInfo.uncompressedFilename);
        return downloadable != null &&
               downloadable.creationMillis > dictionaryInfo.creationMillis;
    }

    public DictionaryInfo getDownloadable(final String uncompressedFilename) {
        return DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.get(uncompressedFilename);
    }

}
