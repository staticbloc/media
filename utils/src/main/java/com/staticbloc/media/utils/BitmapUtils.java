package com.staticbloc.media.utils;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;

public class BitmapUtils {
  @NonNull
  public static byte[] bitmapToByteArray(@NonNull Bitmap bitmap) {
    ByteArrayOutputStream out = null;
    try {
      out = new ByteArrayOutputStream(bitmap.getWidth() * bitmap.getHeight());
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
      return out.toByteArray();
    }
    finally {
      if(out != null) try { out.close(); } catch(Exception ignore) {}
    }
  }
}
