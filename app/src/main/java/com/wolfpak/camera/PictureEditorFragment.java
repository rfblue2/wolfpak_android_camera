package com.wolfpak.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;


/**
 * A fragment that displays a captured image or loops video for the user to edit
 */
public class PictureEditorFragment extends Fragment implements View.OnClickListener {

    private final String TAG = "PictureEditorFragment";

    public static final String ARG_PATH = "path";
    private String mPath;

    private TextureView mTextureView;
    private boolean isImage;

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

    /**
     * Creates a new instance of fragment
     * @param path file path.
     * @return A new instance of fragment PictureEditorFragment.
     */
    public static PictureEditorFragment newInstance(String path) {
        PictureEditorFragment fragment = new PictureEditorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Log.i(TAG, "Received " + mPath);
            mPath = getArguments().getString(ARG_PATH);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_picture_editor, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTextureView = (TextureView) view.findViewById(R.id.edit_texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        view.findViewById(R.id.btn_back).setOnClickListener(this);

        if(mPath.contains(".jpg"))   {
            isImage = true;
        }
    }

    private void displayMedia() {
        if(isImage) {
            Log.i(TAG, "Displaying Image");
            Canvas canvas = mTextureView.lockCanvas();
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                ExifInterface exif = new ExifInterface(mPath);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            } catch(IOException e)  {
                e.printStackTrace();
            }
            if(orientation == ExifInterface.ORIENTATION_ROTATE_90)  {
                Log.i(TAG, "Image rotated 90 degrees");
                Bitmap src = BitmapFactory.decodeFile(mPath);
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
                getFragmentManager().popBackStack();
                break;
        }
    }

    public PictureEditorFragment() {
        // Required empty public constructor
    }

}
