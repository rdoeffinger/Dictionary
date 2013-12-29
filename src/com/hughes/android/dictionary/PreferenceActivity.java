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

import java.util.List;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

public class PreferenceActivity extends android.preference.PreferenceActivity {

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
    public void onContentChanged() {
        super.onContentChanged();
    }
}
