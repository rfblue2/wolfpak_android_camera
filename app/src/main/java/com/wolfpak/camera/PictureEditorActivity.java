package com.wolfpak.camera;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;


public class PictureEditorActivity extends Activity implements View.OnClickListener {

    private final String TAG = "PictureEditorActivity";
    private TextureView mTextureView;
    private boolean isImage;
    private String mFilePath;

    /**
     * Handles lifecycle events on {@link TextureView}
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener()  {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            displayMedia();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        // Also hides action bar
        ActionBar actionBar = getActionBar();
        actionBar.hide();

        setContentView(R.layout.activity_picture_editor);
        mTextureView = (TextureView) findViewById(R.id.edit_texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        findViewById(R.id.btn_back).setOnClickListener(this);

        mFilePath = (String) getIntent().getExtras().get("file");
        Log.i(TAG, "Received " + mFilePath);
        if(mFilePath.contains(".jpg"))   {
            isImage = true;
        }
    }

    private void displayMedia() {
        if(isImage) {
            Log.i(TAG, "Displaying Image");
            Canvas canvas = mTextureView.lockCanvas();
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                ExifInterface exif = new ExifInterface(mFilePath);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            } catch(IOException e)  {
                e.printStackTrace();
            }
            if(orientation == ExifInterface.ORIENTATION_ROTATE_90)  {
                Log.i(TAG, "Image rotated 90 degrees");
                Bitmap src = BitmapFactory.decodeFile(mFilePath);
                // transformation matrix that scales and rotates
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                matrix.postScale(((float) canvas.getWidth()) / src.getHeight(),
                        ((float)canvas.getHeight()) / src.getWidth());
                Bitmap resizedBitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                canvas.drawBitmap(resizedBitmap, 0, 0, null);
            }
            mTextureView.unlockCanvasAndPost(canvas);
        } else  {
            Log.i(TAG, "Displaying Video");
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btn_back:
                finish();
                break;
        }
    }
}
