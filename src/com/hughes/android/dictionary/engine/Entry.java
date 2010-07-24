package com.hughes.android.dictionary.engine;

import java.util.List;

public abstract class Entry {
  
  EntrySource entrySource;
  
  abstract List<String> getMainTokens();
  abstract List<String> getOtherTokens();
  
}
