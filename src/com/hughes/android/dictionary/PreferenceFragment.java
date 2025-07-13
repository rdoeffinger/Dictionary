package com.hughes.android.dictionary;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;

import java.util.List;

public class PreferenceFragment extends PreferenceFragmentCompat {
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
            b.setNeutralButton(getString(R.string.choose), (dialogInterface, i) -> dirPickerLauncher.launch(null));
            b.create().show();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    ActivityResultLauncher<Uri> dirPickerLauncher;

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
        dirPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree() {
                    @Override
                    public Intent createIntent(Context context, Uri input) {
                        Intent intent = super.createIntent(context, input);
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                        return intent;
                    }
                },
                result -> {
                    if (result == null) return;
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                    getActivity().getContentResolver().takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    prefs.edit().putString(getResources().getString(R.string.quickdicDirectoryKey), result.toString()).commit();
                });
    }
}
