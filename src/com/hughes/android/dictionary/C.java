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

public class C {

    static final String DICTIONARY_CONFIGS = "dictionaryConfigs2";

    public static final String DICT_FILE = "dictFile";
    public static final String INDEX_INDEX = "indexIndex";
    public static final String SEARCH_TOKEN = "searchToken";
    public static final String CAN_AUTO_LAUNCH_DICT = "canAutoLaunch";
    public static final String SHOW_LOCAL = "showLocal";

    public static final String THANKS_FOR_UPDATING_VERSION = "thanksForUpdatingVersion";

    enum Theme {
        DEFAULT(R.style.Theme_Default,
                R.style.Theme_Default_TokenRow_Fg,
                R.color.theme_default_token_row_fg,
                R.drawable.theme_default_token_row_main_bg,
                R.drawable.theme_default_token_row_other_bg,
                R.drawable.theme_default_other_lang_bg),

        LIGHT(R.style.Theme_Light,
                R.style.Theme_Light_TokenRow_Fg,
                R.color.theme_light_token_row_fg,
                R.drawable.theme_light_token_row_main_bg,
                R.drawable.theme_light_token_row_other_bg,
                R.drawable.theme_light_other_lang_bg);

        private Theme(final int themeId, final int tokenRowFg,
                final int tokenRowFgColor,
                final int tokenRowMainBg, final int tokenRowOtherBg,
                final int otherLangBg) {
            this.themeId = themeId;
            this.tokenRowFg = tokenRowFg;
            this.tokenRowFgColor = tokenRowFgColor;
            this.tokenRowMainBg = tokenRowMainBg;
            this.tokenRowOtherBg = tokenRowOtherBg;
            this.otherLangBg = otherLangBg;
        }

        final int themeId;
        final int tokenRowFg;
        final int tokenRowFgColor;
        final int tokenRowMainBg;
        final int tokenRowOtherBg;
        final int otherLangBg;
    }

}
