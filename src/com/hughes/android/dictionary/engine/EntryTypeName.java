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

package com.hughes.android.dictionary.engine;


public enum EntryTypeName {

//  WIKTIONARY_TITLE_ONE_WORD(0),
//  WIKTIONARY_MEANING_ONE_WORD(0),
//  WIKTIONARY_TRANSLATION_ONE_WORD(0),

  WIKTIONARY_TITLE_SINGLE(0, true),
  WIKTIONARY_FORM_SINGLE(0, true),

  NOUN(0),
  VERB(0),
  ADJ(0),
  ADV(0),
  ONE_WORD(0, true),
  MULTIROW_HEAD_ONE_WORD(0, true),
  MULTIROW_TAIL_ONE_WORD(0, true),

  WIKTIONARY_TITLE_MULTI(0, true),
  WIKTIONARY_FORM_MULTI(0, true),
  WIKTIONARY_TRANSLATION_SENSE(0),
  WIKTIONARY_ENGLISH_DEF_WIKI_LINK(0),
  WIKTIONARY_ENGLISH_DEF_OTHER_LANG(0),
  WIKTIONARY_ENGLISH_DEF(0),

  TWO_WORDS(0),
  THREE_WORDS(0),
  FOUR_WORDS(0),
  FIVE_OR_MORE_WORDS(0),
  WIKTIONARY_TRANSLATION_WIKI_TEXT(0),
  WIKTIONARY_TRANSLATION_OTHER_TEXT(0),
//  WIKTIONARY_EXAMPLE_OTHER_WORDS(0),
  
  MULTIROW_HEAD_MANY_WORDS(0),
  MULTIROW_TAIL_MANY_WORDS(0),
  PART_OF_HYPHENATED(0),
  BRACKETED(0),
  PARENTHESIZED(0),
  SEE_ALSO(0), 
  WIKTIONARY_TRANSLITERATION(0),
  ;

  final int nameResId;
  final boolean overridesStopList;
  
  private EntryTypeName(final int nameResId) {
    this(nameResId, false);
  }

  private EntryTypeName(final int nameResId, final boolean overridesStopList) {
    this.nameResId = nameResId;
    this.overridesStopList = overridesStopList;
  }

}
