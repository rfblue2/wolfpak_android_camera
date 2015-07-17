package com.wolfpak.camera;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
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
import android.net.Uri;
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

import com.wolfpak.camera.colorpicker.ColorPickerView;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

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

    public static final String ARG_PATH = "path";
    private String mPath; // original path of file

    private static final String serverURL = "https://ec2-52-4-176-1.compute-1.amazonaws.com/";

    private TextureView mTextureView;
    private boolean isImage;

    // for blurring
    private static final int BLUR_RADIUS = 20;
    private static final int BLUR_SIDE = 100;
    private boolean blur; // true if blur tool selected
    private RenderScript mBlurScript = null;
    private ScriptIntrinsicBlur mIntrinsicScript = null;
    private Bitmap mTextureBitmap = null;
    private Bitmap mBlurredBitmap = null;
    private Canvas blurCanvas = null;

    private MediaPlayer mMediaPlayer;

    private EditableOverlay mOverlay;
    private ColorPickerView mColorPicker;
    private ImageButton mDrawButton;

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
            mPath = getArguments().getString(ARG_PATH);
            Log.i(TAG, "Received " + mPath);
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
        mTextureView.setOnTouchListener(this);

        mOverlay = (EditableOverlay) view.findViewById(R.id.overlay);
        mOverlay.init();

        view.findViewById(R.id.btn_back).setOnClickListener(this);
        view.findViewById(R.id.btn_download).setOnClickListener(this);
        view.findViewById(R.id.btn_upload).setOnClickListener(this);
        view.findViewById(R.id.btn_undo).setOnClickListener(this);
        view.findViewById(R.id.btn_text).setOnClickListener(this);
        view.findViewById(R.id.btn_blur).setOnClickListener(this);
        mDrawButton = (ImageButton) view.findViewById(R.id.btn_draw);
        mDrawButton.setOnClickListener(this);

        blur = false;
        mBlurScript = RenderScript.create(getActivity());
        mIntrinsicScript = ScriptIntrinsicBlur.create(mBlurScript, Element.U8_4(mBlurScript));

        mColorPicker = (ColorPickerView)
                view.findViewById(R.id.color_picker_view);
        mColorPicker.setOnColorChangedListener(new ColorPickerView.OnColorChangedListener() {

            @Override
            public void onColorChanged(int newColor) {
                mDrawButton.setBackgroundColor(newColor);
                mOverlay.setColor(newColor);
            }
        });
        mColorPicker.setVisibility(View.GONE);

        if(mPath.contains(".jpeg"))   {
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
                ".jpeg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    public void sendToServer()  {
        Log.i(TAG, "Sending to Server");
        File tempfile = null;
        // saves a temporary copy in pictures directory
        try {
            tempfile = createImageFile();
            FileOutputStream output = new FileOutputStream(tempfile);
            // blits overlay onto textureview
            Bitmap finalImage = Bitmap.createBitmap(mTextureView.getBitmap());
            Canvas c = new Canvas(finalImage);
            c.drawBitmap(mOverlay.getBitmap(), 0, 0, null);
            // compresses whatever textureview and overlay have
            finalImage.compress(Bitmap.CompressFormat.JPEG, 100, output);
        } catch(IOException e)  {
            e.printStackTrace();
        }
        // check network connection
        ConnectivityManager connMgr = (ConnectivityManager)
                getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            new ServerUploadTask().execute(tempfile.getAbsolutePath());
        } else {
            Toast.makeText(getActivity(), "Couldn't connect to network", Toast.LENGTH_SHORT);
            Log.e(TAG, "Couldn't connect to network");
        }
    }

    /**
     * Trust every server - dont check for any certificate
     */
    private static void trustAllHosts() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                }
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                }
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[] {};
                }
            }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection
                    .setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
     * Uploads media to server
     * @param srcPath
     * @return
     * @throws IOException
     */
    private int uploadMedia(String srcPath) throws IOException  {
        // TODO Currently only handles images!!!
        Log.i(TAG, "Uploading Media");
        /*HttpsURLConnection conn = null;
        DataOutputStream dos = null;
        int serverResponseCode = 0;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = generateUUID();//"*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;*/
        File sourceFile = new File(srcPath);
        String fileName = sourceFile.getName();

        // dummy parameters
        String handle = sourceFile.getName();
        String latitude = "0";
        String longitude = "0";//device location
        String nsfw = "true";
        String is_image = "true";
        String user = "temp_test_id";

        try {
            trustAllHosts();
            MultipartUtility mu = new MultipartUtility(serverURL, "UTF-8");
            mu.addFormField("media", fileName);
            mu.addFormField("handle", handle);
            mu.addFormField("latitude", latitude);
            mu.addFormField("longitude", longitude);
            mu.addFormField("nsfw", nsfw);
            mu.addFormField("is_image", is_image);
            mu.addFormField("user", user);

            mu.addFilePart("file", sourceFile);

            List<String> response = mu.finish();

            Log.d(TAG, "SERVER REPLY ");
            for(String line : response) {
                Log.d(TAG, line);
            }
            /*// open a URL connection to the Servlet
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            URL url = new URL(serverURL);

            // Open a HTTP  connection to  the URL
            trustAllHosts();
            conn = (HttpsURLConnection) url.openConnection();
            conn.setHostnameVerifier(DO_NOT_VERIFY);
            conn.setDoInput(true); // Allow Inputs
            conn.setDoOutput(true); // Allow Outputs
            conn.setUseCaches(false); // Don't use a Cached Copy
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            //conn.setRequestProperty("media", fileName);
            //conn.setRequestProperty("handle", handle);
            //conn.setRequestProperty("latitude", latitude);
            //conn.setRequestProperty("longitude", longitude);
            //conn.setRequestProperty("nsfw", nsfw);
            //conn.setRequestProperty("is_image", is_image);
            //conn.setRequestProperty("user", user);

            dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"media\";filename=\""
                            + fileName + "\"" + lineEnd);
            dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
            dos.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);

            dos.writeBytes(lineEnd);

            // create a buffer of  maximum size
            bytesAvailable = fileInputStream.available();

            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0) {

                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            }

            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"handle\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            //dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + lineEnd);
            //dos.writeBytes("Content-Length: " + parameter.length() + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(handle);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"latitude\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(latitude);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"longitude\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(longitude);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"is_nsfw\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(nsfw);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"is_image\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(is_image);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"user\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(user);
            dos.writeBytes(lineEnd);

            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Responses from the server (code and message)
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();

            Log.i(TAG, "HTTP Response is : "
                    + serverResponseMessage + ": " + serverResponseCode);

            if(serverResponseCode == 200){

                Log.i(TAG, "Upload Complete");
                Toast.makeText(getActivity(), "Upload Completed", Toast.LENGTH_SHORT);
            }

            //close the streams //
            fileInputStream.close();
            dos.flush();
            dos.close(); */
            Log.i(TAG, "Exiting Upload");
        } catch(Exception e)    {
            e.printStackTrace();
        }
        return 0;
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

        }
        Toast.makeText(getActivity(), "SAVED!", Toast.LENGTH_SHORT).show();
    }

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
                if((int) x - BLUR_SIDE / 2 < 0)
                    x = BLUR_SIDE / 2;
                if((int) y - BLUR_SIDE / 2 < 0)
                    y = BLUR_SIDE / 2;

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
            case R.id.btn_draw:
                if(mOverlay.getState() != EditableOverlay.STATE_DRAW) {
                    mOverlay.setState(EditableOverlay.STATE_DRAW);
                    mOverlay.setColor(mColorPicker.getColor());
                    mColorPicker.setVisibility(View.VISIBLE);
                    mDrawButton.setBackgroundColor(mOverlay.getColor());
                }
                else {
                    mOverlay.setState(EditableOverlay.STATE_IDLE);
                    mColorPicker.setVisibility(View.GONE);
                    mDrawButton.setBackgroundColor(0x00000000);
                }
                break;
            case R.id.btn_blur:
                if(isImage) {// never enable blurring for video
                    if(blur) mOverlay.setState(EditableOverlay.STATE_IDLE);
                    else mOverlay.setState(EditableOverlay.STATE_BLUR);
                    blur = !blur;
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch(v.getId())   {
            case R.id.edit_texture:
                if(blur) {
                    blurImage(event.getAction(), event.getX(), event.getY());
                }
                break;
        }
        return true;
    }

    public PictureEditorFragment() {
        // Required empty public constructor
    }

    private class ServerUploadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                uploadMedia(params[0]);
                return "Upload completed";
            } catch(IOException e)  {
                e.printStackTrace();
            }
            return "Upload failed";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }

}
