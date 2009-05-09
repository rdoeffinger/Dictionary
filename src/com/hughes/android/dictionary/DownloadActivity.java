package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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

    final InputStream in;
    final FileOutputStream out;
    
    final File destFile = new File(dest);
    final File destTmpFile;
    try {
      destTmpFile = File.createTempFile("dictionaryDownload", "tmp", destFile.getParentFile());
      final URL uri = new URL(source);
      in = uri.openStream();
      out = new FileOutputStream(destTmpFile);
    } catch (Exception e) {
      Log.e("THAD", "Error downloading file", e);
      setDownloadStatus("Error downloading file: \n" + e.getLocalizedMessage());
      return;
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          long byteCount = 0;
          int bytesRead;
          final byte[] bytes = new byte[4096];
          while ((bytesRead = in.read(bytes)) != -1) {
            out.write(bytes, 0, bytesRead);
            byteCount += bytesRead;
            setDownloadStatus(String.format("Downloading: %d bytes so far", byteCount));
          }
          in.close();
          out.close();
          destFile.delete();
          destTmpFile.renameTo(destFile);
          setDownloadStatus(String.format("Downloaded finished: %d bytes", byteCount));
        } catch (IOException e) {
          Log.e("THAD", "Error downloading file", e);
          setDownloadStatus("Error downloading file: \n" + e.getLocalizedMessage());
        }
      }
    };

    downloadExecutor.execute(runnable);
  }

  private void setDownloadStatus(final String status) {
    final TextView downloadStatus = (TextView) findViewById(R.id.downloadStatus);
    uiHandler.post(new Runnable() {
      public void run() {
        downloadStatus.setText(status);
      }
    });
  }

}
