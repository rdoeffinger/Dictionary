// Copyright 2012 Google Inc. All Rights Reserved.
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

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

public class NonLinkClickableSpan extends ClickableSpan {

    // The singleton pattern doesn't work here--we need a separate instance for
    // each span.

    final int color;
    
    public NonLinkClickableSpan(int color) {
        this.color = color;
    }

    // Won't see these on a long-click.
    @Override
    public void onClick(View widget) {
        // Don't need to do anything.  These spans are just used to see where
        // the user long-pressed.
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
        ds.setColor(color);
    }

}
