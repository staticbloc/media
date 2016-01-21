package com.staticbloc.media.utils;

import android.graphics.Bitmap;
import android.os.Environment;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhotoWriter {
  private PhotoWriter() {}

  public static File writePhotoToFile(@NonNull byte[] photo, @Nullable File file) {
    if(file == null) {
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
      String filePath = timeStamp + ".jpg";
      file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filePath);
    }

    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      out.write(photo);
      return file;
    } catch (IOException e) {
      throw new RuntimeException("SimpleCamera got an error while saving a captured JPEG byte array to file " + file.getAbsolutePath(), e);
    }
    finally {
      if(out != null) try { out.close(); } catch(Exception ignore) {}
    }
  }

  public static File writePhotoToFile(@NonNull Bitmap photo, @NonNull File file) {
    return writePhotoToFile(photo, 100, Bitmap.CompressFormat.JPEG, file);
  }

  public static File writePhotoToFile(@NonNull Bitmap photo, @IntRange(from=1, to=100) int compressionQuality, Bitmap.CompressFormat compressionFormat, @NonNull File file) {
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      photo.compress(compressionFormat, compressionQuality, out);
      return file;
    } catch (IOException e) {
      throw new RuntimeException("SimpleCamera got an error while saving a bitmap to file " + file.getAbsolutePath(), e);
    }
    finally {
      if(out != null) try { out.close(); } catch(Exception ignore) {}
    }
  }
}
