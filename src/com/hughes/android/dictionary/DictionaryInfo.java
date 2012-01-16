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

import java.io.Serializable;

public class DictionaryInfo implements Serializable {
  
  private static final long serialVersionUID = -6850863377577700388L;
  
  // Stuff populated from the text file.
  public final String[] langIsos = new String[2];
  public String uncompressedFilename;
  public String downloadUrl;
  public long uncompressedSize;
  public long creationMillis;
  public final int[] allTokenCounts = new int[2];
  public final int[] mainTokenCounts = new int[2];

  String name;  // Determined at runtime based on locale on device--user editable.
  String localFile;  // Determined based on device's Environment.
  
  public String toTabSeparatedString() {
    return String.format("%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%d", langIsos[0],
        langIsos[1], uncompressedFilename, downloadUrl, creationMillis, uncompressedSize,
        mainTokenCounts[0], mainTokenCounts[1], allTokenCounts[0],
        allTokenCounts[1]);
  }

  public DictionaryInfo(final String line) {
    final String[] fields = line.split("\t");
    int i = 0;
    langIsos[0] = fields[i++];
    langIsos[1] = fields[i++];
    uncompressedFilename = fields[i++];
    downloadUrl = fields[i++];
    creationMillis = Long.parseLong(fields[i++]);
    uncompressedSize = Long.parseLong(fields[i++]);
    mainTokenCounts[0] = Integer.parseInt(fields[i++]);
    mainTokenCounts[1] = Integer.parseInt(fields[i++]);
    allTokenCounts[0] = Integer.parseInt(fields[i++]);
    allTokenCounts[1] = Integer.parseInt(fields[i++]);
  }

  public DictionaryInfo() {
    // Blank object.
  }

  @Override
  public String toString() {
    return name;
  }


}
