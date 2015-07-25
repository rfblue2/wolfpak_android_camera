package com.wolfpak.camera.editor;

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
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Fragment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.wolfpak.camera.R;
import com.wolfpak.camera.editor.colorpicker.ColorPickerView;
import com.wolfpak.camera.preview.CameraFragment;

import java.nio.ByteBuffer;

/**
 * A fragment that displays a captured image or loops video for the user to edit
 * @author Roland Fong
 */
public class PictureEditorFragment extends Fragment
        implements View.OnClickListener, View.OnTouchListener {

    private static final String TAG = "PictureEditorFragment";

    private static TextureView mTextureView;
    private static boolean isImage;

    private static String mVideoPath;

    private MediaSaver mMediaSaver;

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
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    /**
     * Creates a new instance of fragment
     * @return A new instance of fragment PictureEditorFragment.
     */
    public static PictureEditorFragment newInstance() {
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
        // set up texture view
        mTextureView = (TextureView) view.findViewById(R.id.edit_texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        mTextureView.setOnTouchListener(this);
        // set up bitmap overlay (drawing and text)
        mOverlay = (EditableOverlay) view.findViewById(R.id.overlay);
        mOverlay.init((TextOverlay) view.findViewById(R.id.text_overlay));
        // set up buttons
        view.findViewById(R.id.btn_back).setOnClickListener(this);
        view.findViewById(R.id.btn_download).setOnClickListener(this);
        view.findViewById(R.id.btn_upload).setOnClickListener(this);
        view.findViewById(R.id.btn_undo).setOnClickListener(this);
        view.findViewById(R.id.btn_text).setOnClickListener(this);
        view.findViewById(R.id.btn_blur).setOnClickListener(this);
        mDrawButton = (ImageButton) view.findViewById(R.id.btn_draw);
        mDrawButton.setOnClickListener(this);
        // set up blurring scripts
        mBlurScript = RenderScript.create(getActivity());
        mIntrinsicScript = ScriptIntrinsicBlur.create(mBlurScript, Element.U8_4(mBlurScript));
        // set up color picker
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
                    mOverlay.getTextOverlay().setmTextColor(newColor);
                }
            }
        });
        mColorPicker.setVisibility(View.GONE);

        mMediaSaver = new MediaSaver(getActivity(), mOverlay, mTextureView);

        if(CameraFragment.getImage() != null) {
            isImage = true;
        } else if(CameraFragment.getVideoPath() != null)    {
            isImage = false;
        } else {
            Log.e(TAG, "Unknown File Type");
            // This should never happen but if it does, just go back to camera
            Toast.makeText(getActivity(), "Sorry, editor encountered an error", Toast.LENGTH_SHORT);
            getFragmentManager().popBackStack();
        }
    }

    /**
     * @return if image is being handled
     */
    public static boolean isImage()    {
        return isImage;
    }

    /**
     * @return mVideoPath the path of the video
     */
    public static String getVideoPath() {
        return mVideoPath;
    }

    /**
     * Displays media onto textureview
     */
    private void displayMedia() {
        if(isImage) {
            if(CameraFragment.getImage() != null) {
                Canvas canvas = mTextureView.lockCanvas();
                // put image info from camera into buffer
                ByteBuffer buffer = CameraFragment.getImage().getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                CameraFragment.getImage().close(); // don't forget to close the image buffer!
                CameraFragment.setImage(null); // so we know to skip initing image upon resume
                // resize horizontally oriented images
                if (src.getWidth() > src.getHeight()) {
                    // transformation matrix that scales and rotates
                    Matrix matrix = new Matrix();
                    if(CameraFragment.getFace() == CameraCharacteristics.LENS_FACING_FRONT)  {
                        matrix.setScale(-1, 1);
                    }
                    matrix.postRotate(90);
                    matrix.postScale(((float) canvas.getWidth()) / src.getHeight(),
                            ((float) canvas.getHeight()) / src.getWidth());
                    Bitmap resizedBitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                    canvas.drawBitmap(resizedBitmap, 0, 0, null);
                    UndoManager.addScreenState(resizedBitmap); // initial state
                }

                mTextureView.unlockCanvasAndPost(canvas);
            } else { // device likely resumed,  so restore previous session
                Canvas c = mTextureView.lockCanvas();
                c.drawBitmap(UndoManager.getLastScreenState(), 0, 0, null);
                mTextureView.unlockCanvasAndPost(c);
            }
        } else  {
            if(CameraFragment.getVideoPath() != null) {
                mVideoPath = CameraFragment.getVideoPath();
                UndoManager.addScreenState(Bitmap.createBitmap(mOverlay.getBitmap()));// initial state
                CameraFragment.setVideoPath(null); // so we know to skip initing upon resume
            } else  { // device likely resumed, so restore previous session
                mOverlay.setBitmap(UndoManager.getLastScreenState());
            }
            // play the video
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
     * @return the bitmap of ONLY the textureview
     */
    public static Bitmap getBitmap()   {
        return mTextureView.getBitmap();
    }

    /**
     * Sets the bitmap on TextureView
     * @param bitmap
     */
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

    /**
     * Blurs image at specified coordinates by producing a circular bitmap and blitting it
     * to the textureview
     * @param action
     * @param x
     * @param y
     */
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
                break;
            case MotionEvent.ACTION_CANCEL:
            default: break;
        }

    }

    /**
     * Closes the media player
     */
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
                UndoManager.clearStates();
                getFragmentManager().popBackStack();
                break;
            case R.id.btn_download:
                mMediaSaver.downloadMedia();
                break;
            case R.id.btn_upload:
                mMediaSaver.uploadMedia();
                // go back to camera after uploading
                UndoManager.clearStates();
                getFragmentManager().popBackStack();
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
                    mOverlay.getTextOverlay().nextState();// go to default state
                } else  {// if text is selected
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

    // Required empty public constructor
    public PictureEditorFragment() {}

}
