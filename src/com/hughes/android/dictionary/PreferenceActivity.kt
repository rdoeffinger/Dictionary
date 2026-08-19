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

import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import java.io.File

class PreferenceActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    public override fun onCreate(savedInstanceState: Bundle?) {
        DictionaryApplication.applyTheme(this)
        val application = DictionaryApplication.INSTANCE

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString(getString(R.string.quickdicDirectoryKey), "")!!.isEmpty()) {
            prefs.edit().putString(
                getString(R.string.quickdicDirectoryKey),
                application.dictDir.uri.path
            ).commit()
        }
        if (prefs.getString(getString(R.string.wordListFileKey), "")!!.isEmpty()) {
            prefs.edit().putString(
                getString(R.string.wordListFileKey),
                application.wordListFile!!.uri.path
            ).commit()
        }

        /*
          @author Dominik Köppl Preference: select default dictionary As this
         *         list is dynamically generated, we have to do it in this
         *         fashion
         */
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_activity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(
            toolbar
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.prefFrag)
        ) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    private fun suggestedPaths(suffix: String?): String {
        var dirs = ""
        val externalDir = Environment.getExternalStorageDirectory().absolutePath
        if (File(externalDir).canWrite()) dirs += "\n$externalDir/quickDic$suffix"
        val files = applicationContext.getExternalFilesDirs(null)
        for (f in files) {
            if (f.canWrite()) dirs += "\n" + f.absolutePath + suffix
        }
        val fd = applicationContext.filesDir
        if (fd.canWrite()) dirs += "\n" + fd.absolutePath + suffix
        return dirs
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, v: String?) {
        DictionaryApplication.INSTANCE.init(applicationContext)
        val application = DictionaryApplication.INSTANCE
        val dictDir = application.dictDir
        if (!dictDir.isDirectory || !dictDir.canWrite() || !DictionaryApplication.checkFileCreate(
                dictDir
            )
        ) {
            val dirs = suggestedPaths("")
            AlertDialog.Builder(this).setTitle(getString(R.string.error))
                .setMessage(getString(R.string.chosenNotWritable) + dirs)
                .setNeutralButton("Close", null).show()
        }
        val wordlist = application.wordListFile!!
        var ok = false
        try {
            ok = wordlist.canWrite()
        } catch (_: Exception) {
        }
        if (!ok) {
            val dirs = suggestedPaths("/wordList.txt")
            AlertDialog.Builder(this).setTitle(getString(R.string.error))
                .setMessage(getString(R.string.chosenNotWritable) + dirs)
                .setNeutralButton("Close", null).show()
        }
    }

    companion object {
        @JvmField
        var prefsMightHaveChanged: Boolean = false
    }
}
