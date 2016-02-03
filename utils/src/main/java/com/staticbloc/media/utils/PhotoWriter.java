package com.staticbloc.media.utils;

import android.graphics.Bitmap;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PhotoWriter {
  private PhotoWriter() {}

  @NonNull
  public static File writePhotoToFile(@NonNull byte[] photo, @NonNull File file) {
    FileUtils.mkdirs(file);

    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      out.write(photo);
      return file;
    } catch (IOException e) {
      throw new RuntimeException("SimpleCamera got an error while saving a JPEG byte array to file " + file.getAbsolutePath(), e);
    }
    finally {
      if(out != null) try { out.close(); } catch(Exception ignore) {}
    }
  }

  @NonNull
  public static File writePhotoToFile(@NonNull Bitmap photo, @NonNull File file) {
    return writePhotoToFile(photo, 100, Bitmap.CompressFormat.JPEG, file);
  }

  @NonNull
  public static File writePhotoToFile(@NonNull Bitmap photo, @IntRange(from=1, to=100) int compressionQuality, Bitmap.CompressFormat compressionFormat, @NonNull File file) {
    FileUtils.mkdirs(file);

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
