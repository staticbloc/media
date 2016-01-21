package com.staticbloc.media.camera;

import android.annotation.SuppressLint;
import android.media.CamcorderProfile;
import com.staticbloc.media.utils.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.media.CamcorderProfile.QUALITY_1080P;
import static android.media.CamcorderProfile.QUALITY_2160P;
import static android.media.CamcorderProfile.QUALITY_480P;
import static android.media.CamcorderProfile.QUALITY_720P;
import static android.media.CamcorderProfile.QUALITY_CIF;
import static android.media.CamcorderProfile.QUALITY_QCIF;
import static android.media.CamcorderProfile.QUALITY_QVGA;
import static android.media.CamcorderProfile.hasProfile;

/*package*/ class VideoQualities {
  @SuppressLint("InlinedApi") private static final int[] QUALITIES = new int[] { QUALITY_QCIF, QUALITY_QVGA, QUALITY_CIF, QUALITY_480P, QUALITY_720P, QUALITY_1080P, QUALITY_2160P };

  private final List<Size> supportedVideoSizes = new ArrayList<>(7);
  private final Map<Size, CamcorderProfile> sizeToProfile = new HashMap<>(7);

  private VideoQualities() {}

  public static VideoQualities get(final int cameraId) {
    VideoQualities videoQualities = new VideoQualities();
    for (int quality : QUALITIES) {
      if (hasProfile(cameraId, quality)) {
        CamcorderProfile profile = CamcorderProfile.get(cameraId, quality);
        Size size = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
        videoQualities.sizeToProfile.put(size, profile);
        videoQualities.supportedVideoSizes.add(size);
      }
    }

    return videoQualities;
  }

  public CamcorderProfile getProfile(Size size) {
    return sizeToProfile.get(size);
  }

  public List<Size> getSupportedVideoSizes() {
    return new ArrayList<>(supportedVideoSizes);
  }
}
