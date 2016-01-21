package com.staticbloc.media.camera;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

public class ShutterSoundOverride {
  private final Context context;
  private final AudioManager audioManager;
  private final AtomicInteger priority;

  private SoundPool soundPool;
  private int soundId = -1;

  private int overriddenVolume = -1;

  private boolean opened;
  private boolean closed;

  public ShutterSoundOverride(Context context) {
    this.context = context;
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    priority = new AtomicInteger();
  }

  public void open() {
    opened = true;
    closed = false;
  }

  public void open(String absolutePath) {
    soundPool = createSoundPool();

    soundId = soundPool.load(absolutePath, 1);

    opened = true;
    closed = false;
  }

  public void open(int resId) {
    soundPool = createSoundPool();

    soundId = soundPool.load(context, resId, 1);

    opened = true;
    closed = false;
  }

  public void overrideVolume() {
    if(closed) {
      throw new IllegalStateException("Do not call overrideVolume after close");
    }
    else if(opened && overriddenVolume == -1) {
      overriddenVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
      audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    }
  }

  public void play() {
    if(closed) {
      throw new IllegalStateException("Do not call play after close");
    }
    else if(soundId != -1 && soundPool != null) {
      soundPool.play(soundId, 1f, 1f, priority.getAndIncrement(), 0, 1f);
    }
  }

  public void restoreVolume() {
    if(closed) {
      throw new IllegalStateException("Do not call play after close");
    }
    if(opened && overriddenVolume != -1) {
      audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, overriddenVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
      overriddenVolume = -1;
    }
  }

  public void close() {
    closed = true;
    opened = false;
    if(soundPool != null) {
      soundPool.release();
      soundPool = null;
    }
  }

  private static SoundPool createSoundPool() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new SoundPool.Builder()
          .setMaxStreams(1)
          .setAudioAttributes(new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build())
          .build();
    }
    else {
      return new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
    }
  }
}
