// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.accessibilityinsightsforandroidservice;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.annotation.Nullable;

import java.util.Set;

public class ScreenshotActivity extends Activity {
  private MediaProjectionManager mediaManager;
  private static final int SCREENSHOT = 99999;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mediaManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    startActivityForResult(mediaManager.createScreenCaptureIntent(), SCREENSHOT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == SCREENSHOT) {
      if (resultCode == RESULT_OK) {
        MediaProjectionHolder.set(mediaManager.getMediaProjection(resultCode, data));
      }
    }

    if (MediaProjectionHolder.get() == null) {
      Toast.makeText(this, R.string.screenshot_permission_not_granted, Toast.LENGTH_LONG).show();
    }

    if (Settings.canDrawOverlays(this) == false) {
      Intent overlay = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
      overlay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(overlay);
    }

    finish();
  }
}
