package com.hughes.android.dictionary.engine;

import java.io.Serializable;

import com.hughes.util.IndexedObject;

public class EntrySource extends IndexedObject implements Serializable {
  
  private static final long serialVersionUID = -1323165134846120269L;
  
  final String name;
  
  public EntrySource(final String name) {
    this.name = name;
  }
  
}
