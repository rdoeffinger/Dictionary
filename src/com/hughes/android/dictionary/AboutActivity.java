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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class AboutActivity extends AppCompatActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        DictionaryApplication.INSTANCE.init(getApplicationContext());
        setTheme(DictionaryApplication.INSTANCE.getSelectedTheme().themeId);
        androidx.activity.EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_activity);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, 0);
            return windowInsets;
        });

        final View scrollView = findViewById(R.id.scrollView);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return windowInsets;
        });

        String ver = "???";
        try {
            PackageManager pm = getPackageManager();
            if (pm != null) {
                PackageInfo p = pm.getPackageInfo(getPackageName(), 0);
                ver = p.versionName + " (ID " + PackageInfoCompat.getLongVersionCode(p) + ")";
            }
        } catch (Exception ignored) {
        }
        TextView titleView = findViewById(R.id.titleText);
        titleView.setText("QuickDic " + ver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
