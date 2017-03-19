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

import java.util.ArrayList;
import java.util.List;

import com.hughes.util.LRUCacheMap;

public class TransliteratorManager {

    private static boolean starting = false;
    private static boolean ready = false;
    private static ThreadSetup threadSetup = null;
    private static LRUCacheMap<String, Transliterator> cache = new LRUCacheMap<String, Transliterator>(4);

    // Whom to notify when we're all set up and ready to go.
    private static List<Callback> callbacks = new ArrayList<TransliteratorManager.Callback>();

    public static Transliterator get(String rules) {
        // DO NOT make the method synchronized!
        // synchronizing on the class would break the whole
        // asynchronous init concept, since the runnable
        // then holds the same lock as the init function needs.
        Transliterator result = null;
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
            final String transliterated = get(Language.en.getDefaultNormalizerRules()).transliterate("Îñţérñåţîöñåļîžåţîờñ");
            if (!"internationalization".equals(transliterated)) {
                System.out.println("Wrong transliteration: " + transliterated);
            }

            final List<Callback> callbacks = new ArrayList<TransliteratorManager.Callback>();
            synchronized (TransliteratorManager.class) {
                callbacks.addAll(TransliteratorManager.callbacks);
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
