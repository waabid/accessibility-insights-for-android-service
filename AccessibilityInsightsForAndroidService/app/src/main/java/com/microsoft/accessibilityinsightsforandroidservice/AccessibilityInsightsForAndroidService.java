// Portions Copyright (c) Microsoft Corporation
// Licensed under the MIT License.
//
// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.microsoft.accessibilityinsightsforandroidservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class AccessibilityInsightsForAndroidService extends AccessibilityService {
  private static final String TAG = "AccessibilityInsightsForAndroidService";
  private static ServerThread ServerThread = null;
  private final AxeScanner axeScanner;
  private final EventHelper eventHelper;
  private final DeviceConfigFactory deviceConfigFactory;
  private final OnScreenshotAvailableProvider onScreenshotAvailableProvider =
      new OnScreenshotAvailableProvider();
  private final BitmapProvider bitmapProvider = new BitmapProvider();
  private HandlerThread screenshotHandlerThread = null;
  private ScreenshotController screenshotController = null;
  private int activeWindowId = -1; // Set initial state to an invalid ID
  private WindowManager myWindowManager;
  private ArrayList<ElementHighlight> views;
  private MediaRecorder mediaRecorder;
  private VirtualDisplay virtualDisplay;
  private int count;

  public AccessibilityInsightsForAndroidService() {
    deviceConfigFactory = new DeviceConfigFactory();
    axeScanner =
        AxeScannerFactory.createAxeScanner(deviceConfigFactory, this::getRealDisplayMetrics);
    eventHelper = new EventHelper(new ThreadSafeSwapper<>());
  }

  private DisplayMetrics getRealDisplayMetrics() {
    // Correct screen metrics are only accessible within the context of the running
    // service. They're not available when the service initializes, hence the callback
    return DisplayMetricsHelper.getRealDisplayMetrics(this);
  }

  private void StopServerThread() {
    if (ServerThread != null) {
      ServerThread.exit();
      try {
        ServerThread.join();
      } catch (InterruptedException e) {
        Logger.logError(TAG, StackTrace.getStackTrace(e));
      }
      ServerThread = null;
    }
  }

  private void stopScreenshotHandlerThread() {
    if (screenshotHandlerThread != null) {
      screenshotHandlerThread.quit();
      screenshotHandlerThread = null;
    }

    screenshotController = null;
  }

  @Override
  protected void onServiceConnected() {
    Logger.logVerbose(TAG, "*** onServiceConnected");

    if (Settings.canDrawOverlays(this)) {
      myWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
      views = new ArrayList<ElementHighlight>();
    }

    this.startScreenshotActivity();

    AccessibilityServiceInfo info = new AccessibilityServiceInfo();
    info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
    info.feedbackType = AccessibilityEvent.TYPES_ALL_MASK;
    info.notificationTimeout = 0;
    info.flags =
        AccessibilityServiceInfo.DEFAULT
            | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

    setServiceInfo(info);

    stopScreenshotHandlerThread();
    screenshotHandlerThread = new HandlerThread("ScreenshotHandlerThread");
    screenshotHandlerThread.start();
    Handler screenshotHandler = new Handler(screenshotHandlerThread.getLooper());

    screenshotController =
        new ScreenshotController(
            this::getRealDisplayMetrics,
            screenshotHandler,
            onScreenshotAvailableProvider,
            bitmapProvider,
            MediaProjectionHolder::get);

    StopServerThread();

    ResponseThreadFactory responseThreadFactory =
        new ResponseThreadFactory(
            screenshotController, eventHelper, axeScanner, deviceConfigFactory);
    ServerThread = new ServerThread(new ServerSocketFactory(), responseThreadFactory);
    ServerThread.start();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    Logger.logVerbose(TAG, "*** onUnbind");
    StopServerThread();
    stopScreenshotHandlerThread();
    MediaProjectionHolder.cleanUp();
    return false;
  }

  private void cleanViews() {
    if (myWindowManager != null) {
      views.forEach(view -> {
        myWindowManager.removeView(view);
      });
      views.clear();
    }
  }

  private void redrawHighlights() {
    if (myWindowManager != null) {
      views.forEach(view -> {
        view.invalidate();
      });
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    // This logic ensures that we only track events from the active window, as
    // described under "Retrieving window content" of the Android service docs at
    // https://www.android-doc.com/reference/android/accessibilityservice/AccessibilityService.html
    int windowId = event.getWindowId();

    int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
        || eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT) {
      activeWindowId = windowId;
    }

    if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      createHighlightBox(event);
    }

    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      redrawHighlights();
    }

    if (activeWindowId == windowId) {
      eventHelper.recordEvent(getRootInActiveWindow());
    }

    if (count == 30) {
      startRecording();
    }

    if (count == 200) {
      stopRecording();
    }

    count++;
    Log.v(TAG, "count is " + count);
  }

  private void createHighlightBox(AccessibilityEvent event) {
    if (Settings.canDrawOverlays(this) && isNodeUnique(event)) {
      int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
      int offset = 0;
      if (resourceId > 0) {
        offset = getResources().getDimensionPixelSize(resourceId);
      }
      ElementHighlight elementHighlight = new ElementHighlight(this, event, offset);
      WindowManager.LayoutParams params = new WindowManager.LayoutParams(getRealDisplayMetrics().widthPixels, getRealDisplayMetrics().heightPixels, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
      myWindowManager.addView(elementHighlight, params);
      views.add(elementHighlight);
    }
  }

  private boolean isNodeUnique(AccessibilityEvent event) {
    for (ElementHighlight view : views) {
      if (view.getEventSource().equals(event.getSource())) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void onInterrupt() {}

  private void startScreenshotActivity() {
    Intent startScreenshot = new Intent(this, ScreenshotActivity.class);
    startScreenshot.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startScreenshot);
  }

  private void startRecording() {
    MediaProjection mediaProjection = MediaProjectionHolder.get();
    if (mediaProjection != null && mediaRecorder == null) {
      Log.v(TAG, "about to start");
      mediaRecorder = new MediaRecorder();
      prepareMediaRecorder();
      DisplayMetrics displayMetrics = getRealDisplayMetrics();
      virtualDisplay = mediaProjection.createVirtualDisplay("my display", displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
      mediaRecorder.start();
    }
  }

  private void prepareMediaRecorder() {
    DisplayMetrics displayMetrics = getRealDisplayMetrics();
    String directory = this.getExternalFilesDir(null) + File.separator + "Test_Recordings_For_Focus_Order";
    File folder = new File(directory);
    if (!folder.exists()) {
      folder.mkdir();
    }
    String filename = (new Date()).toString() + "_focus_recording.mp4";
    String filepath = directory + File.separator + filename;
    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    mediaRecorder.setVideoEncodingBitRate(512 * 1000);
    mediaRecorder.setVideoFrameRate(24);
    mediaRecorder.setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
    mediaRecorder.setOutputFile(filepath);
    Log.v(TAG, "file path is: " + filepath);
    try {
      mediaRecorder.prepare();

    } catch (Exception  e) {
      e.printStackTrace();
      return;
    }
  }

  private void stopRecording() {
    Log.v(TAG, "about to stop");
    if (mediaRecorder != null) {
      mediaRecorder.stop();
      mediaRecorder.reset();
      mediaRecorder = null;
    }

    if (virtualDisplay != null) {
      virtualDisplay.release();
    }
  }
}
