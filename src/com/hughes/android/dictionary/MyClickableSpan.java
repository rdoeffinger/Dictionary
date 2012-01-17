package com.hughes.android.dictionary;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

public class MyClickableSpan extends ClickableSpan {
  
  static MyClickableSpan instance = new MyClickableSpan();

  // Won't see these on a long-click.
  @Override
  public void onClick(View widget) {
  }

  @Override
  public void updateDrawState(TextPaint ds) {
    super.updateDrawState(ds);
    ds.setUnderlineText(false);
    //ds.setColor(color);
  }

}
