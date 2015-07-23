package com.wolfpak.camera.editor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.wolfpak.camera.DeviceLocator;

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

/**
 * Handles the saving of media to the phone and to the server
 * @author Roland Fong
 */
public class MediaSaver {

    private static final String TAG = "MediaSaver";
    private static final String SERVER_URL = "http://ec2-52-4-176-1.compute-1.amazonaws.com/posts/";

    private Activity mActivity;
    private EditableOverlay mOverlay;
    private TextureView mTextureView;
    private ProgressDialog mProgressDialog;

    // parameters
    private HashMap mMap;
    private String[] keys = { "handle", "latitude", "longitude", "is_nsfw", "is_image", "user", "media" };
    private File mFileToServer = null;

    /**
     * Constructor for MediaSaver
     * @param activity
     * @param overlay the EditableOverlay
     * @param textureView the Editor's TextureView
     */
    public MediaSaver(Activity activity, EditableOverlay overlay, TextureView textureView)    {
        mActivity = activity;
        mOverlay = overlay;
        mTextureView = textureView;
        // init the hashmap
        mMap = new HashMap(7);
        for(String key : keys)
            mMap.put(key, null);
    }

    /**
     * Creates an file for an image stored in the pictures directory and writes data to file system
     * @return file in pictures directory
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imagefile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpeg",         /* suffix */
                storageDir      /* directory */
        );

        // write data to file system
        FileOutputStream output = new FileOutputStream(imagefile);
        // combines overlay and textureview
        Bitmap finalImage = Bitmap.createBitmap(mTextureView.getBitmap());
        Canvas c = new Canvas(finalImage);
        c.drawBitmap(mOverlay.getBitmap(), 0, 0, null);
        finalImage.compress(Bitmap.CompressFormat.JPEG, 75, output);

        return imagefile;
    }

    /**
     * Starts a dialog for user to choose title and NSFW, then calls {@link #sendToServer()}
     * to begin server upload
     */
    public void uploadMedia()   {
        UploadDialog uploadDialog = new UploadDialog();
        uploadDialog.setUploadDialogListener(new UploadDialog.UploadDialogListener() {
            @Override
            public void onDialogPositiveClick(UploadDialog dialog) {
                // initialize server params here
                mMap.put("handle", dialog.getHandle());
                mMap.put("is_nsfw", dialog.isNsfw() ? "true" : "false");
                mMap.put("is_image", PictureEditorFragment.isImage() ? "true" : "false");
                mMap.put("user", "temp_test_id");
                mMap.put("latitude", DeviceLocator.getLatitude());
                mMap.put("longitude", DeviceLocator.getLongitude());
                // show a progress dialog to user until sent
                mProgressDialog = ProgressDialog.show(mActivity, "Please wait...", "sending", true);
                sendToServer();

            }

            @Override
            public void onDialogNegativeClick(UploadDialog dialog) {
            }
        });
        uploadDialog.show(mActivity.getFragmentManager(), "UploadDialog");
    }

    /**
     * Prepares image and executes async task to send media to server
     */
    private void sendToServer() {
        Log.i(TAG, "Sending to Server");

        File tempfile = null;

        if(PictureEditorFragment.isImage()) {
            try {
                tempfile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else  {
            // TODO handle video creation
            Toast.makeText(mActivity, "Sending video to server currently not supported", Toast.LENGTH_SHORT);
        }

        if(tempfile != null)    {
            // init media to send
            mFileToServer = tempfile;
            mMap.put("media", mFileToServer);;
        }
        // check network connection
        ConnectivityManager connMgr = (ConnectivityManager)
                mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            // network connection is good, start request
            RequestParams params = new RequestParams();
            for(String key : keys) {
                if (key != "media") params.put(key, mMap.get(key));
            }
            // for some reason media has to be sent separately
            try {
                params.put("media", mFileToServer);
            } catch(FileNotFoundException e)    {
                e.printStackTrace();
            }
            // asynchronously communicates with server
            AsyncHttpClient client = new AsyncHttpClient();
            client.post(SERVER_URL, params, new AsyncHttpResponseHandler() {

                @Override
                public void onStart() {
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    Log.d(TAG, "Upload Success " + statusCode);
                    mProgressDialog.dismiss();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                    Log.e(TAG, "Upload Failure " + statusCode);
                    mProgressDialog.dismiss();
                }

                @Override
                public void onRetry(int retryNo) {
                    // called when request is retried
                }
            });
        } else {
            Toast.makeText(mActivity, "Couldn't connect to network", Toast.LENGTH_SHORT);
            Log.e(TAG, "Couldn't connect to network");
        }
    }

    /**
     * Downloads user edited media into corresponding directory in phone.
     * Calls {@link #saveImage()} or {@link #saveVideo()} depending on whether
     * {@link PictureEditorFragment} holds an image or video
     */
    public void downloadMedia() {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(mActivity);
                mProgressDialog.setTitle("Saving...");
                mProgressDialog.setMessage("Please wait.");
                mProgressDialog.setCancelable(false);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... arg0) {
                try {
                    if (PictureEditorFragment.isImage()) {
                        saveImage();
                    } else {
                        saveVideo();
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
     * Writes image data into file system
     */
    private void saveImage()    {
        FileOutputStream output = null;
        File tempfile = null;
        try {
            tempfile = createImageFile();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.DATA, tempfile.getAbsolutePath());

            mActivity.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            // stores the image with other image media (accessible through Files > Images)
            MediaStore.Images.Media.insertImage(mActivity.getContentResolver(),
                    tempfile.getAbsolutePath(), tempfile.getName(), "No Description");
        } catch (IOException e) {
            Toast.makeText(mActivity, "Save encountered an error", Toast.LENGTH_SHORT);
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
    }

    /**
     * Writes video data into file system
     */
    private void saveVideo()    {
        // TODO handle video saving
        Toast.makeText(mActivity, "Video saving currently not supported", Toast.LENGTH_SHORT);
    }

}