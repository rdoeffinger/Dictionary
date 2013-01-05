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

package com.hughes.android.dictionary;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DownloadActivity extends Activity {

  public static final String SOURCE = "source";
  public static final String DEST = "dest";
  public static final String MESSAGE = "message";

  String source;
  String dest;
  String message;
  long bytesProcessed = 0;
  long contentLength = -1;

  private final Executor downloadExecutor = Executors.newSingleThreadExecutor();
  private final Handler uiHandler = new Handler();

  final AtomicBoolean stop = new AtomicBoolean(false);
  
  public static Intent getLaunchIntent(final String source, final String dest, final String message) {
    final Intent intent = new Intent();
    intent.setClassName(DownloadActivity.class.getPackage().getName(), DownloadActivity.class.getName());
    intent.putExtra(SOURCE, source);
    intent.putExtra(DEST, dest);
    intent.putExtra(MESSAGE, message);
    return intent;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    setTheme(((DictionaryApplication)getApplication()).getSelectedTheme().themeId);

    super.onCreate(savedInstanceState);
    final Intent intent = getIntent();
    source = intent.getStringExtra(SOURCE);
    dest = intent.getStringExtra(DEST);
    message = intent.getStringExtra(MESSAGE);
    if (source == null || dest == null) {
      throw new RuntimeException("null source or dest.");
    }
    setContentView(R.layout.download_activity);

    final TextView sourceTextView = (TextView) findViewById(R.id.source);
    sourceTextView.setText(source);

    final TextView destTextView = (TextView) findViewById(R.id.dest);
    destTextView.setText(dest);

    final TextView messageTextView = (TextView) findViewById(R.id.downloadMessage);
    messageTextView.setText(message);

    final ProgressBar progressBar = (ProgressBar) findViewById(R.id.downloadProgressBar);
    progressBar.setIndeterminate(false);
    progressBar.setMax(100);
    
    bytesProcessed = 0;
    contentLength = 100;
    setDownloadStatus(getString(R.string.openingConnection));

    final Runnable runnable = new Runnable() {
      public void run() {

        try {
          final File destFile = new File(dest);
          if (destFile.getAbsoluteFile().getParent() != null) {
            destFile.getAbsoluteFile().getParentFile().mkdirs();
          }

          final File destTmpFile = File.createTempFile("dictionaryDownload", "tmp", destFile
              .getParentFile());
          destTmpFile.deleteOnExit();

          final URL uri = new URL(source);
          final URLConnection connection = uri.openConnection();
          contentLength = connection.getContentLength();
          final InputStream in = connection.getInputStream();
          if (in == null) {
            throw new IOException("Unable to open InputStream from source: " + source);
          }
          final FileOutputStream out = new FileOutputStream(destTmpFile); 
          int bytesRead = copyStream(in, out, R.string.downloading);
          
          if (bytesRead == -1 && !stop.get()) {
            destFile.delete();
            destTmpFile.renameTo(destFile);
          } else {
           Log.d("THAD", "Stopped downloading file.");
          }
          
          if (dest.toLowerCase().endsWith(".zip")) {
            final ZipFile zipFile = new ZipFile(destFile);
            final File destUnzipped = new File(dest.substring(0, dest.length() - 4));
            final ZipEntry zipEntry = zipFile.getEntry(destUnzipped.getName());
            if (zipEntry != null) {
              destUnzipped.delete();
              Log.d("THAD", "Unzipping entry: " + zipEntry.getName() + " to " + destUnzipped);
              final InputStream zipIn = zipFile.getInputStream(zipEntry);
              final OutputStream zipOut = new FileOutputStream(destUnzipped);
              contentLength = zipEntry.getSize();
              bytesRead = copyStream(zipIn, zipOut, R.string.unzipping);
              destFile.delete();
            }
          }
          
          setDownloadStatus(String.format(getString(R.string.downloadFinished),
              bytesProcessed));
          
          // If all went well, we can exit this activity.
          uiHandler.post(new Runnable() {
            @Override
            public void run() {
              finish();
            }
          });
          
        } catch (IOException e) {
          Log.e("THAD", "Error downloading file", e);
          setDownloadStatus(String.format(getString(R.string.errorDownloadingFile), e.getLocalizedMessage()));
        }
      }

      private int copyStream(final InputStream in, final OutputStream out, final int messageId)
          throws IOException {
        bytesProcessed = 0;
        int bytesRead;
        final byte[] bytes = new byte[1024 * 16];
        int count = 0;
        while ((bytesRead = in.read(bytes)) != -1 && !stop.get()) {
          out.write(bytes, 0, bytesRead);
          bytesProcessed += bytesRead;
          if (count++ % 20 == 0) {
            setDownloadStatus(getString(messageId, bytesProcessed, contentLength));
          }
        }
        in.close();
        out.close();
        return bytesRead;
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
          progressBar.setProgress((int) (bytesProcessed * 100 / contentLength));
        }
        
        final TextView downloadStatus = (TextView) findViewById(R.id.downloadStatus);
        downloadStatus.setText(status);
      }
    });
  }

}
