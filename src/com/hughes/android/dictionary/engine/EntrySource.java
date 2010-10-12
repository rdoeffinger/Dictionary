package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;

import com.hughes.util.IndexedObject;
import com.hughes.util.raf.RAFListSerializer;

public class EntrySource extends IndexedObject implements Serializable {
  
  private static final long serialVersionUID = -1323165134846120269L;
  
  final String name;
  
  public EntrySource(final int index, final String name) {
    super(index);
    this.name = name;
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
      return new EntrySource(readIndex, name);
    }

    @Override
    public void write(RandomAccessFile raf, EntrySource t) throws IOException {
      raf.writeUTF(t.name);
    }    
  };
  
}
