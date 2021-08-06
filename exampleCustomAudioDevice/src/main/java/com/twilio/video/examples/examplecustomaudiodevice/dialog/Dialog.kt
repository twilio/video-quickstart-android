package com.twilio.video.examples.examplecustomaudiodevice.dialog

import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.widget.EditText

object Dialog {
    fun createConnectDialog(
        participantEditText: EditText,
        callParticipantsClickListener: DialogInterface.OnClickListener?,
        cancelClickListener: DialogInterface.OnClickListener?,
        context: Context
    ): AlertDialog {
        val alertDialogBuilder = AlertDialog.Builder(context)
        alertDialogBuilder.setIcon(android.R.drawable.sym_call_outgoing)
        alertDialogBuilder.setTitle("Connect to a room")
        alertDialogBuilder.setPositiveButton("Connect", callParticipantsClickListener)
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener)
        alertDialogBuilder.setCancelable(false)
        setRoomNameFieldInDialog(participantEditText, alertDialogBuilder)
        return alertDialogBuilder.create()
    }

    private fun setRoomNameFieldInDialog(
        roomNameEditText: EditText,
        alertDialogBuilder: AlertDialog.Builder
    ) {
        roomNameEditText.hint = "room name"
        alertDialogBuilder.setView(roomNameEditText)
    }
}
