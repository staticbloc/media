package com.staticbloc.media.app;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    View decorView = getWindow().getDecorView();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      decorView.setSystemUiVisibility(getKitKatSystemUiFlags());
    }
    else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      decorView.setSystemUiVisibility(getJellyBeanSystemUiFlags());
    }
    else {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    if(getSupportFragmentManager().findFragmentById(R.id.fragment) == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.fragment, MainFragment.newInstance())
          .commit();
    }

  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      View decorView = getWindow().getDecorView();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        decorView.setSystemUiVisibility(getKitKatSystemUiFlags());
      }
      else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        decorView.setSystemUiVisibility(getJellyBeanSystemUiFlags());
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private static int getKitKatSystemUiFlags() {
    return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private static int getJellyBeanSystemUiFlags() {
    return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_FULLSCREEN;
  }
}
