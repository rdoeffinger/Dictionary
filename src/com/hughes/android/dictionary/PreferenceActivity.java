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

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;

public class PreferenceActivity extends AppCompatActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    static boolean prefsMightHaveChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DictionaryApplication.INSTANCE.init(getApplicationContext());
        final DictionaryApplication application = DictionaryApplication.INSTANCE;
        setTheme(application.getSelectedTheme().themeId);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getString(getString(R.string.quickdicDirectoryKey), "").isEmpty()) {
            prefs.edit().putString(getString(R.string.quickdicDirectoryKey), application.getDictDir().getUri().getPath()).commit();
        }
        if (prefs.getString(getString(R.string.wordListFileKey), "").isEmpty()) {
            prefs.edit().putString(getString(R.string.wordListFileKey), application.getWordListFile().getUri().getPath()).commit();
        }

        /*
          @author Dominik Köppl Preference: select default dictionary As this
         *         list is dynamically generated, we have to do it in this
         *         fashion
         */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preference_activity);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.prefFrag), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // getSupportActionBar().getHeight() is often 0 so get the size from the attribute instead
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
            int abSize = getResources().getDimensionPixelSize(tv.resourceId);
            v.setPadding(
                    insets.left,
                    insets.top + abSize,
                    insets.right,
                    insets.bottom
            );
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private String suggestedPaths(String suffix) {
        String dirs = "";
        String externalDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (new File(externalDir).canWrite())
            dirs += "\n" + externalDir + "/quickDic" + suffix;
        File[] files = getApplicationContext().getExternalFilesDirs(null);
        for (File f : files) {
            if (f.canWrite())
                dirs += "\n" + f.getAbsolutePath() + suffix;
        }
        File fd = getApplicationContext().getFilesDir();
        if (fd.canWrite())
            dirs += "\n" + fd.getAbsolutePath() + suffix;
        return dirs;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String v) {
        DictionaryApplication.INSTANCE.init(getApplicationContext());
        final DictionaryApplication application = DictionaryApplication.INSTANCE;
        DocumentFile dictDir = application.getDictDir();
        if (!dictDir.isDirectory() || !dictDir.canWrite() ||
                !DictionaryApplication.checkFileCreate(dictDir)) {
            String dirs = suggestedPaths("");
            new AlertDialog.Builder(this).setTitle(getString(R.string.error))
            .setMessage(getString(R.string.chosenNotWritable) + dirs)
            .setNeutralButton("Close", null).show();
        }
        DocumentFile wordlist = application.getWordListFile();
        boolean ok = false;
        try {
            ok = wordlist.canWrite();
        } catch (Exception ignored) {
        }
        if (!ok) {
            String dirs = suggestedPaths("/wordList.txt");
            new AlertDialog.Builder(this).setTitle(getString(R.string.error))
            .setMessage(getString(R.string.chosenNotWritable) + dirs)
            .setNeutralButton("Close", null).show();
        }
    }
}
