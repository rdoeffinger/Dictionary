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

import com.ibm.icu.text.Transliterator;

import java.util.Comparator;

public class NormalizeComparator implements Comparator<String> {
  
  final Transliterator normalizer;
  final Comparator<Object> comparator;

  public NormalizeComparator(final Transliterator normalizer,
      final Comparator<Object> comparator) {
    this.normalizer = normalizer;
    this.comparator = comparator;
  }

  @Override
  public int compare(final String s1, final String s2) {
    final String n1 = normalizer.transform(s1);
    final String n2 = normalizer.transform(s2);
    final int cn = comparator.compare(n1, n2);
    if (cn != 0) {
      return cn;
    }
    return comparator.compare(s1, s2);
  }

}
