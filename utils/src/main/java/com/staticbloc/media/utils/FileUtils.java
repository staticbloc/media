package com.staticbloc.media.utils;

import android.support.annotation.NonNull;

import java.io.File;

public class FileUtils {
  public static void mkdirs(@NonNull File file) {
    File parent = file.getParentFile();
    if(parent != null) {
      boolean mkdirs = parent.mkdirs();
      if(!mkdirs && !parent.exists()) {
        throw new RuntimeException("Could not create parent directories for " + file.getAbsolutePath());
      }
    }
  }
}
