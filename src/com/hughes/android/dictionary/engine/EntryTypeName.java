package com.hughes.android.dictionary.engine;


public enum EntryTypeName {

  NOUN(0),
  VERB(0),
  ADJ(0),
  ADV(0),
  ONE_WORD(0),
  MULTIROW_HEAD_ONE_WORD(0),
  MULTIROW_TAIL_ONE_WORD(0),

  TWO_WORDS(0),
  THREE_WORDS(0),
  FOUR_WORDS(0),
  FIVE_OR_MORE_WORDS(0),
  
  WIKTIONARY_DE_MAIN(0),
  MULTIROW_HEAD_MANY_WORDS(0),
  MULTIROW_TAIL_MANY_WORDS(0),
  PART_OF_HYPHENATED(0),
  BRACKETED(0),
  PARENTHESIZED(0),
  SEE_ALSO(0),
  ;

  final int nameResId;
  
  private EntryTypeName(final int nameResId) {
    this.nameResId = nameResId;
  }

}
