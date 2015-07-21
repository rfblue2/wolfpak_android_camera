package com.wolfpak.camera;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.wolfpak.camera.colorpicker.ColorPickerView;

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/*
TODO
Undo button ideas:
Separate pictureEditingManager class to hold each event
Each activity (blur or draw) sends an "Event" object of sorts to class
Undo triggers a redo of all events until the said event to undo
Manager should hold max of, say 20 events

Event would be a motionevent and then a tag of whether it was a draw or blur
(MotionEvent.obtain(event))
 */

/**
 * A fragment that displays a captured image or loops video for the user to edit
 */
public class PictureEditorFragment extends Fragment
        implements View.OnClickListener, View.OnTouchListener {

    private static final String TAG = "PictureEditorFragment";

    private static final String serverURL = "http://ec2-52-4-176-1.compute-1.amazonaws.com/posts/";

    private static TextureView mTextureView;
    private static boolean isImage;

    private String mVideoPath;

    // for blurring
    private static final int BLUR_RADIUS = 20;
    private static final int BLUR_SIDE = 100;
    private RenderScript mBlurScript = null;
    private ScriptIntrinsicBlur mIntrinsicScript = null;
    private Bitmap mTextureBitmap = null;
    private Bitmap mBlurredBitmap = null;
    private Canvas blurCanvas = null;

    private MediaPlayer mMediaPlayer;

    private EditableOverlay mOverlay;
    private static ColorPickerView mColorPicker;
    private ImageButton mDrawButton;

    ProgressDialog mProgressDialog;

    // parameters
    File mFileToServer = null;
    String handle = null;
    String latitude = null;
    String longitude = null;//device location
    String nsfw = null;
    String is_image = null;
    String user = null;

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
     * @return A new instance of fragment PictureEditorFragment.
     */
    public static PictureEditorFragment newInstance(/*String path*/) {
        PictureEditorFragment fragment = new PictureEditorFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        mTextureView.setOnTouchListener(this);

        mOverlay = (EditableOverlay) view.findViewById(R.id.overlay);
        mOverlay.init((TextOverlay) view.findViewById(R.id.text_overlay));

        view.findViewById(R.id.btn_back).setOnClickListener(this);
        view.findViewById(R.id.btn_download).setOnClickListener(this);
        view.findViewById(R.id.btn_upload).setOnClickListener(this);
        view.findViewById(R.id.btn_undo).setOnClickListener(this);
        view.findViewById(R.id.btn_text).setOnClickListener(this);
        view.findViewById(R.id.btn_blur).setOnClickListener(this);
        mDrawButton = (ImageButton) view.findViewById(R.id.btn_draw);
        mDrawButton.setOnClickListener(this);

        mBlurScript = RenderScript.create(getActivity());
        mIntrinsicScript = ScriptIntrinsicBlur.create(mBlurScript, Element.U8_4(mBlurScript));

        mColorPicker = (ColorPickerView)
                view.findViewById(R.id.color_picker_view);
        mColorPicker.setOnColorChangedListener(new ColorPickerView.OnColorChangedListener() {

            @Override
            public void onColorChanged(int newColor) {
                if(mOverlay.getState() == EditableOverlay.STATE_DRAW) {
                    mDrawButton.setBackgroundColor(newColor);
                    mOverlay.setColor(newColor);
                } else if(mOverlay.getState() == EditableOverlay.STATE_TEXT)    {
                    mOverlay.getTextOverlay().setTextColor(newColor);
                }
            }
        });
        mColorPicker.setVisibility(View.GONE);

        if(CameraFragment.getImage() != null) {
            isImage = true;
        } else if(CameraFragment.getVideoPath() != null)    {
            isImage = false;
        } else {
            Log.e(TAG, "Unknown File Type");
            // TODO handle error
        }
    }

    /**
     * @return if image is handled
     */
    public static boolean isImage()    {
        return isImage;
    }

    /**
     * Displays media onto textureview
     */
    private void displayMedia() {
        if(isImage) {
            if(CameraFragment.getImage() != null) {
                Log.i(TAG, "Displaying Image");
                Canvas canvas = mTextureView.lockCanvas();
                ByteBuffer buffer = CameraFragment.getImage().getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                CameraFragment.getImage().close();
                CameraFragment.setImage(null);
                // resize horizontally oriented images
                if (src.getWidth() > src.getHeight()) {
                    // transformation matrix that scales and rotates
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    matrix.postScale(((float) canvas.getWidth()) / src.getHeight(),
                            ((float) canvas.getHeight()) / src.getWidth());
                    Bitmap resizedBitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                    canvas.drawBitmap(resizedBitmap, 0, 0, null);
                    UndoManager.addScreenState(resizedBitmap); // initial state
                }

                mTextureView.unlockCanvasAndPost(canvas);
            } else {
                Canvas c = mTextureView.lockCanvas();
                c.drawBitmap(UndoManager.getLastScreenState(), 0, 0, null);
                mTextureView.unlockCanvasAndPost(c);
            }
        } else  {
            Log.i(TAG, "Displaying Video");
            if(CameraFragment.getVideoPath() != null) {
                mVideoPath = CameraFragment.getVideoPath();
                UndoManager.addScreenState(mOverlay.getBitmap());// initial state
                CameraFragment.setVideoPath(null);
            } else  {
                mOverlay.setBitmap(UndoManager.getLastScreenState());
            }
            try {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(mVideoPath);
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

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return the ColorPickerView
     */
    public static ColorPickerView getColorPicker()  {
        return mColorPicker;
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
                ".jpeg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    /**
     * Prepares image and executes async task to send media to server
     */
    public void sendToServer()  {
        Log.i(TAG, "Sending to Server");

        File tempfile = null;

        try {
            tempfile = createImageFile();
            FileOutputStream output = new FileOutputStream(tempfile);
            // blits overlay onto textureview
            Bitmap finalImage = Bitmap.createBitmap(mTextureView.getBitmap());
            Canvas c = new Canvas(finalImage);
            c.drawBitmap(mOverlay.getBitmap(), 0, 0, null);
            // compresses whatever textureview and overlay have
            finalImage.compress(Bitmap.CompressFormat.JPEG, 75, output);
        } catch(IOException e)  {
            e.printStackTrace();
        }

        if(tempfile != null)    {
            // dummy parameters
            handle = tempfile.getName();
            latitude = "0";
            longitude = "0";//device location
            nsfw = "true";
            is_image = "true";
            user = "temp_test_id";
            mFileToServer = tempfile;
            // saves a temporary copy in pictures directory
        }
        // check network connection
        ConnectivityManager connMgr = (ConnectivityManager)
                getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {

            RequestParams params = new RequestParams();
            params.put("handle", "name_handle");
            params.put("latitude", latitude);
            params.put("longitude", longitude);
            params.put("is_nsfw", nsfw);
            params.put("is_image", is_image);
            params.put("user", user);

            try {
                params.put("media", mFileToServer);
            } catch(FileNotFoundException e)    {
                e.printStackTrace();
            }

            AsyncHttpClient client = new AsyncHttpClient();
            client.post(serverURL, params, new AsyncHttpResponseHandler() {

                @Override
                public void onStart() {
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    Log.e(TAG, "Upload Success");
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                    Log.e(TAG, "Upload Failure " + statusCode);
                }

                @Override
                public void onRetry(int retryNo) {
                    // called when request is retried
                }
            });
        } else {
            Toast.makeText(getActivity(), "Couldn't connect to network", Toast.LENGTH_SHORT);
            Log.e(TAG, "Couldn't connect to network");
        }


    }

    /**
     * Generates a unique UUID from device properties
     * @return
     */
    private String generateUUID()   {
        // generates a uuid
        String android_id = Settings.Secure.getString(getActivity().getApplicationContext()
                .getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i(TAG, "android_id : " + android_id);

        final TelephonyManager tm = (TelephonyManager) getActivity().getBaseContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        Log.i(TAG, "tmDevice : " + tmDevice);
        tmSerial = "" + tm.getSimSerialNumber();
        Log.i(TAG, "tmSerial : " + tmSerial);
        androidId = ""
                + android.provider.Settings.Secure.getString(
                getActivity().getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice
                .hashCode() << 32)
                | tmSerial.hashCode());
        String UUID = deviceUuid.toString();
        Log.i(TAG, "UUID : " + UUID);
        return UUID;
    }

    /**
     * Downloads user edited media into corresponding directory in phone
     */
    private void downloadMedia()    {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            File tempfile;
            FileOutputStream output = null;

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(getActivity());
                mProgressDialog.setTitle("Saving...");
                mProgressDialog.setMessage("Please wait.");
                mProgressDialog.setCancelable(false);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... arg0) {
                try {
                    if(isImage) {
                        try {
                            // saves a temporary copy in pictures directory
                            tempfile = createImageFile();
                            output = new FileOutputStream(tempfile);
                            // blits overlay onto textureview
                            Bitmap finalImage = Bitmap.createBitmap(mTextureView.getBitmap());
                            Canvas c = new Canvas(finalImage);
                            c.drawBitmap(mOverlay.getBitmap(), 0, 0, null);
                            // compresses whatever textureview and overlay have
                            finalImage.compress(Bitmap.CompressFormat.JPEG, 100, output);

                            ContentValues values = new ContentValues();
                            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                            values.put(MediaStore.MediaColumns.DATA, tempfile.getAbsolutePath());

                            getActivity().getContentResolver().insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            // stores the image with other image media (accessible through Files > Images)
                            MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),
                                    tempfile.getAbsolutePath(), tempfile.getName(), "No Description");
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
                        // save video
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if ( mProgressDialog!=null) {
                    mProgressDialog.dismiss();
                }
            }

        };
        task.execute((Void[])null);

    }

    /**
     * @return the bitmap of ONLY the textureview
     */
    public static Bitmap getBitmap()   {
        return mTextureView.getBitmap();
    }

    public static void setBitmap(Bitmap bitmap)    {
        Canvas c = mTextureView.lockCanvas();
        c.drawBitmap(bitmap, 0, 0, null);
        mTextureView.unlockCanvasAndPost(c);
    }

    /**
     * Takes a square bitmap and turns it into a circle
     * @param bitmap
     * @return
     */
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = BLUR_SIDE / 2;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    private void blurImage(int action, float x, float y)    {
        switch(action)  {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                blurCanvas = mTextureView.lockCanvas();
                mTextureBitmap = mTextureView.getBitmap();
                // Blur bitmap
                mBlurredBitmap = Bitmap.createBitmap(BLUR_SIDE, BLUR_SIDE, mTextureBitmap.getConfig());

                // prevent errors when blur rectangle exceeds bounds
                if((int) x - BLUR_SIDE / 2 <= 0)
                    x = BLUR_SIDE / 2;
                if((int) y - BLUR_SIDE / 2 <= 0)
                    y = BLUR_SIDE / 2;

                if((int) x + BLUR_SIDE > mTextureBitmap.getWidth())
                    x = mTextureBitmap.getWidth() - BLUR_SIDE / 2;
                if((int) y + BLUR_SIDE > mTextureBitmap.getHeight())
                    y = mTextureBitmap.getHeight() - BLUR_SIDE / 2;

                final Bitmap blurSource = Bitmap.createBitmap(mTextureBitmap,
                        (int) x - BLUR_SIDE / 2, (int) y - BLUR_SIDE / 2, BLUR_SIDE, BLUR_SIDE);

                final Allocation inAlloc = Allocation.createFromBitmap(mBlurScript,
                        blurSource, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_GRAPHICS_TEXTURE);
                final Allocation outAlloc = Allocation.createFromBitmap(mBlurScript, mBlurredBitmap);

                mIntrinsicScript.setRadius(BLUR_RADIUS);
                mIntrinsicScript.setInput(inAlloc);
                mIntrinsicScript.forEach(outAlloc);
                outAlloc.copyTo(mBlurredBitmap);

                // first draw what's on textureview, then draw blur
                blurCanvas.drawBitmap(mTextureBitmap, 0, 0, null);
                blurCanvas.drawBitmap(getRoundedCornerBitmap(mBlurredBitmap), (int) x - BLUR_SIDE / 2, (int)y - BLUR_SIDE / 2, null);
                mTextureView.unlockCanvasAndPost(blurCanvas);
                break;
            case MotionEvent.ACTION_UP:
                Bitmap screen = Bitmap.createBitmap(mTextureView.getBitmap());
                Canvas c = new Canvas(screen);
                c.drawBitmap(mOverlay.getBitmapWithoutText(), 0, 0, null);
                UndoManager.addScreenState(Bitmap.createBitmap(screen));
                screen.recycle();
                break;
            case MotionEvent.ACTION_CANCEL:
            default: break;
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
    public void onResume() {
        super.onResume();
        if(UndoManager.getNumberOfStates() > 1) {
            if (isImage) {
                /*Canvas c = mTextureView.lockCanvas();
                c.drawBitmap(UndoManager.getLastScreenState(), 0, 0, null);
                mTextureView.unlockCanvasAndPost(c);*/
            } else {
                mOverlay.setBitmap(UndoManager.getLastScreenState());
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // TODO put code to save textureview bitmap so it doesn't go back to original picture
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
            case R.id.btn_upload:
                sendToServer();
                break;
            case R.id.btn_undo:
                if(UndoManager.getNumberOfStates() > 1) {
                    if (isImage) {
                        Canvas c = mTextureView.lockCanvas();
                        c.drawBitmap(UndoManager.undoScreenState(), 0, 0, null);
                        mTextureView.unlockCanvasAndPost(c);
                        mOverlay.clearBitmap();
                    } else {
                        mOverlay.setBitmap(UndoManager.undoScreenState());
                    }
                } else  {
                    Toast.makeText(getActivity(), "Cannot Undo", Toast.LENGTH_SHORT);
                }
                break;
            case R.id.btn_draw:
                if(mOverlay.getState() == EditableOverlay.STATE_TEXT)    {
                    mOverlay.getTextOverlay().setEditable(false);
                    mOverlay.getTextOverlay().setEnabled(false);
                    mOverlay.getTextOverlay().clearFocus();
                }
                if(mOverlay.getState() != EditableOverlay.STATE_DRAW) {
                    mOverlay.setState(EditableOverlay.STATE_DRAW);
                    mOverlay.setColor(mColorPicker.getColor());
                    mColorPicker.setVisibility(View.VISIBLE);
                    mDrawButton.setBackgroundColor(mOverlay.getColor());
                } else {
                    mOverlay.setState(EditableOverlay.STATE_IDLE);
                    mColorPicker.setVisibility(View.GONE);
                    mDrawButton.setBackgroundColor(0x00000000);
                }
                break;
            case R.id.btn_blur:
                if(isImage) {// don't enable blurring for video
                    if(mOverlay.getState() == EditableOverlay.STATE_BLUR) {
                        mOverlay.setState(EditableOverlay.STATE_IDLE);
                        break;
                    } else if(mOverlay.getState() == EditableOverlay.STATE_DRAW)   {
                        mColorPicker.setVisibility(View.GONE);
                        mDrawButton.setBackgroundColor(0x00000000);
                    } else if(mOverlay.getState() == EditableOverlay.STATE_TEXT)    {
                        mOverlay.getTextOverlay().setEditable(false);
                        mOverlay.getTextOverlay().setEnabled(false);
                        mOverlay.getTextOverlay().clearFocus();
                    }
                    mOverlay.setState(EditableOverlay.STATE_BLUR);
                }
                break;
            case R.id.btn_text:
                if(mOverlay.getState() == EditableOverlay.STATE_DRAW)   {
                    mColorPicker.setVisibility(View.GONE);
                    mDrawButton.setBackgroundColor(0x00000000);
                }
                if(mOverlay.getState() != EditableOverlay.STATE_TEXT) {
                    mOverlay.setState(EditableOverlay.STATE_TEXT);
                    mOverlay.getTextOverlay().setEditable(true);
                    mOverlay.getTextOverlay().setEnabled(true);
                    mOverlay.getTextOverlay().requestFocus();
                    Log.i(TAG, "About to advance, currently: " + mOverlay.getTextOverlay().getState());
                    mOverlay.getTextOverlay().nextState();// go to default state
                } else  {// if text is selected
                    Log.i(TAG, "About to advance, currently: " + mOverlay.getTextOverlay().getState());
                    // goes to next state and ends text editing session if text hidden
                    if(mOverlay.getTextOverlay().nextState() == TextOverlay.TEXT_STATE_HIDDEN)  {
                        mOverlay.setState(EditableOverlay.STATE_IDLE);
                    }
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch(v.getId())   {
            case R.id.edit_texture:
                if(mOverlay.getState() == EditableOverlay.STATE_BLUR) {
                    blurImage(event.getAction(), event.getX(), event.getY());
                }
                break;
        }
        return true;
    }

    public PictureEditorFragment() {
        // Required empty public constructor
    }

}
