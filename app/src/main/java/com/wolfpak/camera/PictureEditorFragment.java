package com.wolfpak.camera;

import android.app.Activity;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A fragment that displays a captured image or loops video for the user to edit
 */
public class PictureEditorFragment extends Fragment implements View.OnClickListener {

    private final String TAG = "PictureEditorFragment";

    public static final String ARG_PATH = "path";
    private String mPath; // original path of file

    private TextureView mTextureView;
    private boolean isImage;

    private MediaPlayer mMediaPlayer;

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
        view.findViewById(R.id.btn_download).setOnClickListener(this);
        view.findViewById(R.id.btn_upload).setOnClickListener(this);
        view.findViewById(R.id.btn_undo).setOnClickListener(this);
        view.findViewById(R.id.btn_text).setOnClickListener(this);
        view.findViewById(R.id.btn_blur).setOnClickListener(this);
        view.findViewById(R.id.btn_draw).setOnClickListener(this);

        if(mPath.contains(".jpg"))   {
            isImage = true; // it's image
        } else if(mPath.contains(".mp4"))   {
            isImage = false; // it's video
        } else  {
            Log.e(TAG, "Unknown File Type");
            // TODO handle error
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
            try {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(mPath);
                mMediaPlayer.setSurface(new Surface(mTextureView.getSurfaceTexture()));
                mMediaPlayer.setLooping(true);
                mMediaPlayer.prepareAsync();

                // Play video when the media source is ready for playback.
                mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.start();
                    }
                });

            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates an file for an image to be stored in the pictures directory
     * @return
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    /**
     * Downloads user edited media into corresponding directory in phone
     */
    private void downloadMedia()    {

        File tempfile;
        FileOutputStream output = null;

        if(isImage) {
            try {
                // saves a temporary copy in pictures directory
                tempfile = createImageFile();
                output = new FileOutputStream(tempfile);
                // pulls whatever the textureview has and compresses
                mTextureView.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, output);

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.MediaColumns.DATA, tempfile.getAbsolutePath());

                getActivity().getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                // stores the image with other image media (accessible through Files > Images)
                MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),
                        tempfile.getAbsolutePath(), tempfile.getName(), "No Description");
                // deletes temporary file
                tempfile.delete();
            } catch(IOException e)  {
                e.printStackTrace();
            } finally {
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else  {

        }
    }

    private void closeMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(!isImage) {
            closeMediaPlayer();
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btn_back:
                getFragmentManager().popBackStack();
                break;
            case R.id.btn_download:
                downloadMedia();
                break;
        }
    }

    public PictureEditorFragment() {
        // Required empty public constructor
    }

}
