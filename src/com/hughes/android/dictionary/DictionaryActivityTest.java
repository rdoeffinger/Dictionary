package com.hughes.android.dictionary;

import android.test.ActivityInstrumentationTestCase2;

public class DictionaryActivityTest extends ActivityInstrumentationTestCase2<DictionaryActivity> {

  public DictionaryActivityTest() {
    super(DictionaryActivity.class.getPackage().getName(), DictionaryActivity.class);
  }
  
  public void testRunAndFinish() {
    final DictionaryActivity dict = getActivity();
    dict.finish();
  }
  
  abstract class NotifyRunnable implements Runnable {
    boolean finished = false;
    public final void run() {
      assertEquals(false, finished);
      run2();
      synchronized (this) {
        finished = true;
        this.notifyAll();
      }
    }
    public void waitForFinish() throws InterruptedException {
      synchronized (this) {
        while (!finished) {
          this.wait();
        }
        finished = false;
      }
      getActivity().waitForSearchEnd();
    }
    protected abstract void run2();
  }

  private void postAndWait(final NotifyRunnable notifyRunnable) throws Exception {
    getActivity().uiHandler.post(notifyRunnable);
    notifyRunnable.waitForFinish();
  }

  public void resetDictionary() throws Exception {
    final DictionaryActivity dict = getActivity();
    
    if (dict.languageList.languageData.language == Language.EN) {
      postAndWait(switchLangRunnable());
    }
    assertEquals(Language.DE, dict.languageList.languageData.language);

    postAndWait(new NotifyRunnable() {
      protected void run2() {
        dict.searchText.setText("");
        dict.onSearchTextChange("");
      }
    });
  }

  public void testSwitchLanguage() throws Exception {
    final DictionaryActivity dict = getActivity();
    resetDictionary();

    final NotifyRunnable switchLang = switchLangRunnable();

    postAndWait(switchLang);
    assertEquals(Language.EN, dict.languageList.languageData.language);
    assertEquals("EN", dict.langButton.getText().toString());

    postAndWait(switchLang);
    assertEquals(Language.DE, dict.languageList.languageData.language);
    assertEquals("DE", dict.langButton.getText().toString());
    
    dict.finish();
  }

  public void testUpDownArrows() throws Exception {
    final DictionaryActivity dict = getActivity();
    resetDictionary();
    assertEquals(0, dict.getSelectedItemPosition());
    
    final NotifyRunnable upButton = new NotifyRunnable() {
      protected void run2() {
        dict.onUpButton();
      }
    };
    final NotifyRunnable downButton = new NotifyRunnable() {
      protected void run2() {
        dict.onDownButton();
      }
    };
    
    dict.getListView().requestFocus();
    assertTrue(dict.getListView().isFocused());
    
    String word1 = "-1";
    String word2 = "-14";
    String word3 = "-15";

    postAndWait(upButton);
    assertEquals(0, dict.getSelectedItemPosition());
    assertEquals(word1, dict.searchText.getText().toString());

    postAndWait(downButton);
    assertEquals(2, dict.getSelectedItemPosition());
    assertEquals(word2, dict.searchText.getText().toString());
    
    postAndWait(downButton);
    assertEquals(4, dict.getSelectedItemPosition());
    assertEquals(word3, dict.searchText.getText().toString());

    postAndWait(upButton);
    assertEquals(2, dict.getSelectedItemPosition());
    assertEquals(word2, dict.searchText.getText().toString());
    
    postAndWait(upButton);
    assertEquals(0, dict.getSelectedItemPosition());
    assertEquals(word1, dict.searchText.getText().toString());
    
    postAndWait(upButton);
    assertEquals(0, dict.getSelectedItemPosition());

    postAndWait(downButton);
    assertEquals(2, dict.getSelectedItemPosition());

    dict.finish();
  }

  private NotifyRunnable switchLangRunnable() {
    final NotifyRunnable switchLang = new NotifyRunnable() {
      public void run2() {
        getActivity().onLanguageButton();
      }};
    return switchLang;
  }

}
