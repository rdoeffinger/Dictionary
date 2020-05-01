package com.hughes.android.dictionary;

import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceFragmentCompat;

import java.util.List;

public class PreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String s) {
        final DictionaryApplication application = DictionaryApplication.INSTANCE;
        addPreferencesFromResource(R.xml.preferences);
        ListPreference defaultDic = (ListPreference) findPreference(getResources().getString(
                R.string.defaultDicKey));
        List<DictionaryInfo> dicts = application.getDictionariesOnDevice(null);

        final CharSequence[] entries = new CharSequence[dicts.size()];
        final CharSequence[] entryvalues = new CharSequence[dicts.size()];

        for (int i = 0; i < entries.length; ++i) {
            entries[i] = dicts.get(i).dictInfo;
            entryvalues[i] = dicts.get(i).uncompressedFilename;
        }

        defaultDic.setEntries(entries);
        defaultDic.setEntryValues(entryvalues);
    }
}
