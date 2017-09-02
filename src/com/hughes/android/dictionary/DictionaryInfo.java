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
import java.util.ArrayList;
import java.util.List;

public class DictionaryInfo implements Serializable {

    private static final long serialVersionUID = -6850863377577700388L;

    public static final class IndexInfo implements Serializable {
        private static final long serialVersionUID = 6524751236198309438L;

        public static final int NUM_CSV_FIELDS = 3;

        public final String shortName; // Often LangISO.
        public final int allTokenCount;
        public final int mainTokenCount;

        public IndexInfo(String shortName, int allTokenCount, int mainTokenCount) {
            this.shortName = shortName;
            this.allTokenCount = allTokenCount;
            this.mainTokenCount = mainTokenCount;
        }

        public StringBuilder append(StringBuilder result) {
            result.append(shortName);
            result.append("\t").append(allTokenCount);
            result.append("\t").append(mainTokenCount);
            return result;
        }

        public IndexInfo(final String[] fields, int i) {
            shortName = fields[i++];
            allTokenCount = Integer.parseInt(fields[i++]);
            mainTokenCount = Integer.parseInt(fields[i++]);
        }
    }

    // Stuff populated from the text file.
    public String uncompressedFilename; // used as a key throughout the program.
    public String downloadUrl;
    public long uncompressedBytes;
    public long zipBytes;
    public long creationMillis;
    public final ArrayList<IndexInfo> indexInfos = new ArrayList<IndexInfo>();
    public String dictInfo;

    public DictionaryInfo() {
        // Blank object.
    }

    public boolean isValid() {
        return indexInfos != null && !indexInfos.isEmpty();
    }

    public StringBuilder append(final StringBuilder result) {
        result.append(uncompressedFilename);
        result.append("\t").append(downloadUrl);
        result.append("\t").append(creationMillis);
        result.append("\t").append(uncompressedBytes);
        result.append("\t").append(zipBytes);
        result.append("\t").append(indexInfos.size());
        for (final IndexInfo indexInfo : indexInfos) {
            indexInfo.append(result.append("\t"));
        }
        result.append("\t").append(dictInfo.replace("\n", "\\\\n"));
        return result;
    }

    public DictionaryInfo(final String line) {
        final String[] fields = line.split("\t");
        int i = 0;
        uncompressedFilename = fields[i++];
        downloadUrl = fields[i++];
        creationMillis = Long.parseLong(fields[i++]);
        uncompressedBytes = Long.parseLong(fields[i++]);
        zipBytes = Long.parseLong(fields[i++]);
        final int size = Integer.parseInt(fields[i++]);
        indexInfos.ensureCapacity(size);
        for (int j = 0; j < size; ++j) {
            indexInfos.add(new IndexInfo(fields, i));
            i += IndexInfo.NUM_CSV_FIELDS;
        }
        dictInfo = fields[i++].replace("\\\\n", "\n");
    }

    @Override
    public String toString() {
        return uncompressedFilename;
    }

}
