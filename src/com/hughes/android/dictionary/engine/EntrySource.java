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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;

import com.hughes.util.IndexedObject;
import com.hughes.util.raf.RAFListSerializer;

public class EntrySource extends IndexedObject implements Serializable {
  
  private static final long serialVersionUID = -1323165134846120269L;
  
  final String name;
  final int pairEntryStart;
  
  public EntrySource(final int index, final String name, final int pairEntryStart) {
    super(index);
    this.name = name;
    this.pairEntryStart = pairEntryStart;
  }
  
  @Override
  public String toString() {
    return name;
  }


  public static RAFListSerializer<EntrySource> SERIALIZER = new RAFListSerializer<EntrySource>() {

    @Override
    public EntrySource read(RandomAccessFile raf, int readIndex)
        throws IOException {
      final String name = raf.readUTF();
      final int pairEntryStart = raf.readInt();
      return new EntrySource(readIndex, name, pairEntryStart);
    }

    @Override
    public void write(RandomAccessFile raf, EntrySource t) throws IOException {
      raf.writeUTF(t.name);
      raf.writeInt(t.pairEntryStart);
    }    
  };
  
}
