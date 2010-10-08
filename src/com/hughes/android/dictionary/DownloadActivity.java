package com.hughes.android.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
  long bytesDownloaded = 0;
  long contentLength = -1;

  private final Executor downloadExecutor = Executors.newSingleThreadExecutor();
  private final Handler uiHandler = new Handler();

  final AtomicBoolean stop = new AtomicBoolean(false);

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
    setContentView(R.layout.download_activity);

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
      destTmpFile = File.createTempFile("dictionaryDownload", "tmp", destFile
          .getParentFile());
      final URL uri = new URL(source);
      final URLConnection connection = uri.openConnection();
      contentLength = connection.getContentLength();
      in = connection.getInputStream();
      out = new FileOutputStream(destTmpFile);
    } catch (Exception e) {
      Log.e("THAD", "Error downloading file", e);
      setDownloadStatus(String.format(getString(R.string.errorDownloadingFile), e.getLocalizedMessage()));
      return;
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          bytesDownloaded = 0;
          int bytesRead;
          final byte[] bytes = new byte[1024 * 8];
          int count = 0;
          while ((bytesRead = in.read(bytes)) != -1 && !stop.get()) {
            out.write(bytes, 0, bytesRead);
            bytesDownloaded += bytesRead;
            if (count++ % 20 == 0) {
              setDownloadStatus(getString(R.string.downloading,
                  bytesDownloaded, contentLength));
            }
          }
          in.close();
          out.close();
          if (bytesRead == -1 && !stop.get()) {
            destFile.delete();
            destTmpFile.renameTo(destFile);
          } else {
           Log.d("THAD", "Stopped downloading file.");
          }
          setDownloadStatus(String.format(getString(R.string.downloadFinished),
              bytesDownloaded));
        } catch (IOException e) {
          Log.e("THAD", "Error downloading file", e);
          setDownloadStatus(String.format(getString(R.string.errorDownloadingFile), e.getLocalizedMessage()));
        }
      }
    };

    downloadExecutor.execute(runnable);
  }

  @Override
  protected void onStop() {
    stop.set(true);
    super.onStop();
  }

  private void setDownloadStatus(final String status) {
    uiHandler.post(new Runnable() {
      public void run() {
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.downloadProgressBar);
        if (contentLength > 0) {
          progressBar.setProgress((int) (bytesDownloaded * 100 / contentLength));
        }
        
        final TextView downloadStatus = (TextView) findViewById(R.id.downloadStatus);
        downloadStatus.setText(status);
      }
    });
  }

}
