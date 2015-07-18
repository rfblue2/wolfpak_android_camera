package com.wolfpak.camera;

import android.graphics.Bitmap;

import java.util.ArrayList;

/**
 * Created by Roland on 7/18/2015.
 */
public class UndoManager {

    ArrayList<Bitmap> screenStates;

    /**
     * Initializes Undo Manager
     */
    public UndoManager ()   {
        screenStates = new ArrayList<Bitmap>();
    }

    /**
     * Adds screen state to state list
     * @param b
     */
    public void addScreenState(Bitmap b) {
        screenStates.add(b);
    }

    /**
     * @return the last saved screen state
     */
    public Bitmap getLastScreenState()  {
        return screenStates.get(screenStates.size() - 1); // returns previous state
    }
    /**
     * Removes the last saved screen state and returns the one before it
     * @return the previous screen state
     */
    public Bitmap undoScreenState()  {
        screenStates.remove(screenStates.size() - 1); // removes last saved state
        return screenStates.get(screenStates.size() - 1); // returns previous state
    }

    public int getNumberOfStates()  {
        return screenStates.size();
    }
}
