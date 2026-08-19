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

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.hughes.android.dictionary.DictionaryActivity.Companion.getLaunchIntent
import com.hughes.android.dictionary.DictionaryApplication.Companion.applyTheme
import com.hughes.android.dictionary.DictionaryApplication.Companion.checkFileCreate
import com.hughes.android.dictionary.DictionaryApplication.Companion.onCreateGlobalOptionsMenu
import com.hughes.android.dictionary.engine.DictionaryInfo
import com.hughes.android.dictionary.engine.DictionaryInfo.IndexInfo
import com.hughes.android.util.IntentLauncher
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// Right-click:
//  Delete, move to top.
class DictionaryManagerActivity : AppCompatActivity() {
    private val listView: ListView by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(android.R.id.list)
    }

    // For DownloadManager bug workaround
    private val finishedDownloadIds: MutableSet<Long?> = HashSet()

    private var application: DictionaryApplication? = null

    private var filterSearchView: SearchView? = null
    private var showDownloadable: ToggleButton? = null

    private var dictionariesOnDeviceHeaderRow: LinearLayout? = null
    private var downloadableDictionariesHeaderRow: LinearLayout? = null

    private var uiHandler: Handler? = null

    private val dictionaryUpdater: Runnable = object : Runnable {
        override fun run() {
            if (uiHandler == null || isFinishing || isDestroyed) {
                return
            }
            uiHandler!!.post { this@DictionaryManagerActivity.setMyListAdapter() }
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @Synchronized
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (DownloadManager.ACTION_NOTIFICATION_CLICKED == action) {
                startActivity(getLaunchIntent(applicationContext).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                val downloadId = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, 0
                )
                if (finishedDownloadIds.contains(downloadId)) return  // ignore double notifications

                val query = DownloadManager.Query()
                query.setFilterById(downloadId)
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val dest: String?
                val status: Int
                val reason: Int
                downloadManager.query(query).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) {
                        Log.e(LOG, "Couldn't find download.")
                        return
                    }
                    dest = cursor
                        .getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    status = cursor
                        .getInt(
                            cursor
                                .getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                    reason =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
                if (DownloadManager.STATUS_SUCCESSFUL != status) {
                    Log.w(
                        LOG,
                        "Download failed: status=" + status +
                                ", reason=" + reason
                    )
                    var msg = reason.toString()
                    when (reason) {
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> msg = "File exists"
                        DownloadManager.ERROR_FILE_ERROR -> msg = "File error"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> msg = "Not enough space"
                    }
                    AlertDialog.Builder(context).setTitle(getString(R.string.error))
                        .setMessage(getString(R.string.downloadFailed, msg))
                        .setNeutralButton("Close", null).show()
                    return
                }

                Log.w(LOG, "Download finished: $dest Id: $downloadId")
                if (!isFinishing) Toast.makeText(
                    context, getString(R.string.unzippingDictionary, dest),
                    Toast.LENGTH_LONG
                ).show()

                finishedDownloadIds.add(downloadId)
                Thread {
                    if (unzipInstall(context, Uri.parse(dest), dest, true)) {
                        Log.w(LOG, "Unzipping finished: $dest Id: $downloadId")
                    }
                }.start()
            }
        }
    }

    private fun unzipInstall(
        context: Context,
        zipUri: Uri,
        dest: String?,
        delete: Boolean
    ): Boolean {
        var localZipFile: File? = null
        var zipFileStream: InputStream? = null
        var zipFile: ZipInputStream? = null
        var result = false
        try {
            if ("content" == zipUri.scheme) {
                zipFileStream = context.contentResolver.openInputStream(zipUri)
            } else {
                localZipFile = File(zipUri.path!!)
                try {
                    zipFileStream = FileInputStream(localZipFile)
                } catch (e: Exception) {
                    if (ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (uiHandler != null && !isFinishing && !isDestroyed) uiHandler!!.post {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ), 0
                            )
                        }
                        return false
                    }
                    throw e
                }
            }
            zipFile = ZipInputStream(BufferedInputStream(zipFileStream))
            var zipEntry: ZipEntry?
            while ((zipFile.nextEntry.also { zipEntry = it }) != null) {
                // Note: this check prevents security issues like accidental path
                // traversal, which unfortunately ZipInputStream has no protection against.
                // So take extra care when changing it.
                if (!Pattern.matches("[a-zA-Z0-9._-]+\\.quickdic", zipEntry!!.name)) {
                    Log.w(LOG, "Invalid zip entry: " + zipEntry.name)
                    continue
                }
                Log.d(LOG, "Unzipping entry: " + zipEntry.name)
                var targetFile: DocumentFile? = application!!.dictDir.findFile(zipEntry.name)
                if (targetFile != null && targetFile.exists()) {
                    targetFile.renameTo(zipEntry.name.replace(".quickdic", ".bak.quickdic"))
                }
                targetFile = application!!.dictDir.createFile("", zipEntry.name)
                context.contentResolver.openAssetFileDescriptor(targetFile!!.uri, "wt")!!
                    .createOutputStream().use { zipOut ->
                        copyStream(zipFile, zipOut)
                    }
            }
            if (uiHandler != null && !isFinishing && !isDestroyed) {
                uiHandler!!.post {
                    application!!.backgroundUpdateDictionaries(
                        dictionaryUpdater
                    )
                }
                uiHandler!!.post {
                    Toast.makeText(
                        context, getString(R.string.installationFinished, dest),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            result = true
        } catch (e: Exception) {
            val dir: DocumentFile = application!!.dictDir
            val msg: String = if (!dir.canWrite() || !checkFileCreate(dir)) {
                getString(R.string.notWritable, dir.uri.path)
            } else {
                getString(R.string.unzippingFailed, dest + ": " + e.message)
            }
            if (uiHandler != null && !isFinishing && !isDestroyed) {
                uiHandler!!.post {
                    AlertDialog.Builder(context).setTitle(getString(R.string.error)).setMessage(msg)
                        .setNeutralButton("Close", null).show()
                }
            }
            Log.e(LOG, "Failed to unzip.", e)
        } finally {
            try {
                zipFile?.close()
            } catch (_: IOException) {
            }
            try {
                zipFileStream?.close()
            } catch (_: IOException) {
            }
            if (delete) {
                if (localZipFile != null) localZipFile.delete()
                else context.contentResolver.delete(zipUri, null)
            }
        }
        return result
    }

    private fun readableCheckAndError(requestPermission: Boolean) {
        val dictDir: DocumentFile = application!!.dictDir
        if (dictDir.canRead()) return
        blockAutoLaunch = true
        if (requestPermission &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 0
            )
            return
        }
        blockAutoLaunch = true

        val builder = AlertDialog.Builder(listView.context)
        builder.setTitle(getString(R.string.error))
        builder.setMessage(
            getString(
                R.string.unableToReadDictionaryDir,
                dictDir.uri.toString(),
                Environment.getExternalStorageDirectory()
            )
        )
        builder.setNeutralButton("Close", null)
        builder.create().show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        readableCheckAndError(false)

        application!!.backgroundUpdateDictionaries(dictionaryUpdater)

        setMyListAdapter()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        application = DictionaryApplication.INSTANCE

        super.onCreate(savedInstanceState)
        Log.d(LOG, "onCreate:$this")

        blockAutoLaunch = false

        // UI init.
        setContentView(R.layout.dictionary_manager_activity)
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

        dictionariesOnDeviceHeaderRow = LayoutInflater.from(
            listView.context
        ).inflate(
            R.layout.dictionary_manager_header_row_on_device, listView, false
        ) as LinearLayout?

        downloadableDictionariesHeaderRow = LayoutInflater.from(
            listView.context
        ).inflate(
            R.layout.dictionary_manager_header_row_downloadable, listView, false
        ) as LinearLayout

        showDownloadable = downloadableDictionariesHeaderRow!!
            .findViewById(R.id.hideDownloadable)
        showDownloadable!!.setOnCheckedChangeListener { _, _ -> onShowDownloadableChanged() }

        val downloadManagerIntents = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        downloadManagerIntents.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        ContextCompat.registerReceiver(
            this,
            broadcastReceiver,
            downloadManagerIntents,
            ContextCompat.RECEIVER_EXPORTED
        )

        setMyListAdapter()
        registerForContextMenu(listView)
        listView.itemsCanFocus = true

        readableCheckAndError(true)

        onCreateSetupActionBar()

        val intent = getIntent()
        if (intent != null && intent.action != null &&
            intent.action == Intent.ACTION_VIEW
        ) {
            blockAutoLaunch = true
            val uri = intent.data
            unzipInstall(this, uri!!, uri.lastPathSegment, false)
        }
    }

    private fun onCreateSetupActionBar() {
        val actionBar = supportActionBar!!
        actionBar.setDisplayShowTitleEnabled(false)
        actionBar.setDisplayShowHomeEnabled(false)
        actionBar.setDisplayHomeAsUpEnabled(false)

        filterSearchView = SearchView(actionBar.themedContext)
        filterSearchView!!.setIconifiedByDefault(false)
        // filterSearchView.setIconified(false); // puts the magnifying glass in
        // the
        // wrong place.
        filterSearchView!!.setQueryHint(getString(R.string.searchText))
        filterSearchView!!.setSubmitButtonEnabled(false)
        val lp = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        filterSearchView!!.layoutParams = lp
        filterSearchView!!.inputType = InputType.TYPE_CLASS_TEXT
        filterSearchView!!.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or  // EditorInfo.IME_FLAG_NO_FULLSCREEN | // Requires API
                // 11
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        filterSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterSearchView!!.clearFocus()
                return false
            }

            override fun onQueryTextChange(filterText: String?): Boolean {
                setMyListAdapter()
                return true
            }
        })
        filterSearchView!!.setFocusable(true)

        actionBar.customView = filterSearchView
        actionBar.setDisplayShowCustomEnabled(true)

        // Avoid wasting space on large left inset
        val tb = filterSearchView!!.parent as Toolbar
        tb.setContentInsetsRelative(0, 0)
    }

    public override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onStart() {
        super.onStart()
        uiHandler = Handler(Looper.getMainLooper())
    }

    override fun onStop() {
        super.onStop()
        uiHandler = null
    }

    override fun onResume() {
        super.onResume()

        if (PreferenceActivity.prefsMightHaveChanged) {
            PreferenceActivity.prefsMightHaveChanged = false
            finish()
            startActivity(intent)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        showDownloadable!!.isChecked = prefs.getBoolean(C.SHOW_DOWNLOADABLE, true)

        if (!blockAutoLaunch &&
            intent.getBooleanExtra(C.CAN_AUTO_LAUNCH_DICT, true) &&
            prefs.contains(C.DICT_FILE) &&
            prefs.contains(C.INDEX_SHORT_NAME)
        ) {
            Log.d(LOG, "Skipping DictionaryManager, going straight to dictionary.")
            startActivity(
                getLaunchIntent(
                    applicationContext,
                    prefs.getString(C.DICT_FILE, ""),
                    prefs.getString(C.INDEX_SHORT_NAME, ""),
                    ""
                )
            )
            finish()
            return
        }

        // Remove the active dictionary from the prefs so we won't autolaunch
        // next time.
        prefs.edit().remove(C.DICT_FILE).remove(C.INDEX_SHORT_NAME).commit()

        application!!.backgroundUpdateDictionaries(dictionaryUpdater)

        setMyListAdapter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if ("true" == Settings.System.getString(contentResolver, "firebase.test.lab")) {
            return false // testing the menu is not very interesting
        }
        val sort = menu.add(getString(R.string.sortDicts))
        sort.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        sort.setOnMenuItemClickListener { _ ->
            application!!.sortDictionaries()
            setMyListAdapter()
            true
        }

        val browserDownload = menu.add(getString(R.string.browserDownload))
        browserDownload.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        browserDownload.setOnMenuItemClickListener { _ ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri
                .parse("https://github.com/rdoeffinger/Dictionary/releases/v0.3-dictionaries")
            startActivity(intent)
            false
        }

        onCreateGlobalOptionsMenu(this, menu)
        return true
    }

    override fun onCreateContextMenu(
        menu: ContextMenu, view: View?,
        menuInfo: ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, view, menuInfo)
        Log.d(LOG, "onCreateContextMenu, $menuInfo")

        val adapterContextMenuInfo =
            menuInfo as AdapterContextMenuInfo
        val position = adapterContextMenuInfo.position
        val row = listView.adapter.getItem(position) as MyListAdapter.Row

        if (row.dictionaryInfo == null) {
            return
        }

        if (position > 0 && row.onDevice) {
            val moveToTopMenuItem =
                menu.add(R.string.moveToTop)
            moveToTopMenuItem.setOnMenuItemClickListener { _ ->
                application!!.moveDictionaryToTop(row.dictionaryInfo)
                setMyListAdapter()
                true
            }
        }

        if (row.onDevice) {
            val deleteMenuItem = menu.add(R.string.deleteDictionary)
            deleteMenuItem
                .setOnMenuItemClickListener { _ ->
                    application!!.deleteDictionary(row.dictionaryInfo)
                    setMyListAdapter()
                    true
                }
        }
    }

    private fun onShowDownloadableChanged() {
        setMyListAdapter()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this).edit()
        prefs.putBoolean(C.SHOW_DOWNLOADABLE, showDownloadable!!.isChecked)
        prefs.commit()
    }

    internal inner class MyListAdapter(filters: Array<String>?) :
        BaseAdapter() {
        val dictionariesOnDevice: MutableList<DictionaryInfo> = application!!.getDictionariesOnDevice(filters)
        val downloadableDictionaries: MutableList<DictionaryInfo> = if (showDownloadable!!.isChecked) {
            application!!.getDownloadableDictionaries(filters)
        } else {
            mutableListOf()
        }

        internal inner class Row(
            val dictionaryInfo: DictionaryInfo?,
            val onDevice: Boolean
        )

        override fun getCount(): Int {
            return 2 + dictionariesOnDevice.size + downloadableDictionaries.size
        }

        override fun getItem(position: Int): Row {
            var position = position
            if (position == 0) {
                return Row(null, true)
            }
            position -= 1

            if (position < dictionariesOnDevice.size) {
                return Row(dictionariesOnDevice[position], true)
            }
            position -= dictionariesOnDevice.size

            if (position == 0) {
                return Row(null, false)
            }
            position -= 1

            assert(position < downloadableDictionaries.size)
            return Row(downloadableDictionaries[position], false)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewTypeCount(): Int {
            return 3
        }

        override fun getItemViewType(position: Int): Int {
            val row = getItem(position)
            if (row.dictionaryInfo == null) {
                return if (row.onDevice) 0 else 1
            }
            assert(row.dictionaryInfo.indexInfos.size <= 2)
            return 2
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            if (convertView === dictionariesOnDeviceHeaderRow ||
                convertView === downloadableDictionariesHeaderRow
            ) {
                return convertView
            }

            val row = getItem(position)

            if (row.dictionaryInfo == null) {
                assert(convertView == null)
                return if (row.onDevice) dictionariesOnDeviceHeaderRow else downloadableDictionariesHeaderRow
            }
            return createDictionaryRow(row.dictionaryInfo, parent, convertView, row.onDevice)
        }
    }

    private fun setMyListAdapter() {
        val filter = if (filterSearchView == null) "" else filterSearchView!!.query
            .toString()
        val filters: Array<String> =
            filter.trim { it <= ' ' }.lowercase(Locale.getDefault()).split("[\\s\\-]+".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        listView.adapter = MyListAdapter(filters)
    }

    private fun isDownloadActive(downloadUrl: String, cancel: Boolean): Boolean {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterByStatus(DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING)
        val cursor = downloadManagerQuery(downloadManager, query, cancel) ?: return cancel

        val destFile: String?
        try {
            destFile = File(URL(downloadUrl).path).name
        } catch (e: MalformedURLException) {
            throw RuntimeException("Invalid download URL!", e)
        }
        while (cursor.moveToNext()) {
            if (downloadUrl == cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))) break
            if (destFile == cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))) break
        }
        val active = !cursor.isAfterLast
        if (active && cancel) {
            val downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            finishedDownloadIds.add(downloadId)
            downloadManager.remove(downloadId)
        }
        cursor.close()
        return active
    }

    private fun downloadManagerQuery(
        downloadManager: DownloadManager,
        query: DownloadManager.Query?,
        cancel: Boolean
    ): Cursor? {
        val cursor: Cursor?
        try {
            cursor = downloadManager.query(query)
        } catch (se: SecurityException) {
            Log.w(LOG, "Failed to query download manager", se)
            showDownloadFailedAlert(getString(R.string.downloadManagerQueryPermissionDenied))
            return null
        }

        // Due to a bug, cursor is null instead of empty when
        // the download manager is disabled.
        if (cursor == null && cancel) {
            showDownloadFailedAlert(getString(R.string.downloadManagerQueryFailed))
        }

        return cursor
    }

    private fun showDownloadFailedAlert(msg: String?) {
        AlertDialog.Builder(this).setTitle(getString(R.string.error))
            .setMessage(getString(R.string.downloadFailed, msg))
            .setNeutralButton("Close", null).show()
    }

    private fun createDictionaryRow(
        dictionaryInfo: DictionaryInfo,
        parent: ViewGroup, row: View?, canLaunch: Boolean
    ): View {
        var row = row
        var canLaunch = canLaunch
        if (row == null) {
            row = LayoutInflater.from(parent.context).inflate(
                R.layout.dictionary_manager_row, parent, false
            )
        }
        val name = row.findViewById<TextView>(R.id.dictionaryName)
        val details = row.findViewById<TextView>(R.id.dictionaryDetails)
        name.text = application!!.getDictionaryName(dictionaryInfo.uncompressedFilename)

        val updateAvailable: Boolean = application!!.updateAvailable(dictionaryInfo)
        val downloadButton = row.findViewById<Button>(R.id.downloadButton)
        val downloadable: DictionaryInfo? =
            application!!.getDownloadable(dictionaryInfo.uncompressedFilename)
        var broken = false
        if (!dictionaryInfo.isValid) {
            broken = true
            canLaunch = false
        }
        if (downloadable != null && (!canLaunch || updateAvailable)) {
            downloadButton.text = getString(
                R.string.downloadButton,
                downloadable.zipBytes / 1024.0 / 1024.0
            )
            downloadButton.minWidth = application!!.languageButtonPixels * 3 / 2
            downloadButton.setOnClickListener { _ ->
                downloadDictionary(
                    downloadable.downloadUrl,
                    downloadable.zipBytes,
                    downloadButton
                )
            }
            downloadButton.visibility = View.VISIBLE

            if (isDownloadActive(downloadable.downloadUrl, false)) downloadButton.text = "X"
        } else {
            downloadButton.visibility = View.GONE
        }

        val buttons = row.findViewById<LinearLayout>(R.id.dictionaryLauncherButtons)

        val sortedIndexInfos: MutableList<IndexInfo> = application!!
            .sortedIndexInfos(dictionaryInfo.indexInfos)
        val builder = StringBuilder()
        if (updateAvailable) {
            builder.append(getString(R.string.updateAvailable))
            builder.append("; ")
        }
        assert(buttons.childCount == 4)
        for (i in 0..1) {
            val textButton = buttons.getChildAt(2 * i) as Button
            val imageButton = buttons.getChildAt(2 * i + 1) as ImageButton
            if (i >= sortedIndexInfos.size) {
                textButton.visibility = View.GONE
                imageButton.visibility = View.GONE
                continue
            }
            val indexInfo = sortedIndexInfos[i]
            val button = IsoUtils.INSTANCE.setupButton(
                textButton, imageButton,
                indexInfo
            )

            if (canLaunch) {
                button.setOnClickListener(
                    IntentLauncher(
                        buttons.context,
                        getLaunchIntent(
                            applicationContext,
                            application!!.getPath(dictionaryInfo.uncompressedFilename).getUri()
                                .toString(),
                            indexInfo.shortName, ""
                        )
                    )
                )
            }
            button.isEnabled = canLaunch
            button.setFocusable(canLaunch)
            builder.append(
                getString(
                    R.string.indexInfo, indexInfo.shortName,
                    indexInfo.mainTokenCount
                )
            )
            builder.append("; ")
        }
        builder.append(
            getString(
                R.string.downloadButton,
                dictionaryInfo.uncompressedBytes / 1024.0 / 1024.0
            )
        )
        if (broken) {
            name.text = "Broken: " + application!!.getDictionaryName(dictionaryInfo.uncompressedFilename)
            builder.append("; Cannot be used, redownload, check hardware/file system")
        }
        details.text = builder.toString()

        if (canLaunch) {
            row.setOnClickListener(
                IntentLauncher(
                    parent.context,
                    getLaunchIntent(
                        applicationContext,
                        application!!.getPath(dictionaryInfo.uncompressedFilename).getUri()
                            .toString(),
                        dictionaryInfo.indexInfos[0].shortName, ""
                    )
                )
            )
            // do not setFocusable, for keyboard navigation
            // offering only the index buttons is better.
        }
        row.isClickable = canLaunch
        // Allow deleting, even if we cannot open
        row.isLongClickable = broken || canLaunch
        row.setBackgroundResource(android.R.drawable.menuitem_background)

        return row
    }

    private fun checkStuckDownload(
        downloadManager: DownloadManager,
        downloadId: Long,
        wifiOnly: Boolean
    ) {
        var status = DownloadManager.STATUS_SUCCESSFUL
        var reason = -1
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        try {
            downloadManager.query(query).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    reason =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
            }
        } catch (e: Exception) {
            Log.w(LOG, "Failed to check initial download status", e)
        }
        Log.w(
            LOG,
            "checkStuckDownload: id=$downloadId, status=$status, reason=$reason"
        )

        if (status != DownloadManager.STATUS_PAUSED && status != DownloadManager.STATUS_PENDING) {
            return
        }

        try {
            downloadManager.remove(downloadId)
        } catch (e: Exception) {
            Log.w(LOG, "Failed to remove stuck download", e)
            return
        }

        val msg =
            if (wifiOnly) getString(R.string.downloadFailedNoWifi) else getString(R.string.downloadFailedNoNetwork)
        AlertDialog.Builder(this).setTitle(getString(R.string.error))
            .setMessage(msg)
            .setNeutralButton("Close", null).show()
        // Reset status of download button
        setMyListAdapter()
    }

    @Synchronized
    private fun downloadDictionary(downloadUrl: String, bytes: Long, downloadButton: Button) {
        if (isDownloadActive(downloadUrl, true)) {
            downloadButton.text = getString(
                R.string.downloadButton,
                bytes / 1024.0 / 1024.0
            )
            return
        }
        var request = DownloadManager.Request(Uri.parse(downloadUrl))

        val destFile: String?
        try {
            destFile = File(URL(downloadUrl).path).name
        } catch (e: MalformedURLException) {
            throw RuntimeException("Invalid download URL!", e)
        }
        Log.d(LOG, "Downloading to: $destFile")
        request.setTitle(destFile)
        var destFilePath: DocumentFile? = application!!.dictDir.findFile(destFile)
        destFilePath?.delete()
        destFilePath = application!!.dictDir.createFile("", destFile)
        try {
            request.setDestinationUri(destFilePath!!.uri)
        } catch (_: Exception) {
            destFilePath?.delete()
        }

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager?

        if (downloadManager == null) {
            val msg = getString(R.string.downloadManagerQueryFailed)
            AlertDialog.Builder(this).setTitle(getString(R.string.error))
                .setMessage(getString(R.string.downloadFailed, msg))
                .setNeutralButton("Close", null).show()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val wifiOnly = prefs.getBoolean(C.WIFI_ONLY, true)
        if (wifiOnly) {
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        }

        var downloadId: Long
        try {
            downloadId = downloadManager.enqueue(request)
        } catch (_: Exception) {
            destFilePath?.delete()
            request = DownloadManager.Request(Uri.parse(downloadUrl))
            request.setTitle(destFile)
            if (wifiOnly) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            }
            downloadId = downloadManager.enqueue(request)
        }

        Log.w(LOG, "Download started: $destFile")
        downloadButton.text = "X"
        if (uiHandler != null) {
            val id = downloadId
            uiHandler!!.postDelayed(
                { checkStuckDownload(downloadManager, id, wifiOnly) },
                2000
            )
        }
    }

    companion object {
        private const val LOG = "QuickDic"
        private var blockAutoLaunch = false

        fun getLaunchIntent(c: Context?): Intent {
            val intent = Intent(c, DictionaryManagerActivity::class.java)
            intent.putExtra(C.CAN_AUTO_LAUNCH_DICT, false)
            return intent
        }

        @Throws(IOException::class)
        private fun copyStream(ins: InputStream, outs: FileOutputStream) {
            val buf = ByteBuffer.allocateDirect(1024 * 64)
            val out = outs.channel
            var bytesRead: Int
            var pos = 0
            val bytes = ByteArray(1024 * 64)
            do {
                bytesRead = ins.read(bytes, pos, bytes.size - pos)
                if (bytesRead != -1) pos += bytesRead
                if (if (bytesRead == -1) pos != 0 else 2 * pos >= bytes.size) {
                    buf.put(bytes, 0, pos)
                    pos = 0
                    buf.flip()
                    while (buf.hasRemaining()) out.write(buf)
                    buf.clear()
                }
            } while (bytesRead != -1)
        }
    }
}
