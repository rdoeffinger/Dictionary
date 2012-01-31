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

import android.app.Activity;
import android.os.Bundle;

public final class AboutActivity extends Activity {

  public static final String CURRENT_DICT_INFO = "currentDictInfo";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    setTheme(((DictionaryApplication)getApplication()).getSelectedTheme().themeId);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.about_activity);
  }

}
