package com.wolfpak.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.wolfpak.camera.preview.CameraFragment;

/**
 * An Activity to demonstrate a customized camera as per WolfPak functional description
 * @author Roland Fong
 */
public class CameraActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        DeviceLocator.setActivity(this); // MUST init the activity for device locator
        if(null == savedInstanceState)  {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.newInstance())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
}
