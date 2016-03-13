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
import android.os.Build;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.List;

public class PreferenceActivity extends android.preference.PreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    static boolean prefsMightHaveChanged = false;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        final DictionaryApplication application = (DictionaryApplication) getApplication();
        setTheme(application.getSelectedTheme().themeId);
        
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getString(getString(R.string.quickdicDirectoryKey), "").equals("")) {
            prefs.edit().putString(getString(R.string.quickdicDirectoryKey), application.getDictDir().getAbsolutePath()).commit();
        }
        if (prefs.getString(getString(R.string.wordListFileKey), "").equals("")) {
            prefs.edit().putString(getString(R.string.wordListFileKey), application.getWordListFile().getAbsolutePath()).commit();
        }

        /**
         * @author Dominik Köppl Preference: select default dictionary As this
         *         list is dynamically generated, we have to do it in this
         *         fashion
         */
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ListPreference defaultDic = (ListPreference) findPreference(getResources().getString(
                R.string.defaultDicKey));
        List<DictionaryInfo> dicts = application.getDictionariesOnDevice(null);

        final CharSequence[] entries = new CharSequence[dicts.size()];
        final CharSequence[] entryvalues = new CharSequence[dicts.size()];

        for (int i = 0; i < entries.length; ++i)
        {
            entries[i] = dicts.get(i).dictInfo;
            entryvalues[i] = dicts.get(i).uncompressedFilename;
        }

        defaultDic.setEntries(entries);
        defaultDic.setEntryValues(entryvalues);
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

    @Override
    public void onContentChanged() {
        super.onContentChanged();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String v) {
        final DictionaryApplication application = (DictionaryApplication)getApplication();
        File dictDir = application.getDictDir();
        if (!dictDir.isDirectory() || !dictDir.canWrite() ||
            !application.checkFileCreate(dictDir)) {
            String dirs = "";
            String externalDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (new File(externalDir).canWrite())
                dirs += "\n" + externalDir + "/quickDic";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                File[] files = getApplicationContext().getExternalFilesDirs(null);
                for (File f : files) {
                    if (f.canWrite())
                        dirs += "\n" + f.getAbsolutePath();
                }
            } else {
                File efd = null;
                try {
                    efd = getApplicationContext().getExternalFilesDir(null);
                } catch (Exception e) {
                }
                if (efd != null) {
                    String externalFilesDir = efd.getAbsolutePath();
                    if (new File(externalFilesDir).canWrite())
                        dirs += "\n" + externalFilesDir;
                }
            }
            new AlertDialog.Builder(this).setTitle(getString(R.string.error))
                .setMessage(getString(R.string.chosenNotWritable) + dirs)
                    .setNeutralButton("Close", null).show();
        }
    }
}
