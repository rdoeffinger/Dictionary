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

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hughes.android.dictionary.engine.TransliteratorManager;

public class DictionaryApplication extends Application {
  
  @Override
  public void onCreate() {
    super.onCreate();
    Log.d("QuickDic", "Application: onCreate");
    TransliteratorManager.init(null);
    
    setTheme(getSelectedTheme().themeId);

    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
      @Override
      public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
          String key) {
        Log.d("THAD", "prefs changed: " + key);
        
        if (key.equals(getString(R.string.themeKey))) {
          setTheme(getSelectedTheme().themeId);
        }
      }
    });
  }
  
  public C.Theme getSelectedTheme() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final String theme = prefs.getString(getString(R.string.themeKey), "themeLight");
    if (theme.equals("themeLight")) {
      return C.Theme.LIGHT;
    } else {
      return C.Theme.DEFAULT;
    }
  }
}
