package com.hughes.android.dictionary.engine;

import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.text.Transliterator;

public class TransliteratorManager {

  private static boolean starting = false;
  private static boolean ready = false;
  
  private static List<Callback> callbacks = new ArrayList<TransliteratorManager.Callback>();
  
  public static synchronized boolean init(final Callback callback) {
    if (ready) {
      return true;
    }
    if (callback != null) {
      callbacks.add(callback);
    }
    if (!starting) {
      starting = true;
      new Thread(init).start();
    }
    return false;
  }
  
  private static final Runnable init = new Runnable() {
    @Override
    public void run() {
      System.out.println("Starting Transliterator load.");
      final String transliterated = 
        Transliterator.createFromRules("", ":: Any-Latin; :: Lower; :: NFD; :: [:Nonspacing Mark:] Remove; :: NFC ;", 
            Transliterator.FORWARD).transliterate("Îñţérñåţîöñåļîžåţîờñ");
      if (!"internationalization".equals(transliterated)) {
        System.out.println("Wrong transliteratation: " + transliterated);
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
  
  
  public interface Callback {
    void onTransliteratorReady();
  }

}
