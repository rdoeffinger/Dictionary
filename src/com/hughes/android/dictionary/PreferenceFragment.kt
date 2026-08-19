package com.hughes.android.dictionary

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == resources.getString(R.string.quickdicDirectoryKey)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val current: String =
                prefs.getString(resources.getString(R.string.quickdicDirectoryKey), "")!!
            val t = EditText(activity)
            t.setText(current)
            t.inputType = InputType.TYPE_CLASS_TEXT
            val b = AlertDialog.Builder(activity)
                .setTitle(requireActivity().getString(R.string.quickdicDirectoryTitle))
                .setView(t)
                .setNegativeButton(getString(android.R.string.cancel), null)
                .setPositiveButton(
                    getString(android.R.string.ok)
                ) { _, _ ->
                    val prefs1 = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    prefs1.edit().putString(
                        resources.getString(R.string.quickdicDirectoryKey),
                        t.text.toString()
                    ).commit()
                }
            b.setNeutralButton(
                getString(R.string.choose)
            ) { _, _ ->
                dirPickerLauncher!!.launch(null)
            }
            b.create().show()
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    var dirPickerLauncher: ActivityResultLauncher<Uri?>? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        val application = DictionaryApplication.INSTANCE
        addPreferencesFromResource(R.xml.preferences)
        val defaultDic = findPreference<ListPreference?>(
            resources.getString(
                R.string.defaultDicKey
            )
        )
        val dicts = application.getDictionariesOnDevice(null)

        val entries = arrayOfNulls<CharSequence>(dicts.size + 1)
        val entryvalues = arrayOfNulls<CharSequence>(dicts.size + 1)

        entries[0] = getString(R.string.none)
        entryvalues[0] = null

        for (i in dicts.indices) {
            entries[i + 1] = dicts[i].dictInfo
            entryvalues[i + 1] = dicts[i].uncompressedFilename
        }

        defaultDic!!.entries = entries
        defaultDic.entryValues = entryvalues
        dirPickerLauncher = registerForActivityResult<Uri?, Uri?>(
            object : OpenDocumentTree() {
                override fun createIntent(context: Context, input: Uri?): Intent {
                    val intent = super.createIntent(context, input)
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    return intent
                }
            },
            ActivityResultCallback { result ->
                if (result == null) return@ActivityResultCallback
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                requireActivity().contentResolver.takePersistableUriPermission(
                    result,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(
                    resources.getString(R.string.quickdicDirectoryKey),
                    result.toString()
                ).commit()
            })
    }
}
