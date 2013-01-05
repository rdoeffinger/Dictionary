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

import com.hughes.android.dictionary.engine.Index.IndexEntry;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.regex.Pattern;

public class TokenRow extends RowBase {
  
  public final boolean hasMainEntry;
  
  TokenRow(final RandomAccessFile raf, final int thisRowIndex, final Index index, final boolean hasMainEntry) throws IOException {
    super(raf, thisRowIndex, index);
    this.hasMainEntry = hasMainEntry;
  }

  TokenRow(final int referenceIndex, final int thisRowIndex, final Index index, final boolean hasMainEntry) {
    super(referenceIndex, thisRowIndex, index);
    this.hasMainEntry = hasMainEntry;
  }
  
  public String toString() {
    return getToken() + "@" + referenceIndex;
  }

  @Override
  public TokenRow getTokenRow(final boolean search) {
    return this;
  }

  @Override
  public void setTokenRow(TokenRow tokenRow) {
    throw new RuntimeException("Shouldn't be setting TokenRow's TokenRow!");
  }
  
  public String getToken() {
    return getIndexEntry().token;
  }
  
  public IndexEntry getIndexEntry() {
      return index.sortedIndexEntries.get(referenceIndex);
  }

  @Override
  public void print(final PrintStream out) {
    final String surrounder = hasMainEntry ? "***" : "===";
    out.println(surrounder + getToken() + surrounder);
    for (final HtmlEntry htmlEntry : index.sortedIndexEntries.get(referenceIndex).htmlEntries) {
        out.println("HtmlEntry: " + htmlEntry.title + " <<<" + htmlEntry.getHtml() + ">>>");
    }
  }

  @Override
  public String getRawText(boolean compact) {
    return getToken();
  }

  @Override
  public RowMatchType matches(List<String> searchTokens, final Pattern orderedMatchPattern, Transliterator normalizer, boolean swapPairEntries) {
    return RowMatchType.NO_MATCH;
  }


}
