// Copyright 2011 Google Inc. All Rights Reserved.
// Some Parts Copyright 2013 Dominik Köppl
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

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.AdapterView.OnItemClickListener
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.cursoradapter.widget.CursorAdapter
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hughes.android.dictionary.DictionaryApplication.Companion.applyTheme
import com.hughes.android.dictionary.DictionaryApplication.Companion.onCreateGlobalOptionsMenu
import com.hughes.android.dictionary.HtmlDisplayActivity.Companion.getHtmlIntent
import com.hughes.android.dictionary.engine.Dictionary
import com.hughes.android.dictionary.engine.DictionaryInfo
import com.hughes.android.dictionary.engine.DictionaryInfo.IndexInfo
import com.hughes.android.dictionary.engine.HtmlEntry
import com.hughes.android.dictionary.engine.Index
import com.hughes.android.dictionary.engine.PairEntry
import com.hughes.android.dictionary.engine.RowBase
import com.hughes.android.dictionary.engine.TokenRow
import com.hughes.android.dictionary.engine.TransliteratorManager
import com.hughes.android.util.IntentLauncher
import com.hughes.android.util.NonLinkClickableSpan
import com.hughes.util.StringUtil
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

class DictionaryActivity : AppCompatActivity() {
    private var application: DictionaryApplication? = null

    private var dictFile: DocumentFile? = null
    private var dictRaf: FileChannel? = null
    private var dictFileTitleName: String? = null

    private var dictionary: Dictionary? = null

    private var indexIndex = 0

    private var index: Index? = null

    private var rowsToShow: MutableList<RowBase>? = null // if not null, just show these rows.

    private val rand = Random()

    private val uiHandler = Handler(Looper.getMainLooper())

    private val searchExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(
                r,
                "searchExecutor"
            )
        }

    private var currentSearchOperation: SearchOperation? = null
    private val MAX_SEARCH_HISTORY = 100
    private val DEFAULT_SEARCH_HISTORY = 10
    private var searchHistoryLimit = 0
    private val searchHistory = ArrayList<String>(DEFAULT_SEARCH_HISTORY)
    private var searchHistoryCursor = MatrixCursor(arrayOf("_id", "search"))

    private var textToSpeech: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private var typeface: Typeface? = null
    private var textColorFg = Color.BLACK
    private var fontSizeSp = 0

    private val listView: ListView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(android.R.id.list)
    }

    private var searchView: SearchView? = null
    private var searchTextView: AutoCompleteTextView? = null
    private var languageButton: ImageButton? = null
    private var languageTextButton: Button? = null
    private var onQueryTextListener: SearchView.OnQueryTextListener? = null

    private var nextWordMenuItem: MenuItem? = null
    private var previousWordMenuItem: MenuItem? = null

    // Never null.
    private var wordList: DocumentFile? = null
    private var saveOnlyFirstSubentry = false
    private var clickOpensContextMenu = false
    private var filterCommands = true
    private var deleteCommands = false

    // Visible for testing.
    private var indexAdapter: ListAdapter? = null

    private fun resolveColorAttribute(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * For some languages, loading the transliterators used in this search takes
     * a long time, so we fire it up on a different thread, and don't invoke it
     * from the main thread until it's already finished once.
     */
    @Volatile
    private var indexPrepFinished = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(LOG, "onSaveInstanceState: " + searchView!!.query.toString())
        outState.putString(C.INDEX_SHORT_NAME, index!!.shortName)
        outState.putString(C.SEARCH_TOKEN, searchView!!.query.toString())
        outState.putStringArrayList(C.SEARCH_HISTORY, searchHistory)
    }

    private fun getMatchLen(search: String, e: Index.IndexEntry?): Int {
        if (e == null) return 0
        for (i in search.indices) {
            val a = search.substring(0, i + 1)
            val b = e.token.substring(0, i + 1)
            if (!a.equals(b, ignoreCase = true)) return i
        }
        return search.length
    }

    private fun dictionaryOpenFail(e: Exception) {
        Log.e(LOG, "Unable to load dictionary.", e)
        if (dictRaf != null) {
            indexAdapter = null
            listView.adapter = null
            try {
                dictRaf!!.close()
            } catch (e1: IOException) {
                Log.e(LOG, "Unable to close dictRaf.", e1)
            }
            dictRaf = null
        }
        if (!isFinishing) Toast.makeText(
            this, getString(R.string.invalidDictionary, "", e.message),
            Toast.LENGTH_LONG
        ).show()
        startActivity(DictionaryManagerActivity.getLaunchIntent(applicationContext))
        finish()
    }

    private fun saveSearchHistory() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val ed = prefs.edit()
        for (i in searchHistory.indices) {
            ed.putString("history$i", searchHistory[i])
        }
        for (i in searchHistory.size..MAX_SEARCH_HISTORY) {
            ed.remove("history$i")
        }
        ed.apply()
    }

    private fun addToSearchHistory(text: String? = searchView!!.query.toString()) {
        if (text.isNullOrEmpty() || searchHistoryLimit == 0) return
        val exists = searchHistory.indexOf(text)
        if (exists >= 0) searchHistory.removeAt(exists)
        else if (searchHistory.size >= searchHistoryLimit) searchHistory.removeAt(searchHistory.size - 1)
        searchHistory.add(0, text)
        searchHistoryCursor = MatrixCursor(arrayOf("_id", "search"))
        for (i in searchHistory.indices) {
            val row = arrayOf<Any?>(i, searchHistory[i])
            searchHistoryCursor.addRow(row)
        }
        if (searchView!!.suggestionsAdapter.cursor != null) {
            searchView!!.suggestionsAdapter.swapCursor(searchHistoryCursor)
            searchView!!.suggestionsAdapter.notifyDataSetChanged()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        // when called via special search intents avoid focusing the search field
        // and thus popping up the keyboard
        var focusSearchView = true
        applyTheme(this)
        application = DictionaryApplication.INSTANCE

        Log.d(LOG, "onCreate:$this")
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Don't auto-launch if this fails.
        prefs.edit().remove(C.DICT_FILE).remove(C.INDEX_SHORT_NAME).commit()

        setContentView(R.layout.dictionary_activity)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(
            toolbar
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            listView
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }

        for (id in listOf(R.id.floatSearchButton, R.id.floatSwapButton)) {
            ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(id)
            ) { v, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                val mlp = v.layoutParams as MarginLayoutParams
                mlp.leftMargin = insets.left
                mlp.bottomMargin = insets.bottom
                mlp.rightMargin = insets.right
                v.layoutParams = mlp
                windowInsets
            }
        }

        textColorFg = resolveColorAttribute(com.google.android.material.R.attr.colorOnSurface)

        if (dictRaf != null) {
            try {
                dictRaf!!.close()
            } catch (e: IOException) {
                Log.e(LOG, "Failed to close dictionary", e)
            }
            dictRaf = null
        }

        val intent = getIntent()
        val intentAction = intent.action
        /*
          @author Dominik Köppl Querying the Intent
         *         com.hughes.action.ACTION_SEARCH_DICT is the advanced query
         *         Arguments: SearchManager.QUERY -> the phrase to search from
         *         -> language in which the phrase is written to -> to which
         *         language shall be translated
         */
        if ("com.hughes.action.ACTION_SEARCH_DICT" == intentAction) {
            focusSearchView = false
            val query = intent.getStringExtra(SearchManager.QUERY)
            var from = intent.getStringExtra("from")
            if (from != null) from = from.lowercase()
            var to = intent.getStringExtra("to")
            if (to != null) to = to.lowercase()
            if (query != null) {
                getIntent().putExtra(C.SEARCH_TOKEN, query)
            }
            if (intent.getStringExtra(C.DICT_FILE) == null && (from != null || to != null)) {
                Log.d(LOG, "DictSearch: from: $from to $to")
                val dicts: MutableList<DictionaryInfo> = application!!.getDictionariesOnDevice(null)
                for (info in dicts) {
                    var hasFrom = from == null
                    var hasTo = to == null
                    for (index in info.indexInfos) {
                        if (!hasFrom && index.shortName.lowercase() == from) hasFrom = true
                        if (!hasTo && index.shortName.lowercase() == to) hasTo = true
                    }
                    if (hasFrom && hasTo) {
                        if (from != null) {
                            var which_index = 0
                            while (which_index < info.indexInfos.size) {
                                if (info.indexInfos[which_index].shortName.lowercase() == from) break
                                ++which_index
                            }
                            intent.putExtra(
                                C.INDEX_SHORT_NAME,
                                info.indexInfos[which_index].shortName
                            )
                        }
                        intent.putExtra(
                            C.DICT_FILE, application!!.getPath(info.uncompressedFilename)
                                .getUri().toString()
                        )
                        break
                    }
                }
            }
        }
        /*
          @author Dominik Köppl Querying the Intent Intent.ACTION_SEARCH is a
         *         simple query Arguments follow from android standard (see
         *         documentation)
         */
        if (intentAction != null && intentAction == Intent.ACTION_SEARCH) {
            focusSearchView = false
            val query = intent.getStringExtra(SearchManager.QUERY)
            if (query != null) getIntent().putExtra(C.SEARCH_TOKEN, query)
        }
        if (intentAction != null && intentAction == Intent.ACTION_SEND) {
            focusSearchView = false
            val query = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (query != null) getIntent().putExtra(C.SEARCH_TOKEN, query)
        }
        /*
         * This processes text on M+ devices where QuickDic shows up in the context menu.
         */
        if (intentAction != null && intentAction == Intent.ACTION_PROCESS_TEXT) {
            focusSearchView = false
            val query = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            if (query != null) {
                getIntent().putExtra(C.SEARCH_TOKEN, query)
            }
        }
        // Support opening dictionary file directly
        if (intentAction != null && intentAction == Intent.ACTION_VIEW) {
            val uri = intent.data
            intent.putExtra(C.DICT_FILE, uri.toString())
            dictFileTitleName = uri!!.lastPathSegment
            try {
                dictRaf =
                    contentResolver.openAssetFileDescriptor(uri, "r")!!.createInputStream()
                        .channel
            } catch (e: Exception) {
                dictionaryOpenFail(e)
                return
            }
        }
        /*
          @author Dominik Köppl If no dictionary is chosen, use the default
         *         dictionary specified in the preferences If this step does
         *         fail (no default dictionary specified), show a toast and
         *         abort.
         */
        if (intent.getStringExtra(C.DICT_FILE) == null) {
            val dictfile = prefs.getString(getString(R.string.defaultDicKey), null)
            if (dictfile != null) intent.putExtra(
                C.DICT_FILE,
                application!!.getPath(dictfile).getUri().toString()
            )
        }
        var dictFilename = intent.getStringExtra(C.DICT_FILE)
        val search = intent.getStringExtra(C.SEARCH_TOKEN)
        if (intent.getStringExtra(C.INDEX_SHORT_NAME) == null && search != null) {
            val dics: MutableList<DictionaryInfo> = application!!.getDictionariesOnDevice(null)
            var bestFname: String? = null
            var bestIndex: String? = null
            var bestMatchLen = 2 // ignore shorter matches
            for (i in dics.indices) {
                try {
                    val dictfile: DocumentFile =
                        application!!.getPath(dics[i].uncompressedFilename)
                    val uriString = dictfile.uri.toString()

                    // If a dictionary is already specified (e.g. default), only search that one.
                    if (dictFilename != null && dictFilename != uriString) {
                        continue
                    }

                    Log.d(LOG, "Checking dictionary " + dics[i].uncompressedFilename)
                    val c = contentResolver.openAssetFileDescriptor(dictfile.uri, "r")!!
                        .createInputStream().channel
                    val dic = Dictionary(c)
                    for (j in dic.indices.indices) {
                        val idx = dic.indices[j]
                        Log.d(LOG, "Checking index " + idx.shortName)
                        if (idx.findExact(search) != null) {
                            Log.d(LOG, "Found exact match")
                            dictFilename = uriString
                            intent.putExtra(C.INDEX_SHORT_NAME, idx.shortName)
                            break
                        }
                        val matchLen = getMatchLen(search, idx.findInsertionPoint(search))
                        Log.d(LOG, "Found partial match length $matchLen")
                        if (matchLen > bestMatchLen) {
                            bestFname = uriString
                            bestIndex = idx.shortName
                            bestMatchLen = matchLen
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (dictFilename == null && bestFname != null) {
                dictFilename = bestFname
                intent.putExtra(C.INDEX_SHORT_NAME, bestIndex)
            }
        }

        if (dictFilename == null) {
            if (!isFinishing) Toast.makeText(
                this,
                getString(R.string.no_dict_file),
                Toast.LENGTH_LONG
            ).show()
            startActivity(DictionaryManagerActivity.getLaunchIntent(applicationContext))
            finish()
            return
        }
        if (dictRaf == null) {
            val u = Uri.parse(dictFilename)
            dictFile = if ("content" == u.scheme) DocumentFile.fromSingleUri(
                applicationContext,
                u
            ) else DocumentFile.fromFile(
                File(u.path!!)
            )
        }

        ttsReady = false
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                updateTTSLanguage(indexIndex)
            } else {
                Log.e(LOG, "TTS initialization failed: status=$status")
            }
        }

        try {
            if (dictRaf == null) {
                dictFileTitleName = application!!.getDictionaryName(dictFile!!.name!!)
                dictRaf = contentResolver.openAssetFileDescriptor(dictFile!!.uri, "r")!!
                    .createInputStream().channel
            }
            title = "QuickDic: $dictFileTitleName"
            dictionary = Dictionary(dictRaf)
        } catch (e: Exception) {
            dictionaryOpenFail(e)
            return
        }
        var targetIndex = intent.getStringExtra(C.INDEX_SHORT_NAME)
        if (savedInstanceState != null && savedInstanceState.getString(C.INDEX_SHORT_NAME) != null) {
            targetIndex = savedInstanceState.getString(C.INDEX_SHORT_NAME)
        }
        indexIndex = 0
        for (i in dictionary!!.indices.indices) {
            if (dictionary!!.indices[i].shortName == targetIndex) {
                indexIndex = i
                break
            }
        }
        Log.d(LOG, "Loading index $indexIndex")
        index = dictionary!!.indices[indexIndex]
        listView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        listView.emptyView = findViewById(android.R.id.empty)
        listView.onItemClickListener =
            OnItemClickListener { _, view, row, id ->
                onListItemClick(
                    listView, view, row, id
                )
            }

        listView.adapter = IndexAdapter(index!!)

        // Pre-load the Transliterator (will spawn its own thread)
        TransliteratorManager.init({
            uiHandler.post {
                onSearchTextChange(
                    searchView!!.query.toString()
                )
            }
        }, DictionaryApplication.threadBackground)

        // Pre-load the collators.
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LESS_FAVORABLE)
            val startMillis = System.currentTimeMillis()
            try {
                for (index in dictionary!!.indices) {
                    val searchToken = index.sortedIndexEntries[0].token
                    val entry = index.findExact(searchToken)
                    if (entry == null || searchToken != entry.token) {
                        Log.e(
                            LOG,
                            "Couldn't find token: " + searchToken + ", " + (if (entry == null) "null" else entry.token)
                        )
                    }
                }
                indexPrepFinished = true
            } catch (_: Exception) {
                Log.w(
                    LOG,
                    "Exception while prepping.  This can happen if dictionary is closed while search is happening."
                )
            }
            Log.d(LOG, "Prepping indices took:" + (System.currentTimeMillis() - startMillis))
        }.start()

        var fontName: String = prefs.getString(getString(R.string.fontKey), "FreeSerif.otf.jpg")!!
        when (fontName) {
            "SYSTEM" -> typeface = Typeface.DEFAULT
            "SERIF" -> typeface = Typeface.SERIF
            "SANS_SERIF" -> typeface = Typeface.SANS_SERIF
            "MONOSPACE" -> typeface = Typeface.MONOSPACE
            else -> {
                if ("FreeSerif.ttf.jpg" == fontName) {
                    fontName = "FreeSerif.otf.jpg"
                }
                try {
                    typeface = Typeface.createFromAsset(assets, fontName)
                } catch (e: Exception) {
                    Log.w(LOG, "Exception trying to use typeface, using default.", e)
                    if (!isFinishing) Toast.makeText(
                        this, getString(R.string.fontFailure, e.localizedMessage),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        if (typeface == null) {
            Log.w(LOG, "Unable to create typeface, using default.")
            typeface = Typeface.DEFAULT
        }
        val fontSize: String = prefs.getString(getString(R.string.fontSizeKey), "14")!!
        fontSizeSp = try {
            fontSize.trim { it <= ' ' }.toInt()
        } catch (_: NumberFormatException) {
            14
        }

        val searchHistoryLimitStr: String =
            prefs.getString(getString(R.string.historySizeKey), "" + DEFAULT_SEARCH_HISTORY)!!
        searchHistoryLimit = try {
            min(searchHistoryLimitStr.trim { it <= ' ' }.toInt(), MAX_SEARCH_HISTORY)
        } catch (_: NumberFormatException) {
            DEFAULT_SEARCH_HISTORY
        }

        // ContextMenu.
        registerForContextMenu(listView)

        // Cache some prefs.
        wordList = application!!.wordListFile
        saveOnlyFirstSubentry = prefs.getBoolean(
            getString(R.string.saveOnlyFirstSubentryKey),
            false
        )
        clickOpensContextMenu = prefs.getBoolean(
            getString(R.string.clickOpensContextMenuKey),
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        )
        Log.d(LOG, "wordList=$wordList, saveOnlyFirstSubentry=$saveOnlyFirstSubentry")
        val commandHandling: String =
            prefs.getString(getString(R.string.commandHandlingKey), "commandItalic")!!
        filterCommands = true
        deleteCommands = false
        if (commandHandling == "commandNone") filterCommands = false
        else if (commandHandling == "commandRemove") deleteCommands = true

        onCreateSetupActionBarAndSearchView()

        val floatSwapButton = findViewById<View>(R.id.floatSwapButton)
        floatSwapButton.setOnLongClickListener { v ->
            onLanguageButtonLongClick(v.context)
            true
        }

        // Set the search text from the intent, then the saved state.
        var text = getIntent().getStringExtra(C.SEARCH_TOKEN)
        if (savedInstanceState != null) {
            text = savedInstanceState.getString(C.SEARCH_TOKEN)
        }
        if (text == null) {
            text = ""
        }

        searchView!!.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val h = searchHistory[position]
                addToSearchHistory(h)
                setSearchText(h, true)
                return true
            }
        })
        searchView!!.setSuggestionsAdapter(object :
            CursorAdapter(this, if (text.isEmpty()) searchHistoryCursor else null, 0) {
            override fun newView(context: Context?, c: Cursor?, p: ViewGroup?): View {
                val v = TextView(context)
                v.setTextColor(textColorFg)
                v.setTypeface(typeface)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, (4 * fontSizeSp / 3).toFloat())
                return v
            }

            override fun bindView(v: View?, context: Context?, c: Cursor) {
                val t = v as TextView
                t.text = c.getString(1)
            }
        })

        // Set up search history
        var savedHistory: ArrayList<String?>? = null
        if (savedInstanceState != null) savedHistory =
            savedInstanceState.getStringArrayList(C.SEARCH_HISTORY)
        if (savedHistory.isNullOrEmpty()) {
            savedHistory = ArrayList()
            for (i in 0..<searchHistoryLimit) {
                val h = prefs.getString("history$i", null) ?: break
                savedHistory.add(h)
            }
        }
        for (i in savedHistory.indices.reversed()) {
            addToSearchHistory(savedHistory[i])
        }
        addToSearchHistory(text)

        setSearchText(text, true)
        Log.d(LOG, "Trying to restore searchText=$text")

        setDictionaryPrefs(this, dictFile, index!!.shortName)

        updateLangButton()
        if (focusSearchView) searchView!!.requestFocus()

        // http://stackoverflow.com/questions/2833057/background-listview-becomes-black-when-scrolling
//        getListView().setCacheColorHint(0);
    }

    private fun onCreateSetupActionBarAndSearchView() {
        val actionBar = supportActionBar
        actionBar!!.setDisplayShowTitleEnabled(false)
        actionBar.setDisplayShowHomeEnabled(false)
        actionBar.setDisplayHomeAsUpEnabled(false)

        val customSearchView = LinearLayout(supportActionBar!!.themedContext)

        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        customSearchView.layoutParams = layoutParams

        languageButton = ImageButton(
            customSearchView.context,
            null,
            0,
            R.style.Widget_Dictionary_LanguageButton
        )
        languageButton!!.id = R.id.languageButton
        languageButton!!.setOnClickListener { v ->
            onLanguageButtonLongClick(
                v.context
            )
        }

        languageTextButton =
            Button(customSearchView.context, null, 0, R.style.Widget_Dictionary_LanguageButton)
        languageTextButton!!.id = R.id.languageTextButton
        languageTextButton!!.setOnClickListener { v ->
            onLanguageButtonLongClick(
                v.context
            )
        }

        // Use a fixed aspect ratio (3:2) for the flag buttons and center them vertically
        // to prevent them from stretching to the full height of the Toolbar.
        val lpb: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            application!!.languageButtonPixels,
            application!!.languageButtonPixels * 2 / 3
        )
        lpb.gravity = Gravity.CENTER_VERTICAL
        customSearchView.addView(languageButton, lpb)
        customSearchView.addView(languageTextButton, lpb)

        searchView = SearchView(supportActionBar!!.themedContext)
        searchView!!.id = R.id.searchView

        // Get rid of search icon, it takes up too much space.
        // There is still text saying "search" in the search field.
        searchView!!.setIconifiedByDefault(true)
        searchView!!.isIconified = false

        searchView!!.setQueryHint(getString(R.string.searchText))
        searchView!!.setSubmitButtonEnabled(false)
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView!!.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or  // EditorInfo.IME_FLAG_NO_FULLSCREEN | // Requires API
                // 11
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        onQueryTextListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d(LOG, "OnQueryTextListener: onQueryTextSubmit: " + searchView!!.query)
                addToSearchHistory()
                hideKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(LOG, "OnQueryTextListener: onQueryTextChange: " + searchView!!.query)
                onSearchTextChange(searchView!!.query.toString())
                return true
            }
        }
        searchView!!.setOnQueryTextListener(onQueryTextListener)
        searchView!!.setFocusable(true)
        searchTextView = searchView!!.findViewById(R.id.search_src_text)
        val lp = LinearLayout.LayoutParams(
            0,
            FrameLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        customSearchView.addView(searchView, lp)

        actionBar.customView = customSearchView
        actionBar.setDisplayShowCustomEnabled(true)

        // Avoid wasting space on large left inset
        val tb = customSearchView.parent as Toolbar
        tb.setContentInsetsRelative(0, 0)

        listView.nextFocusLeftId = R.id.searchView
        findViewById<View>(R.id.floatSwapButton).nextFocusRightId = R.id.languageButton
        languageButton!!.nextFocusLeftId = R.id.floatSwapButton
        languageTextButton!!.nextFocusLeftId = R.id.floatSwapButton
    }

    override fun onResume() {
        Log.d(LOG, "onResume")
        super.onResume()
        if (PreferenceActivity.prefsMightHaveChanged) {
            PreferenceActivity.prefsMightHaveChanged = false
            finish()
            startActivity(intent)
        }
        showKeyboard()
        // prepare list of available dictionaries
        application!!.backgroundUpdateDictionaries(null)
    }

    /**
     * Invoked when MyWebView returns, since the user might have clicked some
     * hypertext in the MyWebView.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (result != null && result.hasExtra(C.SEARCH_TOKEN)) {
            Log.d(LOG, "onActivityResult: " + result.getStringExtra(C.SEARCH_TOKEN))
            jumpToTextFromHyperLink(result.getStringExtra(C.SEARCH_TOKEN)!!, indexIndex)
        }
    }

    override fun onPause() {
        super.onPause()
        addToSearchHistory()
        saveSearchHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dictRaf == null) {
            return
        }

        val searchOperation = currentSearchOperation
        currentSearchOperation = null

        // Before we close the RAF, we have to wind the current search down.
        if (searchOperation != null) {
            Log.d(LOG, "Interrupting search to shut down.")
            currentSearchOperation = null
            searchOperation.interrupted.set(true)
        }
        searchExecutor.shutdownNow()
        textToSpeech!!.shutdown()
        textToSpeech = null

        indexAdapter = null
        listView.adapter = null

        try {
            Log.d(LOG, "Closing RAF.")
            dictRaf!!.close()
        } catch (e: IOException) {
            Log.e(LOG, "Failed to close dictionary", e)
        }
        dictRaf = null
    }

    // --------------------------------------------------------------------------
    // Buttons
    // --------------------------------------------------------------------------
    private fun showKeyboard() {
        // For some reason, this doesn't always work the first time.
        // One way to replicate the problem:
        // Press the "task switch" button repeatedly to pause and resume
        var delay = 1
        while (delay <= 101) {
            searchView!!.postDelayed({
                Log.d(LOG, "Trying to show soft keyboard.")
                val searchTextHadFocus = searchView!!.hasFocus()
                searchView!!.requestFocusFromTouch()
                searchTextView!!.requestFocus()
                val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                manager.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
                manager.showSoftInput(searchTextView, InputMethodManager.SHOW_IMPLICIT)
                if (!searchTextHadFocus) {
                    defocusSearchText()
                }
            }, delay.toLong())
            delay += 100
        }
        searchView!!.post {
            searchTextView!!.threshold = 0
            try {
                searchTextView!!.showDropDown()
                // ignore any errors, in particular BadTokenException happens a lot
            } catch (_: Exception) {
            }
        }
    }

    private fun hideKeyboard() {
        Log.d(LOG, "Hide soft keyboard.")
        listView.requestFocusFromTouch() // Fixes search not working from history list
        searchView!!.clearFocus()
        val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(searchView!!.windowToken, 0)
    }

    private fun updateLangButton() {
        val indexInfo = IndexInfo(index!!.shortName, 0, index!!.mainTokenCount)
        val visibleButton =
            IsoUtils.INSTANCE.setupButton(languageTextButton, languageButton, indexInfo)
        findViewById<View>(R.id.floatSwapButton).nextFocusRightId = visibleButton.id
        updateTTSLanguage(indexIndex)
    }

    private fun speak(text: String?) {
        if (textToSpeech != null && ttsReady) {
            @Suppress("DEPRECATION")
            textToSpeech!!.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun updateTTSLanguage(i: Int) {
        if (!ttsReady || index == null || textToSpeech == null) {
            Log.d(LOG, "Can't updateTTSLanguage.")
            return
        }
        val isoCode = dictionary!!.indices[i].sortLanguage.isoCode
        val locale = Locale.forLanguageTag(isoCode)
        Log.d(LOG, "Setting TTS locale to: $locale (iso: $isoCode)")
        try {
            val ttsResult = textToSpeech!!.setLanguage(locale)
            if (ttsResult != TextToSpeech.LANG_AVAILABLE &&
                ttsResult != TextToSpeech.LANG_COUNTRY_AVAILABLE
            ) {
                Log.e(
                    LOG,
                    "TTS not available in this language: ttsResult=$ttsResult for locale $locale"
                )
            }
        } catch (e: Exception) {
            Log.e(LOG, "Exception setting TTS language", e)
            if (!isFinishing) Toast.makeText(
                this,
                getString(R.string.TTSbroken),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun onSearchButtonClick(dummy: View?) {
        if (!searchView!!.hasFocus()) {
            searchView!!.requestFocus()
        }
        if (!searchView!!.query.toString().isEmpty()) {
            addToSearchHistory()
            searchView!!.setQuery("", false)
        }
        showKeyboard()
        searchView!!.isIconified = false
    }

    fun onLanguageButtonClick(dummy: View?) {
        if (dictionary!!.indices.size == 1) {
            // No need to work to switch indices.
            return
        }
        if (currentSearchOperation != null) {
            currentSearchOperation!!.interrupted.set(true)
            currentSearchOperation = null
        }
        setIndexAndSearchText(
            (indexIndex + 1) % dictionary!!.indices.size,
            searchView!!.query.toString(), false
        )
    }

    private fun onLanguageButtonLongClick(context: Context) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.select_dictionary_dialog)
        dialog.setTitle(R.string.selectDictionary)

        val installedDicts: MutableList<DictionaryInfo> =
            application!!.getDictionariesOnDevice(null)

        val listView = dialog.findViewById<ListView>(android.R.id.list)
        val button = Button(listView.context)
        val name = getString(R.string.dictionaryManager)
        button.text = name
        val intentLauncher: IntentLauncher = object : IntentLauncher(
            listView.context,
            DictionaryManagerActivity.getLaunchIntent(applicationContext)
        ) {
            override fun onGo() {
                dialog.dismiss()
                this@DictionaryActivity.finish()
            }
        }
        button.setOnClickListener(intentLauncher)
        listView.addHeaderView(button)

        listView.itemsCanFocus = true
        listView.adapter = object : BaseAdapter() {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val dictionaryInfo = getItem(position)

                val result = LinearLayout(parent.context)
                // Ensure buttons are centered vertically and spaced consistently in the selection list.
                result.orientation = LinearLayout.HORIZONTAL
                result.gravity = Gravity.CENTER_VERTICAL
                result.setPadding(
                    application!!.languageButtonPixels / 8, application!!.languageButtonPixels / 16,
                    application!!.languageButtonPixels / 8, application!!.languageButtonPixels / 16
                )

                for (i in dictionaryInfo.indexInfos.indices) {
                    val indexInfo = dictionaryInfo.indexInfos[i]
                    val button = IsoUtils.INSTANCE.createButton(
                        parent.context,
                        indexInfo, application!!.languageButtonPixels
                    )
                    val intentLauncher: IntentLauncher = object : IntentLauncher(
                        parent.context,
                        getLaunchIntent(
                            applicationContext,
                            application!!.getPath(dictionaryInfo.uncompressedFilename).getUri()
                                .toString(),
                            indexInfo.shortName, searchView!!.query.toString()
                        )
                    ) {
                        override fun onGo() {
                            dialog.dismiss()
                            this@DictionaryActivity.finish()
                        }
                    }
                    button.setOnClickListener(intentLauncher)
                    if (i == indexIndex && dictFile != null &&
                        dictFile!!.name == dictionaryInfo.uncompressedFilename
                    ) {
                        button.isPressed = true
                    }
                    result.addView(button)
                }

                val nameView = TextView(parent.context)
                val name: String = application!!
                    .getDictionaryName(dictionaryInfo.uncompressedFilename)
                nameView.text = name
                val layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutParams.width = 0
                layoutParams.weight = 1.0f
                nameView.layoutParams = layoutParams
                nameView.gravity = Gravity.CENTER_VERTICAL
                result.addView(nameView)
                return result
            }

            override fun getItemId(position: Int): Long {
                return position.toLong()
            }

            override fun getItem(position: Int): DictionaryInfo {
                return installedDicts[position]
            }

            override fun getCount(): Int {
                return installedDicts.size
            }
        }
        dialog.show()
    }

    private fun onUpDownButton(up: Boolean) {
        if (isFiltered) {
            return
        }
        val firstVisibleRow = Math.clamp(
            listView.firstVisiblePosition.toLong(),
            0,
            index!!.sortedIndexEntries.size - 1
        )
        val row = index!!.rows[firstVisibleRow]
        val tokenRow = row.getTokenRow(true)
        val destIndexEntry: Int = if (up) {
            if (row !== tokenRow) {
                tokenRow.referenceIndex
            } else {
                max(tokenRow.referenceIndex - 1, 0)
            }
        } else {
            // Down
            min(tokenRow.referenceIndex + 1, index!!.sortedIndexEntries.size - 1)
        }
        val dest = index!!.sortedIndexEntries[destIndexEntry]
        Log.d(LOG, "onUpDownButton, destIndexEntry=" + dest.token)
        setSearchText(dest.token, false)
        listView.post { jumpToRow(index!!.sortedIndexEntries[destIndexEntry].startRow) }
        defocusSearchText()
    }

    private fun onRandomWordButton() {
        val destIndexEntry = rand.nextInt(index!!.sortedIndexEntries.size)
        val dest = index!!.sortedIndexEntries[destIndexEntry]
        setSearchText(dest.token, false)
        listView.post { jumpToRow(index!!.sortedIndexEntries[destIndexEntry].startRow) }
        defocusSearchText()
    }

    // --------------------------------------------------------------------------
    // Options Menu
    // --------------------------------------------------------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.showPrevNextButtonsKey), true)
        ) {
            // Next word.
            nextWordMenuItem = menu.add(getString(R.string.nextWord))
                .setIcon(R.drawable.arrow_drop_down_large_18px)
            nextWordMenuItem!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            nextWordMenuItem!!.setOnMenuItemClickListener { _ ->
                onUpDownButton(false)
                true
            }

            // Previous word.
            previousWordMenuItem = menu.add(getString(R.string.previousWord))
                .setIcon(R.drawable.arrow_drop_up_large_18px)
            previousWordMenuItem!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            previousWordMenuItem!!.setOnMenuItemClickListener { _ ->
                onUpDownButton(true)
                true
            }
        }

        val randomWordMenuItem = menu.add(getString(R.string.randomWord))
        randomWordMenuItem.setOnMenuItemClickListener { _ ->
            onRandomWordButton()
            true
        }

        run {
            val dictionaryManager = menu.add(getString(R.string.dictionaryManager))
            dictionaryManager.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            dictionaryManager.setOnMenuItemClickListener { _ ->
                startActivity(DictionaryManagerActivity.getLaunchIntent(applicationContext))
                finish()
                false
            }
        }

        run {
            val aboutDictionary = menu.add(getString(R.string.aboutDictionary))
            aboutDictionary.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            aboutDictionary.setOnMenuItemClickListener { _ ->
                val context = listView.context
                val dialog = Dialog(context)
                dialog.setContentView(R.layout.about_dictionary_dialog)
                val textView = dialog.findViewById<TextView>(R.id.text)

                dialog.setTitle(dictFileTitleName)

                val builder = StringBuilder()
                val dictionaryInfo = dictionary!!.getDictionaryInfo()
                if (dictionaryInfo != null) {
                    try {
                        dictionaryInfo.uncompressedBytes = dictRaf!!.size()
                    } catch (_: IOException) {
                    }
                    builder.append(dictionaryInfo.dictInfo).append("\n\n")
                    if (dictFile != null) {
                        builder.append(
                            getString(
                                R.string.dictionaryPath,
                                dictFile!!.uri.toString()
                            )
                        )
                            .append("\n")
                    }
                    builder.append(
                        getString(R.string.dictionarySize, dictionaryInfo.uncompressedBytes)
                    )
                        .append("\n")
                    builder.append(
                        getString(
                            R.string.dictionaryCreationTime,
                            dictionaryInfo.creationMillis
                        )
                    ).append("\n")
                    for (indexInfo in dictionaryInfo.indexInfos) {
                        builder.append("\n")
                        builder.append(getString(R.string.indexName, indexInfo.shortName))
                            .append("\n")
                        builder.append(
                            getString(R.string.mainTokenCount, indexInfo.mainTokenCount)
                        )
                            .append("\n")
                    }
                    builder.append("\n")
                    builder.append(getString(R.string.sources)).append("\n")
                    for (source in dictionary!!.sources) {
                        builder.append(
                            getString(
                                R.string.sourceInfo, source.getName(),
                                source.getNumEntries()
                            )
                        ).append("\n")
                    }
                }
                textView.text = builder.toString()

                dialog.show()
                val layoutParams = WindowManager.LayoutParams()
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                dialog.window!!.attributes = layoutParams
                false
            }
        }

        onCreateGlobalOptionsMenu(this, menu)

        return true
    }

    // --------------------------------------------------------------------------
    // Context Menu + clicks
    // --------------------------------------------------------------------------
    override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenuInfo?) {
        val adapterContextMenuInfo = menuInfo as AdapterContextMenuInfo
        val row = listView.adapter.getItem(adapterContextMenuInfo.position) as RowBase

        if (clickOpensContextMenu && (row is HtmlEntry.Row ||
                    (row is TokenRow && !row.indexEntry.htmlEntries.isEmpty()))
        ) {
            val html =
                if (row is TokenRow) row.indexEntry.htmlEntries else mutableListOf<HtmlEntry?>(
                    (row as HtmlEntry.Row).entry
                )
            val highlight = if (row is HtmlEntry.Row) row.getTokenRow(true).token else null
            val open = menu.add("Open")
            open.setOnMenuItemClickListener { _ ->
                showHtml(html, highlight)
                false
            }
        }

        val addToWordlist = menu.add(
            getString(
                R.string.addToWordList,
                wordList!!.name
            )
        )
        addToWordlist
            .setOnMenuItemClickListener { _ ->
                onAppendToWordList(row)
                false
            }

        val share = menu.add("Share")
        share.setOnMenuItemClickListener { _ ->
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(
                Intent.EXTRA_SUBJECT, row.getTokenRow(true)
                    .token
            )
            shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                row.getRawText(saveOnlyFirstSubentry)
            )
            startActivity(shareIntent)
            false
        }

        val copy = menu.add(android.R.string.copy)
        copy.setOnMenuItemClickListener { _ ->
            onCopy(row)
            false
        }

        if (selectedSpannableText != null) {
            val selectedText = selectedSpannableText
            val searchForSelection = menu.add(
                getString(
                    R.string.searchForSelection,
                    selectedSpannableText
                )
            )
            searchForSelection
                .setOnMenuItemClickListener { _ ->
                    jumpToTextFromHyperLink(selectedText!!, selectedSpannableIndex)
                    false
                }
            // Rats, this won't be shown:
            //searchForSelection.setIcon(R.drawable.abs__ic_search);
        }

        if ((row is TokenRow || selectedSpannableText != null) && ttsReady) {
            val speak = menu.add(R.string.speak)
            val textToSpeak = if (row is TokenRow) row.token else selectedSpannableText
            updateTTSLanguage(if (row is TokenRow) indexIndex else selectedSpannableIndex)
            speak.setOnMenuItemClickListener { _ ->
                speak(textToSpeak)
                false
            }
        }
        if (row is PairEntry.Row && ttsReady) {
            val pairs = row.entry.pairs
            val speakLeft = menu.add(R.string.speak_left)
            speakLeft.setOnMenuItemClickListener { _ ->
                val idx = if (index!!.swapPairEntries) 1 else 0
                updateTTSLanguage(idx)
                var text: String? = ""
                for (p in pairs) text += p.get(idx)
                text =
                    text!!.replace("\\{[^{}]*\\}".toRegex(), "").replace("{", "").replace("}", "")
                speak(text)
                false
            }
            val speakRight = menu.add(R.string.speak_right)
            speakRight.setOnMenuItemClickListener { _ ->
                val idx = if (index!!.swapPairEntries) 0 else 1
                updateTTSLanguage(idx)
                var text: String? = ""
                for (p in pairs) text += p.get(idx)
                text =
                    text!!.replace("\\{[^{}]*\\}".toRegex(), "").replace("{", "").replace("}", "")
                speak(text)
                false
            }
        }
    }

    private fun jumpToTextFromHyperLink(
        selectedText: String, defaultIndexToUse: Int
    ) {
        var indexToUse = -1
        var numFound = 0
        for (i in dictionary!!.indices.indices) {
            val index = dictionary!!.indices[i]
            if (indexPrepFinished) {
                println("Doing index lookup: on $selectedText")
                val indexEntry = index.findExact(selectedText)
                if (indexEntry != null) {
                    val tokenRow = index.rows[indexEntry.startRow]
                        .getTokenRow(false)
                    if (tokenRow != null && tokenRow.hasMainEntry) {
                        indexToUse = i
                        ++numFound
                    }
                }
            } else {
                Log.w(LOG, "Skipping findExact on index " + index.shortName)
            }
        }
        if (numFound != 1) {
            indexToUse = defaultIndexToUse
        }
        // Without this extra delay, the call to jumpToRow that this
        // invokes doesn't always actually have any effect.
        val actualIndexToUse = indexToUse
        listView.postDelayed({
            setIndexAndSearchText(actualIndexToUse, selectedText, true)
            addToSearchHistory(selectedText)
        }, 100)
    }

    /**
     * Called when user clicks outside of search text, so that they can start
     * typing again immediately.
     */
    private fun defocusSearchText() {
        // Log.d(LOG, "defocusSearchText");
        // Request focus so that if we start typing again, it clears the text
        // input.
        listView.requestFocus()

        // Visual indication that a new keystroke will clear the search text.
        // Doesn't seem to work unless searchText has focus.
        // searchView.selectAll();
    }

    private fun onListItemClick(l: ListView?, v: View, rowIdx: Int, id: Long) {
        defocusSearchText()
        if (clickOpensContextMenu && dictRaf != null) {
            openContextMenu(v)
        } else {
            val row = listView.adapter.getItem(rowIdx) as RowBase?
            if (row !is PairEntry.Row) {
                v.performClick()
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun onAppendToWordList(row: RowBase) {
        defocusSearchText()

        val rawText = StringBuilder()
        rawText.append(SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(Date())).append("\t")
        rawText.append(index!!.longName).append("\t")
        rawText.append(row.getTokenRow(true).token).append("\t")
        rawText.append(row.getRawText(saveOnlyFirstSubentry))
        Log.d(LOG, "Writing : $rawText")

        try {
            val out = PrintStream(contentResolver.openOutputStream(wordList!!.uri, "wa"))
            out.println(rawText)
            out.close()
        } catch (e: Exception) {
            Log.e(LOG, "Unable to append to " + wordList!!.uri.path, e)
            if (!isFinishing) Toast.makeText(
                this,
                getString(R.string.failedAddingToWordList, wordList!!.uri.path),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onCopy(row: RowBase) {
        defocusSearchText()

        Log.d(LOG, "Copy, row=$row")
        val result = StringBuilder()
        result.append(row.getRawText(false))
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Dictionary", result.toString()))
        Log.d(LOG, "Copied: $result")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.unicodeChar != 0) {
            if (!searchView!!.hasFocus()) {
                setSearchText("" + event.unicodeChar.toChar(), true)
                searchView!!.requestFocus()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            Log.d(LOG, "Trying to hide soft keyboard.")
            val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
            val focus = currentFocus
            if (inputManager != null && focus != null) {
                inputManager.hideSoftInputFromWindow(
                    focus.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setIndexAndSearchText(newIndex: Int, newSearchText: String, hideKeyboard: Boolean) {
        var newIndex = newIndex
        Log.d(LOG, "Changing index to: $newIndex")
        if (newIndex == -1) {
            Log.e(LOG, "Invalid index.")
            newIndex = 0
        }
        if (newIndex != indexIndex) {
            indexIndex = newIndex
            index = dictionary!!.indices[indexIndex]
            indexAdapter = IndexAdapter(index!!)
            listView.adapter = indexAdapter
            Log.d(LOG, "changingIndex, newLang=" + index!!.longName)
            setDictionaryPrefs(this, dictFile, index!!.shortName)
            updateLangButton()
        }
        setSearchText(newSearchText, true, hideKeyboard)
    }

    private fun setSearchText(text: String, triggerSearch: Boolean, hideKeyboard: Boolean) {
        Log.d(LOG, "setSearchText, text=$text, triggerSearch=$triggerSearch")
        // Disable the listener, because sometimes it doesn't work.
        searchView!!.setOnQueryTextListener(null)
        searchView!!.setQuery(text, false)
        searchView!!.setOnQueryTextListener(onQueryTextListener)

        if (triggerSearch) {
            onSearchTextChange(text)
        }

        // We don't want to show virtual keyboard when we're changing searchView text programatically:
        if (hideKeyboard) {
            hideKeyboard()
        }
    }

    private fun setSearchText(text: String, triggerSearch: Boolean) {
        setSearchText(text, triggerSearch, true)
    }

    // --------------------------------------------------------------------------
    // SearchOperation
    // --------------------------------------------------------------------------
    private fun searchFinished(searchOperation: SearchOperation) {
        if (searchOperation.interrupted.get()) {
            Log.d(LOG, "Search operation was interrupted: $searchOperation")
            return
        }
        if (searchOperation != currentSearchOperation) {
            Log.d(LOG, "Stale searchOperation finished: $searchOperation")
            return
        }

        val searchResult = searchOperation.searchResult
        Log.d(LOG, "searchFinished: $searchOperation, searchResult=$searchResult")

        currentSearchOperation = null
        // Note: it's important to post to the ListView, otherwise
        // the jumpToRow will randomly not work.
        // It also randomly fails when the list view does not have focus
        listView.post {
            if (currentSearchOperation == null) {
                if (searchResult != null) {
                    if (isFiltered) {
                        clearFiltered()
                    }
                    jumpToRow(searchResult.startRow)
                } else if (searchOperation.multiWordSearchResult != null) {
                    // Multi-row search....
                    setFiltered(searchOperation)
                } else {
                    throw IllegalStateException("This should never happen.")
                }
            } else {
                Log.d(LOG, "More coming, waiting for currentSearchOperation.")
            }
        }
    }

    private fun jumpToRow(row: Int) {
        Log.d(LOG, "jumpToRow: $row, refocusSearchText=false")
        listView.setSelectionFromTop(row, 0)
        listView.isSelected = true
    }

    internal inner class SearchOperation(searchText: String, val index: Index) : Runnable {
        val interrupted: AtomicBoolean = AtomicBoolean(false)

        val searchText: String = StringUtil.normalizeWhitespace(searchText)

        var searchTokens: List<String>? = null // filled in for multiWord.

        var searchStartMillis: Long = 0

        var searchResult: Index.IndexEntry? = null

        var multiWordSearchResult: MutableList<RowBase>? = null

        var done: Boolean = false

        override fun toString(): String {
            return String.format("SearchOperation(%s,%s)", searchText, interrupted)
        }

        override fun run() {
            try {
                searchStartMillis = System.currentTimeMillis()
                val searchTokenArray: Array<String> = WHITESPACE.split(searchText)
                if (searchTokenArray.size == 1) {
                    searchResult = index.findInsertionPoint(searchText, interrupted)
                } else {
                    searchTokens = listOf(*searchTokenArray)
                    multiWordSearchResult = index.multiWordSearch(
                        searchText, searchTokens,
                        interrupted
                    )
                }
                Log.d(
                    LOG,
                    ("searchText=" + searchText + ", searchDuration="
                            + (System.currentTimeMillis() - searchStartMillis)
                            + ", interrupted=" + interrupted.get())
                )
                if (!interrupted.get()) {
                    uiHandler.post { searchFinished(this@SearchOperation) }
                } else {
                    Log.d(LOG, "interrupted, skipping searchFinished.")
                }
            } catch (e: Exception) {
                Log.e(LOG, "Failure during search (can happen during Activity close): " + e.message)
            } finally {
                synchronized(this) {
                    done = true
                    (this as Object).notifyAll()
                }
            }
        }
    }

    // --------------------------------------------------------------------------
    // IndexAdapter
    // --------------------------------------------------------------------------
    private fun showHtml(htmlEntries: MutableList<HtmlEntry?>, htmlTextToHighlight: String?) {
        var html = HtmlEntry.htmlBody(htmlEntries, index!!.shortName)
        val title = HtmlEntry.firstTitle(htmlEntries)
        var style = ""
        if (typeface === Typeface.SERIF) {
            style = "font-family: serif;"
        } else if (typeface === Typeface.SANS_SERIF) {
            style = "font-family: sans-serif;"
        } else if (typeface === Typeface.MONOSPACE) {
            style = "font-family: monospace;"
        }
        if (application!!.selectedTheme == DictionaryApplication.Theme.DEFAULT) style += "body { background-color: black; color: white; } a { color: #00aaff; }"
        // Dictionaries currently all contain http:// links.
        // Regenerating all will not happen soon, so for now replace all occurrences instead.
        html = html.replace("http://", "https://")
        if (filterCommands) {
            html = html.replace(
                "\\{\\{([^{}]*)\\}\\}".toRegex(),
                if (deleteCommands) "" else "<i>$1</i>"
            )
            html = html.replace("\\{([^{}]*)\\}".toRegex(), if (deleteCommands) "" else "<i>$1</i>")
        }
        // Log.d(LOG, "html=" + html);
        startActivityForResult(
            getHtmlIntent(
                applicationContext, String.format(
                    "<html><head><meta name=\"viewport\" content=\"width=device-width\"><style type=\"text/css\">%s</style></head><body>%s</body></html>",
                    style,
                    html
                ),
                htmlTextToHighlight, title
            ),
            0
        )
    }

    internal inner class IndexAdapter : BaseAdapter {
        private val wiktionaryPattern: Pattern =
            Pattern.compile("(\\{\\{[^{}]*\\}\\}|\\{[^{}]*\\})")

        val index: Index

        val rows: MutableList<RowBase>

        val toHighlight: MutableSet<String>?

        private var mPaddingDefault = 0

        private var mPaddingLarge = 0

        constructor(index: Index) {
            this.index = index
            rows = index.rows
            toHighlight = null
            metrics
        }

        constructor(index: Index, rows: MutableList<RowBase>, toHighlight: List<String>?) {
            this.index = index
            this.rows = rows
            this.toHighlight = toHighlight?.let { LinkedHashSet(it) }
            metrics
        }

        private val metrics: Unit
            get() {
                var scale = 1f
                // Get the screen's density scale
                // The previous method getResources().getDisplayMetrics()
                // used to occasionally trigger a null pointer exception,
                // so try this instead.
                // As it still crashes, add a fallback
                try {
                    val dm = DisplayMetrics()
                    windowManager.defaultDisplay.getMetrics(dm)
                    scale = dm.density
                } catch (_: NullPointerException) {
                }
                // Convert the dps to pixels, based on density scale
                mPaddingDefault = (PADDING_DEFAULT_DP * scale + 0.5f).toInt()
                mPaddingLarge = (PADDING_LARGE_DP * scale + 0.5f).toInt()
            }

        override fun getCount(): Int {
            return rows.size
        }

        override fun getItem(position: Int): RowBase {
            return rows[position]
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).index().toLong()
        }

        override fun getViewTypeCount(): Int {
            return 5
        }

        override fun getItemViewType(position: Int): Int {
            when (val row = getItem(position)) {
                is PairEntry.Row -> {
                    val entry = row.entry
                    val rowCount = entry.pairs.size
                    return if (rowCount > 1) 1 else 0
                }

                is TokenRow -> {
                    val indexEntry = row.indexEntry
                    return if (indexEntry.htmlEntries.isEmpty()) 2 else 3
                }

                is HtmlEntry.Row -> {
                    return 4
                }

                else -> {
                    throw IllegalArgumentException("Unsupported Row type: " + row.javaClass)
                }
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return when (val row = getItem(position)) {
                is PairEntry.Row -> {
                    getView(position, row, parent, convertView as TableLayout?)
                }

                is TokenRow -> {
                    getView(row, parent, convertView as TextView?)
                }

                is HtmlEntry.Row -> {
                    getView(row, parent, convertView as TextView?)
                }

                else -> {
                    throw IllegalArgumentException("Unsupported Row type: " + row.javaClass)
                }
            }
        }

        private fun findWiktionaryCommands(
            colText: String,
            pos: ArrayList<IntArray>,
            remove: Boolean
        ): String {
            val m = wiktionaryPattern.matcher(colText)
            val res = StringBuilder()
            var last_pos = 0
            while (m.find()) {
                res.append(colText, last_pos, m.start())
                last_pos = m.end()
                if (remove) continue
                val start = res.length
                val inner = m.group().replace("{", "").replace("}", "")
                res.append(inner)
                pos.add(intArrayOf(start, res.length))
            }
            res.append(colText.substring(last_pos))
            return res.toString()
        }

        private fun addBoldSpans(token: String, col1Text: String, col1Spannable: Spannable) {
            var startPos = 0
            while ((col1Text.indexOf(token, startPos).also { startPos = it }) != -1) {
                col1Spannable.setSpan(
                    StyleSpan(Typeface.BOLD), startPos, startPos
                            + token.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                )
                startPos += token.length
            }
        }

        private fun getView(
            position: Int, row: PairEntry.Row, parent: ViewGroup,
            result: TableLayout?
        ): TableLayout {
            var result = result
            val context = parent.context
            val entry = row.entry
            val rowCount = entry.pairs.size
            if (result == null) {
                result = TableLayout(context)
                result.isStretchAllColumns = true
                // Because we have a Button inside a ListView row:
                // http://groups.google.com/group/android-developers/browse_thread/thread/3d96af1530a7d62a?pli=1
                result.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                result.isClickable = true
                result.setFocusable(false)
                result.isLongClickable = true

                result.setBackgroundColor(resolveColorAttribute(com.google.android.material.R.attr.colorSurface))
            } else if (result.childCount > rowCount) {
                result.removeViews(rowCount, result.childCount - rowCount)
            }

            for (r in result.childCount..<rowCount) {
                val layoutParams = TableRow.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams.leftMargin = mPaddingLarge

                val tableRow = TableRow(result.context)

                val col1 = TextView(tableRow.context)
                val col2 = TextView(tableRow.context)
                col1.setTextIsSelectable(true)
                col2.setTextIsSelectable(true)
                col1.setTextColor(textColorFg)
                col2.setTextColor(textColorFg)

                col1.width = 1
                col2.width = 1

                col1.setTypeface(typeface)
                col2.setTypeface(typeface)
                col1.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
                col2.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())

                // col2.setBackgroundResource(theme.otherLangBg);
                if (index.swapPairEntries) {
                    col2.setOnLongClickListener(textViewLongClickListenerIndex0)
                    col1.setOnLongClickListener(textViewLongClickListenerIndex1)
                } else {
                    col1.setOnLongClickListener(textViewLongClickListenerIndex0)
                    col2.setOnLongClickListener(textViewLongClickListenerIndex1)
                }

                // Set the columns in the table.
                if (r == 0) {
                    tableRow.addView(col1, layoutParams)
                    tableRow.addView(col2, layoutParams)
                } else {
                    for (i in 0..1) {
                        val bullet = TextView(tableRow.context)
                        bullet.text = " • "
                        val wrapped = LinearLayout(context)
                        wrapped.orientation = LinearLayout.HORIZONTAL
                        val p1 = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            0f
                        )
                        wrapped.addView(bullet, p1)
                        val p2 = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        wrapped.addView(if (i == 0) col1 else col2, p2)
                        tableRow.addView(wrapped, layoutParams)
                    }
                }

                result.addView(tableRow)
            }

            for (r in 0..<rowCount) {
                val tableRow = result.getChildAt(r) as TableRow
                var left = tableRow.getChildAt(0)
                var right = tableRow.getChildAt(1)
                if (r > 0) {
                    left = (left as ViewGroup).getChildAt(1)
                    right = (right as ViewGroup).getChildAt(1)
                }
                val col1 = left as TextView
                val col2 = right as TextView

                // Set what's in the columns.
                val pair = entry.pairs[r]
                var col1Text = if (index.swapPairEntries) pair.lang2 else pair.lang1
                var col2Text = if (index.swapPairEntries) pair.lang1 else pair.lang2
                val col1Cursive = ArrayList<IntArray>()
                val col2Cursive = ArrayList<IntArray>()
                if (filterCommands) {
                    col1Text = findWiktionaryCommands(col1Text, col1Cursive, deleteCommands)
                    col2Text = findWiktionaryCommands(col2Text, col2Cursive, deleteCommands)
                }
                val col1Spannable: Spannable = SpannableString(col1Text)
                val col2Spannable: Spannable = SpannableString(col2Text)

                for (pos in col1Cursive) {
                    col1Spannable.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        pos[0],
                        pos[1],
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
                for (pos in col2Cursive) {
                    col2Spannable.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        pos[0],
                        pos[1],
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
                // Bold the token instances in col1.
                if (toHighlight != null) {
                    for (token in toHighlight) {
                        addBoldSpans(token, col1Text, col1Spannable)
                    }
                } else addBoldSpans(row.getTokenRow(true).token, col1Text, col1Spannable)

                createTokenLinkSpans(col1, col1Spannable, col1Text)
                createTokenLinkSpans(col2, col2Spannable, col2Text)

                col1.text = col1Spannable
                col2.text = col2Spannable
            }

            result.setOnClickListener { v ->
                this@DictionaryActivity.onListItemClick(
                    this@DictionaryActivity.listView, v, position, position.toLong()
                )
            }

            return result
        }

        private fun getPossibleLinkToHtmlEntryView(
            isTokenRow: Boolean,
            text: String, hasMainEntry: Boolean, htmlEntries: MutableList<HtmlEntry?>,
            htmlTextToHighlight: String?, parent: ViewGroup, textView: TextView?
        ): TextView {
            var textView = textView
            val context = parent.context
            if (textView == null) {
                textView = TextView(context)
                // set up things invariant across one ItemViewType
                // ItemViewTypes handled here are:
                // 2: isTokenRow == true, htmlEntries.isEmpty() == true
                // 3: isTokenRow == true, htmlEntries.isEmpty() == false
                // 4: isTokenRow == false, htmlEntries.isEmpty() == false
                textView.setPadding(
                    if (isTokenRow) mPaddingDefault else mPaddingLarge,
                    mPaddingDefault,
                    mPaddingDefault,
                    0
                )
                textView.setOnLongClickListener(if (indexIndex > 0) textViewLongClickListenerIndex1 else textViewLongClickListenerIndex0)
                textView.isLongClickable = true

                textView.setTypeface(typeface)
                if (isTokenRow) {
                    TextViewCompat.setTextAppearance(
                        textView,
                        resolveColorAttribute(com.google.android.material.R.attr.textAppearanceTitleLarge)
                    )
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (4 * fontSizeSp / 3).toFloat())
                } else {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
                }
                textView.setTextColor(textColorFg)
                if (!htmlEntries.isEmpty()) {
                    textView.isClickable = true
                    textView.movementMethod = LinkMovementMethod.getInstance()
                }
            }

            textView.setBackgroundColor(
                if (hasMainEntry) resolveColorAttribute(com.google.android.material.R.attr.colorSurfaceContainer) else resolveColorAttribute(
                    com.google.android.material.R.attr.colorSurface
                )
            )

            // Make it so we can long-click on these token rows, too:
            val textSpannable: Spannable = SpannableString(text)
            createTokenLinkSpans(textView, textSpannable, text)

            if (!htmlEntries.isEmpty()) {
                val clickableSpan: ClickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                    }
                }
                textSpannable.setSpan(
                    clickableSpan, 0, text.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                textView.setOnClickListener { _ ->
                    showHtml(
                        htmlEntries,
                        htmlTextToHighlight
                    )
                }
            }
            textView.text = textSpannable
            return textView
        }

        private fun getView(row: TokenRow, parent: ViewGroup, result: TextView?): TextView {
            val indexEntry = row.indexEntry
            return getPossibleLinkToHtmlEntryView(
                true, indexEntry.token, row.hasMainEntry,
                indexEntry.htmlEntries, null, parent, result
            )
        }

        private fun getView(row: HtmlEntry.Row, parent: ViewGroup, result: TextView?): TextView {
            val htmlEntry = row.entry
            val tokenRow = row.getTokenRow(true)
            return getPossibleLinkToHtmlEntryView(
                false,
                getString(R.string.seeAlso, htmlEntry.title, htmlEntry.entrySource.getName()),
                false, mutableListOf(htmlEntry), tokenRow.token, parent,
                result
            )
        }


    }

    private fun createTokenLinkSpans(
        textView: TextView, spannable: Spannable,
        text: String
    ) {
        // Saw from the source code that LinkMovementMethod sets the selection!
        // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.1_r1/android/text/method/LinkMovementMethod.java#LinkMovementMethod
        textView.movementMethod = LinkMovementMethod.getInstance()
        val matcher: Matcher = CHAR_DASH.matcher(text)
        while (matcher.find()) {
            spannable.setSpan(
                NonLinkClickableSpan(), matcher.start(),
                matcher.end(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }
    }

    private var selectedSpannableText: String? = null

    private var selectedSpannableIndex = -1

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        selectedSpannableText = null
        selectedSpannableIndex = -1
        return super.onTouchEvent(event)
    }

    private inner class TextViewLongClickListener(val index: Int) : OnLongClickListener {
        override fun onLongClick(v: View?): Boolean {
            val textView = v as TextView
            val start = textView.selectionStart
            val end = textView.selectionEnd
            if (start >= 0 && end >= 0) {
                selectedSpannableText = textView.text.subSequence(start, end).toString()
                selectedSpannableIndex = index
            }
            return false
        }
    }

    private val textViewLongClickListenerIndex0 = TextViewLongClickListener(
        0
    )

    private val textViewLongClickListenerIndex1 = TextViewLongClickListener(
        1
    )

    // --------------------------------------------------------------------------
    // SearchText
    // --------------------------------------------------------------------------
    private fun onSearchTextChange(text: String) {
        if ("thadolina" == text) {
            val dialog = Dialog(listView.context)
            dialog.setContentView(R.layout.thadolina_dialog)
            dialog.setTitle("Ti amo, amore mio!")
            val imageView = dialog.findViewById<ImageView>(R.id.thadolina_image)
            imageView.setOnClickListener { _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://sites.google.com/site/cfoxroxvday/vday2012")
                startActivity(intent)
            }
            dialog.show()
        }
        if (dictRaf == null) {
            Log.d(LOG, "searchText changed during shutdown, doing nothing.")
            return
        }

        Log.d(LOG, "onSearchTextChange: $text")
        if (currentSearchOperation != null) {
            Log.d(LOG, "Interrupting currentSearchOperation.")
            currentSearchOperation!!.interrupted.set(true)
        }
        currentSearchOperation = SearchOperation(text, index!!)
        searchExecutor.execute(currentSearchOperation)
        (findViewById<View>(R.id.floatSearchButton) as FloatingActionButton).setImageResource(if (!text.isEmpty()) R.drawable.ic_clear_black_24dp else R.drawable.ic_search_black_24dp)
        searchView!!.suggestionsAdapter
            .swapCursor(if (text.isEmpty()) searchHistoryCursor else null)
        searchView!!.suggestionsAdapter.notifyDataSetChanged()
    }

    private val isFiltered: Boolean
        // --------------------------------------------------------------------------
        get() = rowsToShow != null

    private fun setFiltered(searchOperation: SearchOperation) {
        if (nextWordMenuItem != null) {
            nextWordMenuItem!!.isEnabled = false
            previousWordMenuItem!!.isEnabled = false
        }
        rowsToShow = searchOperation.multiWordSearchResult
        listView.adapter = IndexAdapter(index!!, rowsToShow!!, searchOperation.searchTokens)
    }

    private fun clearFiltered() {
        if (nextWordMenuItem != null) {
            nextWordMenuItem!!.isEnabled = true
            previousWordMenuItem!!.isEnabled = true
        }
        listView.adapter = IndexAdapter(index!!)
        rowsToShow = null
    }

    companion object {
        private const val PADDING_DEFAULT_DP = 8f

        private const val PADDING_LARGE_DP = 16f

        private const val LOG = "QuickDic"

        @JvmStatic
        fun getLaunchIntent(
            c: Context?, dictFile: String?, indexShortName: String?,
            searchToken: String?
        ): Intent {
            val intent = Intent(c, DictionaryActivity::class.java)
            intent.putExtra(C.DICT_FILE, dictFile)
            intent.putExtra(C.INDEX_SHORT_NAME, indexShortName)
            intent.putExtra(C.SEARCH_TOKEN, searchToken)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }

        private fun setDictionaryPrefs(
            context: Context, dictFile: DocumentFile?,
            indexShortName: String?
        ) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                context
            ).edit()
            if (dictFile != null) {
                prefs.putString(C.DICT_FILE, dictFile.uri.toString())
                prefs.putString(C.INDEX_SHORT_NAME, indexShortName)
            }
            prefs.remove(C.SEARCH_TOKEN) // Don't need to save search token.
            prefs.commit()
        }

        private val WHITESPACE: Pattern = Pattern.compile("\\s+")

        private val CHAR_DASH: Pattern = Pattern.compile("['\\p{L}\\p{M}\\p{N}]+")
    }
}
