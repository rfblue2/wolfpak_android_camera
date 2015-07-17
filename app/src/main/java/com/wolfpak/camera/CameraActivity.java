package com.wolfpak.camera;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * An Activity to demonstrate a customized camera as per WolfPak functional description
 * @author Roland Fong
 */
public class CameraActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_camera);
        if(null == savedInstanceState)  {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.newInstance())
                    .commit();
        }
    }
}
