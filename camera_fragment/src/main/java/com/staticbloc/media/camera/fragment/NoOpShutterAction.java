package com.staticbloc.media.camera.fragment;

import com.staticbloc.media.camera.PhotoCaptureRequest;

/*package*/ class NoOpShutterAction implements PhotoCaptureRequest.OnShutterActionListener {
  @Override public void onShutterAction() {}
}
