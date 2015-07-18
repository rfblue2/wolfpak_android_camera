package com.wolfpak.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;

/**
 * An overlay for drawing above textureview
 * Created by Roland on 7/15/2015.
 */
public class EditableOverlay extends View {

    private static final String TAG = "EditableOverlay";

    /*private static final String EXTRA_EVENT_LIST = "event_list";
    private static final String EXTRA_STATE_LIST = "state_list";
    private static final String EXTRA_STATE = "instance_state";
    private ArrayList<MotionEvent> eventList = new ArrayList<MotionEvent>(100);
    private ArrayList<Integer> stateList = new ArrayList<Integer>(100);*/

    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private Paint mBitmapPaint;
    private Paint mPaint;
    private int mColor;

    private int mState;
    public static final int STATE_IDLE = 0;
    public static final int STATE_DRAW = 1;
    public static final int STATE_BLUR = 2;
    public static final int STATE_TEXT = 3;

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private TextOverlay mTextOverlay;
    private ScaleGestureDetector mScaleDetector;
    private float currentFontSize;
    private final ScaleGestureDetector.OnScaleGestureListener mOnScaleListener =
            new ScaleGestureDetector.OnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    Log.d(TAG, "Scale Begin");
                    currentFontSize = mTextOverlay.getTextSize();
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    Log.d(TAG, "Scale Detected");
                    if(mTextOverlay.getState() == TextOverlay.TEXT_STATE_FREE ||
                            mTextOverlay.getState() == TextOverlay.TEXT_STATE_VERTICAL) {
                        Log.d(TAG, "Scaling by " + detector.getScaleFactor());
                        mTextOverlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * detector.getScaleFactor());
                        mTextOverlay.invalidate();
                        /*mTextOverlay.setWidth((int) (mTextOverlay.getWidth() * detector.getScaleFactor()));
                        mTextOverlay.setHeight((int) (mTextOverlay.getHeight() * detector.getScaleFactor()));*/
                    }
                    return false;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {

                }
            };

    private RotationGestureDetector mRotationDetector;
    private RotationGestureDetector.OnRotationGestureListener mOnRotationListener = new RotationGestureDetector.OnRotationGestureListener() {
        @Override
        public void OnRotation(RotationGestureDetector rotationDetector) {
            Log.d(TAG, "Rotation by angle " + rotationDetector.getAngle());

            if(mTextOverlay.getState() == TextOverlay.TEXT_STATE_FREE ||
                    mTextOverlay.getState() == TextOverlay.TEXT_STATE_VERTICAL) {
                mTextOverlay.setPivotX(mTextOverlay.getWidth() / 2);
                mTextOverlay.setPivotY(mTextOverlay.getHeight() / 2);
                mTextOverlay.setRotation(-1 * rotationDetector.getAngle());
            }
        }
    };

    public EditableOverlay(Context context)  {
        this(context, null);
    }

    public EditableOverlay(Context context, AttributeSet attrs)  {
        this(context, attrs, 0);
    }

    public EditableOverlay(Context context, AttributeSet attrs, int defStyle)    {
        super(context, attrs, defStyle);
    }

    /**
     * Initializes the paint components for the overlay
     */
    public void init(TextOverlay textOverlay) {
        setSaveEnabled(true);
        mState = STATE_IDLE;

        mPath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);

        mColor = 0xFF000000;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(9);

        mTextOverlay = textOverlay;
        mTextOverlay.init();

        mScaleDetector = new ScaleGestureDetector(getContext(), mOnScaleListener);
        mRotationDetector = new RotationGestureDetector(mOnRotationListener);
    }

    public void setBitmap(Bitmap b) {
        mBitmap = b;
    }

    /**
     * @return the overlay bitmap
     */
    public Bitmap getBitmap()
    {
        if(mTextOverlay.getState() != TextOverlay.TEXT_STATE_HIDDEN) {
            Canvas c = new Canvas(mBitmap);
            c.drawBitmap(mTextOverlay.getBitmap(), mTextOverlay.getX(), mTextOverlay.getY(), null);
        }
        return mBitmap;
    }

    /**
     * @return the overlay bitmap without text
     */
    public Bitmap getBitmapWithoutText()    {
        return mBitmap;
    }

    /**
     * Sets the state of the overlay
     * @param state
     */
    public void setState(int state)  {
        mState = state;
    }

    /**
     * @return the state of the overlay
     */
    public int getState()   {
        return mState;
    }

    /**
     * Sets the color of drawing tool
     * @param color
     */
    public void setColor(int color)   {
        mColor = color;
        mPaint.setColor(color);
    }

    /**
     * @return the drawing tool color
     */
    public int getColor() {
        return mColor;
    }

    public TextOverlay getTextOverlay() {
        return mTextOverlay;
    }


    public void clearBitmap()   {
        mBitmap.eraseColor(Color.argb(0, 0, 0, 0));
    }

    private void touch_start(float x, float y, int state) {
        switch(state)   {
            case STATE_DRAW:
                mPath.reset();
                mPath.moveTo(x, y);
                mX = x;
                mY = y;
                break;
            case STATE_TEXT:
                break;
        }
    }

    private void touch_move(float x, float y, int state) {
        switch(state)   {
            case STATE_DRAW:
                float dx = Math.abs(x - mX);
                float dy = Math.abs(y - mY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
                    mX = x;
                    mY = y;
                }
                break;
            case STATE_TEXT:
                break;
        }
    }

    private void touch_up(int state) {
        switch(state)   {
            case STATE_DRAW:
                mPath.lineTo(mX, mY);
                // commit the path to our offscreen
                mCanvas.drawPath(mPath, mPaint);
                // kill this so we don't double draw
                mPath.reset();
                if(PictureEditorFragment.isImage()) { // if image, save overlay and textureview
                    Bitmap screen = Bitmap.createBitmap(PictureEditorFragment.getBitmap());
                    Canvas c = new Canvas(screen);
                    c.drawBitmap(mBitmap, 0, 0, null);
                    PictureEditorFragment.getUndoManager().addScreenState(screen);
                } else  { // if not image, only save overlay
                    PictureEditorFragment.getUndoManager().addScreenState(mBitmap);
                }
                break;
            case STATE_TEXT:
                break;
        }
    }

    private boolean performTouchEvent(MotionEvent event, int state) {
        if(state == STATE_BLUR) return false;// blur is not handled on overlay
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(x, y, state);
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(x, y, state);
                break;
            case MotionEvent.ACTION_UP:
                touch_up(state);
                break;
        }
        invalidate();
        /*eventList.add(MotionEvent.obtain(event));
        stateList.add(state);*/
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mState == STATE_IDLE) return false;// don't even do anything

        mScaleDetector.onTouchEvent(event);
        mRotationDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                return performTouchEvent(event, mState);
        }
        return false; // touch event not consumed - pass this on to textureview
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);

        canvas.drawPath(mPath, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        System.out.println("w "+w+" h "+h+" oldw "+oldw+" oldh "+oldh);
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
    }

    /*@Override
    public Parcelable onSaveInstanceState()
    {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_STATE, super.onSaveInstanceState());
        bundle.putParcelableArrayList(EXTRA_EVENT_LIST, eventList);
        bundle.putIntegerArrayList(EXTRA_STATE_LIST, stateList);

        return bundle;
    }*/

    /*@Override
    public void onRestoreInstanceState(Parcelable state)
    {
        if (state instanceof Bundle)
        {
            Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable(EXTRA_STATE));
            eventList = bundle.getParcelableArrayList(EXTRA_EVENT_LIST);
            stateList = bundle.getIntegerArrayList(EXTRA_STATE_LIST);
            if (eventList == null) {
                eventList = new ArrayList<MotionEvent>(100);
            }
            for(int i = 0; i < eventList.size(); i++)   {
                performTouchEvent(eventList.get(i), stateList.get(i));
            }
            return;
        }
        super.onRestoreInstanceState(state);
    }*/

    public static class RotationGestureDetector {
        private static final int INVALID_POINTER_ID = -1;
        private float fX, fY, sX, sY;
        private int ptrID1, ptrID2;
        private float mAngle;

        private OnRotationGestureListener mListener;

        public float getAngle() {
            return mAngle;
        }

        public RotationGestureDetector(OnRotationGestureListener listener){
            mListener = listener;
            ptrID1 = INVALID_POINTER_ID;
            ptrID2 = INVALID_POINTER_ID;
        }

        public boolean onTouchEvent(MotionEvent event){
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ptrID1 = event.getPointerId(event.getActionIndex());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    ptrID2 = event.getPointerId(event.getActionIndex());
                    sX = event.getX(event.findPointerIndex(ptrID1));
                    sY = event.getY(event.findPointerIndex(ptrID1));
                    fX = event.getX(event.findPointerIndex(ptrID2));
                    fY = event.getY(event.findPointerIndex(ptrID2));
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID){
                        float nfX, nfY, nsX, nsY;
                        nsX = event.getX(event.findPointerIndex(ptrID1));
                        nsY = event.getY(event.findPointerIndex(ptrID1));
                        nfX = event.getX(event.findPointerIndex(ptrID2));
                        nfY = event.getY(event.findPointerIndex(ptrID2));

                        mAngle = angleBetweenLines(fX, fY, sX, sY, nfX, nfY, nsX, nsY);

                        if (mListener != null) {
                            mListener.OnRotation(this);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    ptrID1 = INVALID_POINTER_ID;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    ptrID2 = INVALID_POINTER_ID;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    ptrID1 = INVALID_POINTER_ID;
                    ptrID2 = INVALID_POINTER_ID;
                    break;
            }
            return true;
        }

        private float angleBetweenLines (float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY)
        {
            float angle1 = (float) Math.atan2( (fY - sY), (fX - sX) );
            float angle2 = (float) Math.atan2( (nfY - nsY), (nfX - nsX) );

            float angle = ((float)Math.toDegrees(angle1 - angle2)) % 360;
            if (angle < -180.f) angle += 360.0f;
            if (angle > 180.f) angle -= 360.0f;
            return angle;
        }

        public static interface OnRotationGestureListener {
            public void OnRotation(RotationGestureDetector rotationDetector);
        }
    }
}
