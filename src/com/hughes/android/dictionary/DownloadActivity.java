package com.hughes.android.dictionary;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadActivity extends Activity {

  public static final String SOURCE = "source";
  public static final String DEST = "dest";

  String source;
  String dest;
  
  private final Executor downloadExecutor = Executors.newSingleThreadExecutor();
  private final Handler uiHandler = new Handler();

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final Intent intent = getIntent();
    source = intent.getStringExtra(SOURCE);
    dest = intent.getStringExtra(DEST);
    if (source == null || dest == null) {
      throw new RuntimeException("null source or dest.");
    }
    setContentView(R.layout.download);
    
    final TextView sourceTextView = (TextView) findViewById(R.id.source);
    sourceTextView.setText(source);
    
    final TextView destTextView = (TextView) findViewById(R.id.dest);
    destTextView.setText(dest);

    final ProgressBar progressBar = (ProgressBar) findViewById(R.id.downloadProgressBar);
    progressBar.setIndeterminate(false);
    progressBar.setMax(100);

    final Runnable runnable = new Runnable() {
      public void run() {
        
        for (int i = 0; i < 100; ++i) {
          
          final int progress = i;
          uiHandler.post(new Runnable() {
            public void run() {
              Log.d("THAD", "Setting progress: " + progress);
              progressBar.setProgress(progress);
            }
          });
          
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        
        final TextView downloadComplete = (TextView) findViewById(R.id.downloadComplete);
        uiHandler.post(new Runnable() {
          public void run() {
            progressBar.setProgress(100);
            downloadComplete.setVisibility(View.VISIBLE);
          }
        });
        
      }};
    downloadExecutor.execute(runnable);
    
  }
  
}
