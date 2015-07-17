package com.wolfpak.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.wolfpak.camera.colorpicker.ColorPickerView;

import java.util.ArrayList;
import java.util.List;

/**
 * An overlay for drawing above textureview
 * Created by Roland on 7/15/2015.
 */
public class EditableOverlay extends View {

    private static final String TAG = "EditableOverlay";

    private static final String EXTRA_EVENT_LIST = "event_list";
    private static final String EXTRA_STATE_LIST = "state_list";
    private static final String EXTRA_STATE = "instance_state";
    private ArrayList<MotionEvent> eventList = new ArrayList<MotionEvent>(100);
    private ArrayList<Integer> stateList = new ArrayList<Integer>(100);

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
        eventList.add(MotionEvent.obtain(event));
        stateList.add(state);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mState == STATE_IDLE) return false;// don't even do anything

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

    @Override
    public Parcelable onSaveInstanceState()
    {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_STATE, super.onSaveInstanceState());
        bundle.putParcelableArrayList(EXTRA_EVENT_LIST, eventList);
        bundle.putIntegerArrayList(EXTRA_STATE_LIST, stateList);

        return bundle;
    }

    @Override
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
    }
}
