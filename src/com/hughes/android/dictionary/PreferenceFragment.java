package com.hughes.android.dictionary;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;

import java.util.List;

public class PreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x3253) {
            if (resultCode == Activity.RESULT_OK) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                getActivity().getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                prefs.edit().putString(getResources().getString(R.string.quickdicDirectoryKey), data.getDataString()).commit();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(getResources().getString(R.string.quickdicDirectoryKey))) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            String current = prefs.getString(getResources().getString(R.string.quickdicDirectoryKey), "");
            EditText t = new EditText(getActivity());
            t.setText(current);
            t.setInputType(InputType.TYPE_CLASS_TEXT);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getString(R.string.quickdicDirectoryTitle))
                    .setView(t)
                    .setNegativeButton(getString(android.R.string.cancel), null)
                    .setPositiveButton(getString(android.R.string.ok), (dialogInterface, i) -> {
                        final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getContext());
                        prefs1.edit().putString(getResources().getString(R.string.quickdicDirectoryKey), t.getText().toString()).commit();
                    });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setNeutralButton(getString(R.string.choose), (dialogInterface, i) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    startActivityForResult(intent, 0x3253);
                });
            }
            b.create().show();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String s) {
        final DictionaryApplication application = DictionaryApplication.INSTANCE;
        addPreferencesFromResource(R.xml.preferences);
        ListPreference defaultDic = findPreference(getResources().getString(
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
