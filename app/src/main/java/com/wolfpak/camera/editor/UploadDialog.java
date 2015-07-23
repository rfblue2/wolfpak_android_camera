package com.wolfpak.camera.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.wolfpak.camera.R;

/**
 * Dialog for prompting user upload details before sending to server
 * @author Roland Fong
 */
public class UploadDialog extends DialogFragment {

    private String handle;
    private boolean isNsfw;
    private EditText title;
    private CheckBox nsfw;

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface UploadDialogListener {
        public void onDialogPositiveClick(UploadDialog dialog);
        public void onDialogNegativeClick(UploadDialog dialog);
    }

    // Use this instance of the interface to deliver action events
    private UploadDialogListener mListener;

    public void setUploadDialogListener(UploadDialogListener uploadDialogListener)  {
        mListener = uploadDialogListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_upload, null);
        title = (EditText) view.findViewById(R.id.txt_title);
        nsfw = (CheckBox) view.findViewById(R.id.cb_nsfw);

        // Inflate and set the layout for the dialog
        builder.setView(view)
                .setPositiveButton(R.string.upload, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mListener.onDialogPositiveClick(UploadDialog.this);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getDialog().cancel();
                        mListener.onDialogNegativeClick(UploadDialog.this);
                    }
                });
        return builder.create();
    }

    /**
     * @return isNsfw
     */
    public boolean isNsfw() {
        isNsfw = nsfw.isChecked();
        return isNsfw;
    }

    /**
     * Sets nsfw
     * @param isNsfw
     */
    public void setNsfw(boolean isNsfw) {
        this.isNsfw = isNsfw;
    }

    /**
     * @return the title
     */
    public String getHandle() {
        handle = title.getText().toString();
        return handle;
    }

    /**
     * Sets the title
     * @param handle
     */
    public void setHandle(String handle) {
        this.handle = handle;
    }
}