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

package com.hughes.android.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.hughes.android.dictionary.DictionaryApplication;
import com.hughes.android.dictionary.DictionaryInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PersistentObjectCache {

    private final File dir;
    private final Map<String, Object> objects = new HashMap<>();

    class ConstrainedOIS extends ObjectInputStream {
        ConstrainedOIS(InputStream in) throws IOException {
            super(in);
        }

        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            // Note: try to avoid adding more classes.
            // LinkedHashMap is already more than enough for a DoS
            if (name.equals(String.class.getName()) ||
                    name.equals(DictionaryInfo.IndexInfo.class.getName()) ||
                    name.equals(ArrayList.class.getName()) ||
                    name.equals(HashMap.class.getName()) ||
                    name.equals(DictionaryInfo.class.getName()) ||
                    name.equals(DictionaryApplication.DictionaryConfig.class.getName()) ||
                    name.equals(LinkedHashMap.class.getName())) {
                return super.resolveClass(desc);
            }
            throw new InvalidClassException("Not allowed to deserialize class", name);
        }
    }

    public synchronized <T extends Serializable> T read(final String filename, final Class<T> resultClass) {
        try {
            Object object = objects.get(filename);
            if (object != null) {
                return resultClass.cast(object);
            }
            Log.d(getClass().getSimpleName(), "Cache miss.");
            final File src = new File(dir, filename);
            if (!src.canRead()) {
                Log.d(getClass().getSimpleName(), "File empty: " + src);
                return null;
            }
            ObjectInputStream in = null;
            try {
                in = new ConstrainedOIS(new BufferedInputStream(new FileInputStream(src)));
                object = in.readObject();
                in.close();
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(), "Deserialization failed: " + src, e);
                try {
                    if (in != null) in.close();
                } catch (IOException e2) {}
                return null;
            }
            objects.put(filename, object);
            return resultClass.cast(object);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public synchronized void write(final String filename, final Serializable object) {
        objects.put(filename, object);
        final File dest = new File(dir, filename);
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dest)));
            out.writeObject(object);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Serialization failed: " + dest, e);
        }
        try {
            if (out != null) out.close();
        } catch (IOException e) {}
    }

    private PersistentObjectCache(final Context context) {
        final File filesDir = context.getFilesDir();
        dir = filesDir != null ? filesDir : Environment.getExternalStorageDirectory();
        if (dir == null) {
            throw new RuntimeException("context.getFilesDir() == " + context.getFilesDir()
                                       + ", Environment.getExternalStorageDirectory()="
                                       + Environment.getExternalStorageDirectory());
        }
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
                throw new RuntimeException("File dir changed.  old=" + instance.dir + ", new="
                                           + context.getFilesDir());
            }
        }
        return instance;
    }

    private static PersistentObjectCache instance = null;

}
