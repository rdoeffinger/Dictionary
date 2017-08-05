// Copyright 2017 Reimar Döffinger
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

import java.util.Locale;

import java.text.Collator;

final public class CollatorWrapper {
static public Collator getInstance() {
    return Collator.getInstance();
}
static public Collator getInstanceStrengthIdentical(Locale l) {
    Collator c = Collator.getInstance(l);
    c.setStrength(Collator.IDENTICAL);
    return c;
}
}
