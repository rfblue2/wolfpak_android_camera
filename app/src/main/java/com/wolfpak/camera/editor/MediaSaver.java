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

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handles the saving of media to the phone and to the server
 * Created by rfblue2 on 7/23/2015.
 */
public class MediaSaver {

    private static final String TAG = "MediaSaver";

    private static final String serverURL = "http://ec2-52-4-176-1.compute-1.amazonaws.com/posts/";

    private Activity mActivity;
    private ProgressDialog mProgressDialog;
    private EditableOverlay mOverlay;
    private TextureView mTextureView;

    // TODO use hashmap instead
    // parameters
    File mFileToServer = null;
    String handle = null;
    String latitude = null;
    String longitude = null;//device location
    String nsfw = null;
    String is_image = null;
    String user = null;

    /**
     * Constructor for mediasaver
     * @param activity
     * @param overlay
     * @param textureView
     */
    public MediaSaver(Activity activity, EditableOverlay overlay, TextureView textureView)    {
        mActivity = activity;
        mOverlay = overlay;
        mTextureView = textureView;
    }

    /**
     * Creates an file for an image to be stored in the pictures directory
     * @return file in pictures directory
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
     * Starts a dialog for user to choose title and NSFW, then calls {@link #sendToServer()}
     * to begin server upload
     */
    public void uploadMedia()   {
        UploadDialog uploadDialog = new UploadDialog();
        uploadDialog.setUploadDialogListener(new UploadDialog.UploadDialogListener() {
            @Override
            public void onDialogPositiveClick(UploadDialog dialog) {
                // TODO initialize all server params here
                handle = dialog.getHandle();
                nsfw = dialog.isNsfw() ? "true" : "false";
                is_image = PictureEditorFragment.isImage() ? "true" : "false";
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
    private void sendToServer()  {
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
            user = "temp_test_id";
            mFileToServer = tempfile;
            latitude = "0";
            longitude = "0";
        }
        // check network connection
        ConnectivityManager connMgr = (ConnectivityManager)
                mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {

            RequestParams params = new RequestParams();
            params.put("handle", handle);
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
            // asynchronously communicates with server
            AsyncHttpClient client = new AsyncHttpClient();
            client.post(serverURL, params, new AsyncHttpResponseHandler() {

                @Override
                public void onStart() {
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    Log.e(TAG, "Upload Success");
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

    private void saveImage()    {
        FileOutputStream output = null;
        File tempfile = null;
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

            mActivity.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            // stores the image with other image media (accessible through Files > Images)
            MediaStore.Images.Media.insertImage(mActivity.getContentResolver(),
                    tempfile.getAbsolutePath(), tempfile.getName(), "No Description");
        } catch (IOException e) {
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

    private void saveVideo()    {
        // save video
    }

    /**
     * Downloads user edited media into corresponding directory in phone
     */
    public void downloadMedia()    {

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
}
