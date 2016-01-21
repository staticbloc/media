package com.staticbloc.media.camera;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*package*/ class CameraState {
  @IntDef(value = {PRE_INIT, INIT, OPEN, ERROR, CLOSE, RELEASE}, flag = true)
  @Retention(RetentionPolicy.SOURCE)
  private @interface State {}

  private static final int PRE_INIT = 0;
  public static final int INIT = 1;
  public static final int OPEN = 1 << 1;
  public static final int ERROR = 1 << 2;
  public static final int CLOSE = 1 << 3;
  public static final int RELEASE = 1 << 4;

  private int state = PRE_INIT;

  public synchronized void set(@State int state) {
    this.state = state;
  }

  public synchronized boolean compare(@State int stateToCompare) {
    return state != PRE_INIT && (stateToCompare & state) == state;
  }

  public synchronized boolean compareAndSwap(@State int stateToCompare, @State int stateToSwap) {
    if (state != PRE_INIT && (stateToCompare & state) == state) {
      state = stateToSwap;
      return true;
    }
    else {
      return false;
    }
  }

  public synchronized boolean compareThenSet(@State int stateToCompare, @State int stateToSet) {
    boolean result = state != PRE_INIT && (stateToCompare & state) == state;
    state = stateToSet;
    return result;
  }

  @Override
  public String toString() {
    switch(state) {
      case PRE_INIT:
        return "PRE_INIT";
      case INIT:
        return "INIT";
      case OPEN:
        return "OPEN";
      case ERROR:
        return "ERROR";
      case CLOSE:
        return "CLOSE";
      case RELEASE:
        return "RELEASE";
      default:
        return "<bad_state>";
    }
  }
}
