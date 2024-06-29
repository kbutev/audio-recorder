package com.wirehall.audiorecorder.explorer;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.model.Recording;

import java.io.File;
import java.util.logging.Logger;

public class FilenameInputDialog extends Dialog implements OnClickListener {
  private static final String TAG = FilenameInputDialog.class.getName();

  private final String filePath;
  private final String initialName;
  private Recording recording;

  private DialogInterface.OnDismissListener onSuccessDismissListener;

  public FilenameInputDialog(Context context, String filePath, String initialName) {
    super(context);
    this.filePath = filePath;
    this.initialName = initialName;
  }

  public void setOnSuccessDismissListener(DialogInterface.OnDismissListener listener) {
    this.onSuccessDismissListener = listener;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setCanceledOnTouchOutside(false);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.filename_input_dialog);
    Button cancelButton = findViewById(R.id.btn_filename_input_dialog_cancel);
    cancelButton.setOnClickListener(this);
    Button okButton = findViewById(R.id.btn_filename_input_dialog_ok);

    final EditText editText = findViewById(R.id.et_filename_input_dialog);

    DialogInterface self = this;
    Handler main = new Handler(Looper.getMainLooper());

    okButton.setOnClickListener(
        v -> {
          AsyncTask.execute(() -> {
            try {
              String newRecordingName = editText.getText().toString();
              if (newRecordingName.trim().isEmpty()) {
                editText.setHintTextColor(Color.RED);
                return;
              }
              File sourceFile = new File(filePath);
              File targetFile =
                      new File(
                              sourceFile.getParent(),
                              newRecordingName + FileUtils.DEFAULT_REC_FILENAME_EXTENSION);
              if (sourceFile.exists() && sourceFile.renameTo(targetFile)) {
                recording = new Recording();
                recording.setName(newRecordingName);
                recording.setPath(targetFile.getPath());
              } else {
                Log.e(TAG, "Problem renaming file: " + filePath + " to: " + newRecordingName);
              }
            } catch (Exception e) {
              Log.e(TAG, e.getMessage());
            }

            main.post(() -> {
              dismiss();
              if (onSuccessDismissListener != null) {
                onSuccessDismissListener.onDismiss(self);
              }
            });
          });
        });

    cancelButton.setOnClickListener(
            v -> {
              AsyncTask.execute(() -> {
                try {
                  FileUtils.deleteFile(filePath);
                } catch (Exception e) {
                  Log.e(TAG, e.getMessage());
                }

                main.post(() -> dismiss());
              });
            });

    editText.setText(initialName);
  }

  public Recording getRenamedRecording() {
    return recording;
  }

  @Override
  public void onClick(View v) {
    dismiss();
  }
}
