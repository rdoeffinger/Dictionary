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
