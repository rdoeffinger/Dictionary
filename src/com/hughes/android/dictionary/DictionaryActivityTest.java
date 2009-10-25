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

  public void testSwitchLanguage() throws Exception {

    final DictionaryActivity dict = getActivity();

    final Runnable switchLang = new Runnable() {
      public void run() {
        getActivity().onLanguageButton();
      }};

    if (dict.languageList.languageData.language == Language.EN) {
      dict.uiHandler.post(switchLang);
      Thread.sleep(100);
    }
    
    assertEquals(Language.DE, dict.languageList.languageData.language);
      
    dict.uiHandler.post(switchLang);
    Thread.sleep(100);
    assertEquals(Language.EN, dict.languageList.languageData.language);

    dict.uiHandler.post(switchLang);
    Thread.sleep(100);
    assertEquals(Language.DE, dict.languageList.languageData.language);
    
    dict.finish();
  }

}
