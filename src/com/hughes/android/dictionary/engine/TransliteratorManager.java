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

import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hughes.util.LRUCacheMap;
import com.ibm.icu.text.Transliterator;

public class TransliteratorManager {

    private static boolean starting = false;
    private static boolean ready = false;
    private static ThreadSetup threadSetup = null;
    private static final LRUCacheMap<String, Transliterator> cache = new LRUCacheMap<>(4);

    // Whom to notify when we're all set up and ready to go.
    private static final List<Callback> callbacks = new ArrayList<>();

    public static Transliterator get(String rules) {
        // DO NOT make the method synchronized!
        // synchronizing on the class would break the whole
        // asynchronous init concept, since the runnable
        // then holds the same lock as the init function needs.
        Transliterator result;
        synchronized (cache) {
            result = cache.get(rules);
            if (result == null) {
                result = Transliterator.createFromRules("", rules, Transliterator.FORWARD);
                cache.put(rules, result);
            }
        }
        return result;
    }

    public static synchronized boolean init(final Callback callback, final ThreadSetup setupCallback) {
        if (ready) {
            return true;
        }
        if (callback != null) {
            callbacks.add(callback);
        }
        if (!starting) {
            starting = true;
            threadSetup = setupCallback;
            new Thread(init).start();
        }
        return false;
    }

    private static final Runnable init = new Runnable() {
        @Override
        public void run() {
            synchronized (TransliteratorManager.class) {
                if (threadSetup != null) threadSetup.onThreadStart();
            }
            System.out.println("Starting Transliterator load.");
            try {
                final String transliterated = get(Language.en.getDefaultNormalizerRules()).transliterate("Îñţérñåţîöñåļîžåţîờñ");
                if (!"internationalization".equals(transliterated)) {
                    System.out.println("Wrong transliteration: " + transliterated);
                }
            } catch (StackOverflowError e) {
                // This seems to happen with Android 14 on some Pixel 7 devices
                System.out.println("Transliterator load failed with stack overflow" + e.getMessage());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Locale.setDefault(Locale.Category.FORMAT, Locale.US);
                    Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
                }
            }

            final List<Callback> callbacks;
            synchronized (TransliteratorManager.class) {
                callbacks = new ArrayList<>(TransliteratorManager.callbacks);
                ready = true;
            }
            for (final Callback callback : callbacks) {
                callback.onTransliteratorReady();
            }
        }
    };

    public interface ThreadSetup {
        void onThreadStart();
    }

    public interface Callback {
        void onTransliteratorReady();
    }

}
