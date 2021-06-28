package com.twilio.video.quickstart.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.twilio.video.quickstart.R;

public class Dialog {

    public static AlertDialog createConnectDialog(EditText participantEditText,
                                                  DialogInterface.OnClickListener callParticipantsClickListener,
                                                  DialogInterface.OnClickListener cancelClickListener,
                                                  Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

        alertDialogBuilder.setIcon(R.drawable.ic_video_call_white_24dp);
        alertDialogBuilder.setTitle("Connect to a room");
        alertDialogBuilder.setPositiveButton("Connect", callParticipantsClickListener);
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);

        setRoomNameFieldInDialog(participantEditText, alertDialogBuilder);

        return alertDialogBuilder.create();
    }

    private static void setRoomNameFieldInDialog(EditText roomNameEditText,
                                                 AlertDialog.Builder alertDialogBuilder) {
        roomNameEditText.setHint("room name");
        alertDialogBuilder.setView(roomNameEditText);
    }

}
