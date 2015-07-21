package com.wolfpak.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Shrinks bitmaps by saving them to the file system and compressing them, then uploading
 * them to the UndoManager
 * @author Roland
 */
public class BitmapHandler implements Runnable {

    private Bitmap mBitmap;
    private Activity mActivity;

    public BitmapHandler(Bitmap bitmap, Activity activity)    {
        mBitmap = bitmap;
        mActivity = activity;
    }

    @Override
    public void run() {
        File file = new File(mActivity.getExternalFilesDir(null), "temp.jpeg");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 75, output);
            UndoManager.addScreenState(BitmapFactory.decodeFile(file.getAbsolutePath()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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
}