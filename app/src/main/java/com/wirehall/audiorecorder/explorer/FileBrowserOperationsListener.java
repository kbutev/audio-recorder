package com.wirehall.audiorecorder.explorer;

import android.view.View;

/** Interface used to define the click listener for list item */
public interface FileBrowserOperationsListener {
  void onClick(View view, int position);
  void onDelete(int position);
}
