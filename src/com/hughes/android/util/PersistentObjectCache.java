package com.hughes.android.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;
import android.util.Log;

public class PersistentObjectCache {

  private final File dir;
  private final Map<String, Object> objects = new LinkedHashMap<String, Object>();
  
  public synchronized Object read(final String filename) {
    Object object = (objects.get(filename));
    if (object != null) {
      return object;
    }
    Log.d(getClass().getSimpleName(), "Cache miss.");
    final File src = new File(dir, filename);
    if (!src.canRead()) {
      Log.d(getClass().getSimpleName(), "File empty: " + src);
      return null;
    }
    try {
      final ObjectInputStream in = new ObjectInputStream(new FileInputStream(src));
      object = in.readObject();
      in.close();
    } catch (Exception e) {
      Log.e(getClass().getSimpleName(), "Deserialization failed: " + src, e);
      return null;
    }
    objects.put(filename, object);
    return object;
  }
  
  public synchronized void write(final String filename, final Object object) {
    objects.put(filename, object);
    final File dest = new File(dir, filename);
    try {
      final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dest));
      out.writeObject(object);
      out.close();
    } catch (Exception e) {
      Log.e(getClass().getSimpleName(), "Serialization failed: " + dest, e);
    }
  }

  private PersistentObjectCache(final Context context) {
    dir = context.getFilesDir();
  }
  
  public static synchronized PersistentObjectCache getInstance() {
    if (instance == null) {
      throw new RuntimeException("getInstance called before init.");
    }
    return instance;
  }

  public static synchronized PersistentObjectCache init(final Context context) {
      if (instance == null) {
        instance = new PersistentObjectCache(context);
      } else {
        if (!instance.dir.equals(context.getFilesDir())) {
          throw new RuntimeException("File dir changed.  old=" + instance.dir + ", new=" + context.getFilesDir());
        }
      }
      return instance;
  }
  
  private static PersistentObjectCache instance = null;

}
