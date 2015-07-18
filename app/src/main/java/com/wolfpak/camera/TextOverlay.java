package com.wolfpak.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;

/**
 * Overlay for drawing text, also manages text states
 * Created by Roland on 7/17/2015.
 */
public class TextOverlay extends EditText {

    private final static String TAG = "TextOverlay";

    private int mState;
    /**
     * Text is hidden from user and cannot be edited
     */
    public static final int TEXT_STATE_HIDDEN = 0;
    /**
     * Text displayed on bar and editable by user.  Allows for
     * vertical movement
     */
    public static final int TEXT_STATE_DEFAULT = 1;
    /**
     * Text is centered on the screen with only vertical movement,
     * but without a bar.  Can enlarge or rotate text.  Tapping on
     * text brings up color picker to change color
     */
    public static final int TEXT_STATE_VERTICAL = 2;
    /**
     * Same as TEXT_STATE_VERTICAL except that text can move anywhere
     * on the screen
     */
    public static final int TEXT_STATE_FREE = 3;

    private boolean canEdit;

    Context context = null;

    RelativeLayout.LayoutParams params;

    public TextOverlay(Context context)  {
        this(context, null);
    }

    public TextOverlay(Context context, AttributeSet attrs)  {
        this(context, attrs, 0);
    }

    public TextOverlay(Context context, AttributeSet attrs, int defStyle)    {
        super(context, attrs, defStyle);
        this.context = context;
    }

    /**
     * Initialize view
     */
    public void init()  {
        setState(TEXT_STATE_HIDDEN);
        params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        setLayoutParams(params);
        setDrawingCacheEnabled(true);
        canEdit = false;
    }

    /**
     * @return a bitmap of the text's current appearance
     */
    public Bitmap getBitmap()   {
        // TODO rotate it with view rotation
        Bitmap b = getDrawingCache();
        Matrix rotator = new Matrix();
        rotator.postRotate(getRotation());
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), rotator, true);
    }

    public void setEditable(boolean edit)   {
        canEdit = edit;
    }

    public boolean isEditable() {
        return canEdit;
    }

    /**
     * Set the text overlay state
     * @param state
     */
    public void setState(int state) {
        switch(state)   {
            case TEXT_STATE_HIDDEN:
                setVisibility(View.GONE);
                break;
            case TEXT_STATE_DEFAULT:
                setVisibility(View.VISIBLE);
                requestFocus();
                params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                setLayoutParams(params);
                setBackgroundResource(R.drawable.text_bar);
                break;
            case TEXT_STATE_VERTICAL:
                setVisibility(View.VISIBLE);
                setBackground(null);
                params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                setLayoutParams(params);
                break;
            case TEXT_STATE_FREE:
                setVisibility(View.VISIBLE);
                break;
        }
        mState = state;
    }

    /**
     * @return state
     */
    public int getState()   {
        return mState;
    }

    /**
     * Advances the state
     * @return the new state
     */
    public int nextState()  {
        switch(mState)  {
            case TEXT_STATE_HIDDEN:
                setState(TEXT_STATE_DEFAULT);
                return TEXT_STATE_DEFAULT;
            case TEXT_STATE_DEFAULT:
                setState(TEXT_STATE_VERTICAL);
                return TEXT_STATE_VERTICAL;
            case TEXT_STATE_VERTICAL:
                setState(TEXT_STATE_FREE);
                return TEXT_STATE_FREE;
            case TEXT_STATE_FREE:
                setState(TEXT_STATE_HIDDEN);
                return TEXT_STATE_HIDDEN;
            default:
                return TEXT_STATE_HIDDEN;
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if(focused && (mState == TEXT_STATE_VERTICAL || mState == TEXT_STATE_FREE)) {
            PictureEditorFragment.getColorPicker().setVisibility(View.VISIBLE);
        } else  {
            PictureEditorFragment.getColorPicker().setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(canEdit) {
            float mx = event.getRawX();
            float my = event.getRawY();
            clearFocus();
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (mState == TEXT_STATE_DEFAULT || mState == TEXT_STATE_VERTICAL) {
                        setY(my - getHeight() / 2);
                    } else if (mState == TEXT_STATE_FREE) {
                        setX(mx - getWidth() / 2);
                        setY(my - getHeight() / 2);
                    }
            }
        }
        return super.onTouchEvent(event);
    }
}
