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
package com.hughes.android.dictionary

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.hughes.android.dictionary.HtmlDisplayActivity.Companion.getHelpLaunchIntent
import com.hughes.android.dictionary.engine.Dictionary
import com.hughes.android.dictionary.engine.DictionaryInfo
import com.hughes.android.dictionary.engine.DictionaryInfo.IndexInfo
import com.hughes.android.dictionary.engine.TransliteratorManager
import com.hughes.android.dictionary.engine.TransliteratorManager.ThreadSetup
import com.hughes.util.ListUtil
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.BufferUnderflowException
import java.util.Collections
import java.util.Locale
import java.util.stream.Collectors

enum class DictionaryApplication {
    INSTANCE;

    private var appContext: Context? = null

    enum class Theme(val themeId: Int) {
        DEFAULT(R.style.Theme_Default),
        LIGHT(R.style.Theme_Light)
    }

    class DictionaryConfig {
        // User-ordered list, persisted, just the ones that are/have been
        // present.
        val dictionaryFilesOrdered: MutableList<String> = ArrayList()

        val uncompressedFilenameToDictionaryInfo: MutableMap<String, DictionaryInfo> =
            HashMap()
    }

    var dictionaryConfig: DictionaryConfig? = null

    @JvmField
    var languageButtonPixels: Int = -1

    @Synchronized
    fun init(c: Context?) {
        if (appContext != null) {
            assert(c === appContext)
            return
        }
        appContext = c
        Log.d("QuickDic", "Application: onCreate")
        TransliteratorManager.init(null, threadBackground)
        DictionaryApplication.staticInit(appContext!!)

        languageButtonPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 60f, appContext!!.resources.displayMetrics
        ).toInt()

        // Load the dictionaries we know about.
        dictionaryConfig = readConfig(appContext!!)

        // Theme stuff.
        appContext!!.setTheme(selectedTheme.themeId)
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext!!)
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            Log.d("QuickDic", "prefs changed: $key")
            if (key == appContext!!.getString(R.string.themeKey)) {
                appContext!!.setTheme(selectedTheme.themeId)
            }
        }
    }

    private fun selectDefaultDir(): String {
        val defaultDictDir = File(Environment.getExternalStorageDirectory(), "quickDic")
        val dir = defaultDictDir.absolutePath
        val dictDir = File(dir)
        val fileList = if (dictDir.isDirectory) dictDir.list() else null
        if (fileList != null && fileList.size > 0) {
            return dir
        }
        var efd: File? = null
        try {
            efd = appContext!!.getExternalFilesDir(null)
        } catch (_: Exception) {
        }
        if (efd != null) {
            efd.mkdirs()
            if (!dictDir.isDirectory) {
                appContext!!.getExternalFilesDirs(null)
            }
            if (efd.isDirectory && efd.canWrite() && checkFileCreate(DocumentFile.fromFile(efd))) {
                return efd.absolutePath
            }
        }
        if (!dictDir.isDirectory && !dictDir.mkdirs()) {
            return appContext!!.filesDir.absolutePath
        }
        return dir
    }

    @get:Synchronized
    val dictDir: DocumentFile
        get() {
            // This metaphor doesn't work, because we've already reset
            // prefsMightHaveChanged.
            val prefs =
                PreferenceManager.getDefaultSharedPreferences(appContext!!)
            var dir: String =
                prefs.getString(appContext!!.getString(R.string.quickdicDirectoryKey), "")!!
            if (dir.isEmpty()) {
                dir = selectDefaultDir()
            }
            val dictDir: DocumentFile
            val dirUri = Uri.parse(dir)
            if ("content" == dirUri.scheme) {
                dictDir = DocumentFile.fromTreeUri(appContext!!, dirUri)!!
            } else {
                val df = File(dir)
                df.mkdirs()
                dictDir = DocumentFile.fromFile(df)
            }
            if (!dictDir.isDirectory) {
                appContext!!.getExternalFilesDirs(null)
            }
            return dictDir
        }

    private fun defaultWordListFile(): DocumentFile? {
        val d = dictDir
        val f = d.findFile("wordList.txt")
        return f ?: d.createFile("", "wordList.txt")
    }

    val wordListFile: DocumentFile?
        get() {
            val prefs =
                PreferenceManager.getDefaultSharedPreferences(appContext!!)
            val file: String =
                prefs.getString(appContext!!.getString(R.string.wordListFileKey), "")!!
            if (file.isEmpty()) return defaultWordListFile()
            val u = Uri.parse(file)
            if ("content" == u.scheme) return DocumentFile.fromSingleUri(appContext!!, u)
            if (u.path == null) return defaultWordListFile()
            return DocumentFile.fromFile(File(u.path!!))
        }

    val selectedTheme: Theme
        get() {
            val prefs =
                PreferenceManager.getDefaultSharedPreferences(appContext!!)
            val theme: String =
                prefs.getString(appContext!!.getString(R.string.themeKey), "themeSystem")!!
            when (theme) {
                "themeLight" -> {
                    return Theme.LIGHT
                }
                "themeSystem" -> {
                    val mode = (appContext!!.resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK)
                    return (if (mode == Configuration.UI_MODE_NIGHT_NO) Theme.LIGHT else Theme.DEFAULT)
                }
                else -> {
                    return Theme.DEFAULT
                }
            }
        }

    fun getPath(uncompressedFilename: String): DocumentFile {
        val res = dictDir.findFile(uncompressedFilename)
        return res ?: DocumentFile.fromFile(File(uncompressedFilename))
    }

    var defaultLangISO2: String = Locale.getDefault().language.lowercase(Locale.getDefault())
    var defaultLangName: String? = null
    val fileToNameCache: MutableMap<String?, String?> = HashMap()

    fun sortedIndexInfos(indexInfos: MutableList<IndexInfo>): MutableList<IndexInfo> {
        // Hack to put the default locale first in the name.
        if (indexInfos.size > 1 &&
            indexInfos[1].shortName.lowercase(Locale.getDefault()) == defaultLangISO2
        ) {
            val result: MutableList<IndexInfo> = ArrayList(indexInfos)
            ListUtil.swap(result, 0, 1)
            return result
        }
        return indexInfos
    }

    @Synchronized
    fun getDictionaryName(uncompressedFilename: String): String {
        val currentLocale = Locale.getDefault().language.lowercase(Locale.getDefault())
        if (currentLocale != defaultLangISO2) {
            defaultLangISO2 = currentLocale
            fileToNameCache.clear()
            defaultLangName = null
        }
        if (defaultLangName == null) {
            defaultLangName =
                IsoUtils.INSTANCE.isoCodeToLocalizedLanguageName(appContext, defaultLangISO2)
        }

        var name = fileToNameCache[uncompressedFilename]
        if (name != null) {
            return name
        }

        val dictionaryInfo: DictionaryInfo? =
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!![uncompressedFilename]
        if (dictionaryInfo != null) {
            val sortedIndexInfos = sortedIndexInfos(dictionaryInfo.indexInfos)
            name = sortedIndexInfos.stream()
                .map { e ->
                    IsoUtils.INSTANCE.isoCodeToLocalizedLanguageName(
                        appContext,
                        e!!.shortName
                    )
                }
                .collect(Collectors.joining("-"))
        } else {
            name = uncompressedFilename.replace(".quickdic", "")
        }
        fileToNameCache[uncompressedFilename] = name
        return name
    }

    @Synchronized
    fun moveDictionaryToTop(dictionaryInfo: DictionaryInfo) {
        dictionaryConfig!!.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename)
        dictionaryConfig!!.dictionaryFilesOrdered.add(0, dictionaryInfo.uncompressedFilename)
        writeConfig(appContext!!, dictionaryConfig!!)
    }

    @Synchronized
    fun sortDictionaries() {
        Collections.sort(
            dictionaryConfig!!.dictionaryFilesOrdered,
            uncompressedFilenameComparator
        )
        writeConfig(appContext!!, dictionaryConfig!!)
    }

    @Synchronized
    fun deleteDictionary(dictionaryInfo: DictionaryInfo) {
        while (dictionaryConfig!!.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename)) {
        }
        dictionaryConfig!!.uncompressedFilenameToDictionaryInfo
            .remove(dictionaryInfo.uncompressedFilename)
        getPath(dictionaryInfo.uncompressedFilename).delete()
        writeConfig(appContext!!, dictionaryConfig!!)
    }

    val collator: java.util.Comparator<Any?>? = CollatorWrapper.getInstance()
    val uncompressedFilenameComparator: java.util.Comparator<String> =
        Comparator { uncompressedFilename1, uncompressedFilename2 ->
            val name1 = getDictionaryName(uncompressedFilename1)
            val name2 = getDictionaryName(uncompressedFilename2)
            if (!defaultLangName!!.isEmpty()) {
                if (name1.startsWith("$defaultLangName-")
                    && !name2.startsWith("$defaultLangName-")
                ) {
                    return@Comparator -1
                } else if (name2.startsWith("$defaultLangName-")
                    && !name1.startsWith("$defaultLangName-")
                ) {
                    return@Comparator 1
                }
            }
            collator?.compare(name1, name2)
                ?: name1.compareTo(
                    name2,
                    ignoreCase = true
                )
        }
    val dictionaryInfoComparator: Comparator<DictionaryInfo> =
        Comparator { d1, d2 ->
            // Single-index dictionaries first.
            if (d1.indexInfos.size != d2.indexInfos.size) {
                return@Comparator d1.indexInfos.size - d2.indexInfos.size
            }
            uncompressedFilenameComparator.compare(
                d1.uncompressedFilename,
                d2.uncompressedFilename
            )
        }

    fun backgroundUpdateDictionaries(onUpdateFinished: Runnable?) {
        Thread {
            val oldDictionaryConfig = DictionaryConfig()
            synchronized(this@DictionaryApplication) {
                oldDictionaryConfig.dictionaryFilesOrdered
                    .addAll(dictionaryConfig!!.dictionaryFilesOrdered)
            }
            val newDictionaryConfig = DictionaryConfig()
            for (uncompressedFilename in oldDictionaryConfig.dictionaryFilesOrdered) {
                val dictFile = getPath(uncompressedFilename)
                val dictionaryInfo: DictionaryInfo =
                    getDictionaryInfo(dictFile, appContext!!.contentResolver)
                if (dictionaryInfo.isValid || dictFile.exists()) {
                    newDictionaryConfig.dictionaryFilesOrdered.add(uncompressedFilename)
                    newDictionaryConfig.uncompressedFilenameToDictionaryInfo[uncompressedFilename] =
                        dictionaryInfo
                }
            }

            // Are there dictionaries on the device that we didn't know
            // about already?
            // Pick them up and put them at the end of the list.
            val toAddSorted: MutableList<String> = ArrayList()
            val dictDirFiles = dictDir.listFiles()
            if (dictDirFiles != null) {
                for (file in dictDirFiles) {
                    if (file.name!!.endsWith(".zip")) {
                        if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!!
                                .containsKey(file.name!!.replace(".zip", ""))
                        ) {
                            file.delete()
                        }
                    }
                    if (!file.name!!.endsWith(".quickdic")) {
                        continue
                    }
                    if (newDictionaryConfig.uncompressedFilenameToDictionaryInfo
                            .containsKey(file.name)
                    ) {
                        // We have it in our list already.
                        continue
                    }
                    val dictionaryInfo: DictionaryInfo =
                        getDictionaryInfo(file, appContext!!.contentResolver)
                    if (!dictionaryInfo.isValid) {
                        Log.e(LOG, "Unable to parse dictionary: " + file.uri.path)
                    }

                    toAddSorted.add(file.name!!)
                    newDictionaryConfig.uncompressedFilenameToDictionaryInfo[file.name!!] =
                        dictionaryInfo
                }
            } else {
                Log.w(LOG, "dictDir is not a directory: " + dictDir.uri.path)
            }
            if (!toAddSorted.isEmpty()) {
                Collections.sort(toAddSorted, uncompressedFilenameComparator)
                newDictionaryConfig.dictionaryFilesOrdered.addAll(toAddSorted)
            }

            try {
                writeConfig(appContext!!, newDictionaryConfig)
            } catch (e: Exception) {
                Log.e(LOG, "Failed persisting dictionary configs", e)
            }

            synchronized(this@DictionaryApplication) {
                dictionaryConfig = newDictionaryConfig
            }
            try {
                onUpdateFinished?.run()
            } catch (e: Exception) {
                Log.e(LOG, "Exception running callback.", e)
            }
        }.start()
    }

    private fun matchesFilters(dictionaryInfo: DictionaryInfo, filters: Array<String>?): Boolean {
        if (filters == null) {
            return true
        }
        for (filter in filters) {
            if (!getDictionaryName(dictionaryInfo.uncompressedFilename).lowercase(Locale.getDefault())
                    .contains(
                        filter
                    )
            ) {
                return false
            }
        }
        return true
    }

    @Synchronized
    fun getDictionariesOnDevice(filters: Array<String>?): MutableList<DictionaryInfo> {
        val result: MutableList<DictionaryInfo> = ArrayList(
            dictionaryConfig!!.dictionaryFilesOrdered.size
        )
        for (uncompressedFilename in dictionaryConfig!!.dictionaryFilesOrdered) {
            val dictionaryInfo = dictionaryConfig!!.uncompressedFilenameToDictionaryInfo[uncompressedFilename]
            if (dictionaryInfo != null && matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo)
            }
        }
        return result
    }

    fun getDownloadableDictionaries(filters: Array<String>?): MutableList<DictionaryInfo> {
        val result: MutableList<DictionaryInfo> = ArrayList(
            dictionaryConfig!!.dictionaryFilesOrdered.size
        )

        val remaining: MutableMap<String, DictionaryInfo> = HashMap(
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!!
        )
        remaining.keys.removeAll(dictionaryConfig!!.dictionaryFilesOrdered)
        for (dictionaryInfo in remaining.values) {
            if (matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo)
            }
        }
        Collections.sort(result, dictionaryInfoComparator)
        return result
    }

    fun updateAvailable(dictionaryInfo: DictionaryInfo): Boolean {
        val downloadable: DictionaryInfo? =
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!![dictionaryInfo.uncompressedFilename]
        return downloadable != null &&
                downloadable.creationMillis > dictionaryInfo.creationMillis
    }

    fun getDownloadable(uncompressedFilename: String?): DictionaryInfo? {
        return DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!![uncompressedFilename]
    }

    companion object {
        const val LOG: String = "QuickDicApp"

        @JvmField
        val threadBackground: ThreadSetup = ThreadSetup {
            // THREAD_PRIORITY_BACKGROUND seemed like a good idea, but it
            // can make Transliterator go from 20 seconds to 3 minutes (!)
            Process.setThreadPriority(Process.THREAD_PRIORITY_LESS_FAVORABLE)
        }

        // Static, determined by resources (and locale).
        // Unordered.
        var DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO: MutableMap<String, DictionaryInfo>? =
            null

        @Synchronized
        fun staticInit(context: Context) {
            if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO != null) {
                return
            }
            DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO =
                HashMap()
            val reader = BufferedReader(
                InputStreamReader(context.resources.openRawResource(R.raw.dictionary_info))
            )
            try {
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    if (line!!.isEmpty() || line[0] == '#') {
                        continue
                    }
                    val dictionaryInfo = DictionaryInfo(line)
                    DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO!![dictionaryInfo.uncompressedFilename] =
                        dictionaryInfo
                }
            } catch (e: IOException) {
                Log.e(LOG, "Failed to load downloadable dictionary lists.", e)
            }
            try {
                reader.close()
            } catch (_: IOException) {
            }
        }

        @JvmStatic
        fun applyTheme(activity: AppCompatActivity) {
            INSTANCE.init(activity.applicationContext)
            activity.setTheme(INSTANCE.selectedTheme.themeId)
            DynamicColors.applyToActivityIfAvailable(activity)
            activity.enableEdgeToEdge()
        }

        @JvmStatic
        fun onCreateGlobalOptionsMenu(
            context: Context, menu: Menu
        ) {
            val c = context.applicationContext

            val preferences = menu.add(c.getString(R.string.settings))
            preferences.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            preferences.setOnMenuItemClickListener { _ ->
                PreferenceActivity.prefsMightHaveChanged = true
                val intent = Intent(c, PreferenceActivity::class.java)
                context.startActivity(intent)
                false
            }

            val help = menu.add(c.getString(R.string.help))
            help.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            help.setOnMenuItemClickListener { _ ->
                context.startActivity(getHelpLaunchIntent(c))
                false
            }

            val reportIssue = menu.add(c.getString(R.string.reportIssue))
            reportIssue.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            reportIssue.setOnMenuItemClickListener { _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri
                    .parse("https://github.com/rdoeffinger/Dictionary/issues")
                context.startActivity(intent)
                false
            }

            val about = menu.add(c.getString(R.string.about))
            about.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            about.setOnMenuItemClickListener { _ ->
                val intent = Intent(c, AboutActivity::class.java)
                context.startActivity(intent)
                false
            }
        }

        @JvmStatic
        fun checkFileCreate(dir: DocumentFile): Boolean {
            var testfile = dir.findFile("quickdic_writetest")
            testfile?.delete()
            if (testfile != null && testfile.exists()) return false
            testfile = dir.createFile("", "quickdic_writetest")
            if (testfile == null) return false
            return testfile.exists() and testfile.delete()
        }

        // get DictionaryInfo for case when Dictionary cannot be opened
        private fun getErrorDictionaryInfo(file: DocumentFile): DictionaryInfo {
            val dictionaryInfo = DictionaryInfo()
            dictionaryInfo.uncompressedFilename = file.name
            dictionaryInfo.uncompressedBytes = file.length()
            return dictionaryInfo
        }

        fun getDictionaryInfo(file: DocumentFile, r: ContentResolver): DictionaryInfo {
            try {
                r.openAssetFileDescriptor(file.uri, "r")!!.createInputStream().use { s ->
                    val dict = Dictionary(s.channel)
                    val dictionaryInfo = dict.getDictionaryInfo()
                    dictionaryInfo.uncompressedFilename = file.name
                    dictionaryInfo.uncompressedBytes = file.length()
                    s.close()
                    return dictionaryInfo
                }
            } catch (_: IOException) {
                return getErrorDictionaryInfo(file)
            } catch (_: IllegalArgumentException) {
                // Most likely due to a Buffer.limit beyond size of file,
                // do not crash just because of a truncated dictionary file
                return getErrorDictionaryInfo(file)
            } catch (_: BufferUnderflowException) {
                // Most likely due to a read beyond the buffer limit set,
                // do not crash just because of a truncated or corrupt dictionary file
                return getErrorDictionaryInfo(file)
            }
        }

        fun writeConfig(context: Context, config: DictionaryConfig) {
            val file = File(context.filesDir, C.DICTIONARY_CONFIGS)
            try {
                file.writeText("v1\n" + config.dictionaryFilesOrdered.joinToString("\n"))
            } catch (e: Exception) {
                Log.e(LOG, "Failed to write dictionary config", e)
            }
        }

        fun readConfig(context: Context): DictionaryConfig {
            val config = DictionaryConfig()
            val file = File(context.filesDir, C.DICTIONARY_CONFIGS)
            if (!file.exists()) return config
            try {
                val lines = file.readLines()
                if (lines.isEmpty() || lines[0] != "v1") return config

                for (name in lines.drop(1)) {
                    val dictFile = INSTANCE.getPath(name)
                    if (!dictFile.exists()) continue;
                    config.dictionaryFilesOrdered.add(name)
                    // populate dummy until background scan completes
                    config.uncompressedFilenameToDictionaryInfo[name] =
                        DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO?.get(name)
                            ?: getErrorDictionaryInfo(dictFile)
                }
            } catch (e: Exception) {
                Log.e(LOG, "Failed to read dictionary config", e)
            }
            return config
        }
    }
}
