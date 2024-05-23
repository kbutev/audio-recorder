package com.wirehall.audiorecorder.explorer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.model.Recording;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.wirehall.audiorecorder.setting.SettingActivity.KEY_PREF_CONFIRM_DELETE;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {
  public static final String INTENT_AUDIO_TYPE = "audio/*";
  private static final String TAG = FileListAdapter.class.getName();
  private final List<Recording> recordings;
  private final Context context;
  private final FileBrowserOperationsListener fileBrowserOperationsListener;
  private int selectedRowPosition = RecyclerView.NO_POSITION;

  FileListAdapter(
      Context context,
      List<Recording> recordings,
      FileBrowserOperationsListener fileBrowserOperationsListener) {
    this.context = context;
    this.recordings = recordings;
    this.fileBrowserOperationsListener = fileBrowserOperationsListener;
  }

  public List<Recording> getRecordings() {
    return new ArrayList<>(recordings);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
    LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
    View view = layoutInflater.inflate(R.layout.file_row_layout, parent, false);
    return new ViewHolder(view, fileBrowserOperationsListener);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
    // Note: Do not use the passed position parameter. Instead use viewHolder.getAdapterPosition()
    // as the position sometimes has the wrong value
    viewHolder.itemView.setSelected(
        this.selectedRowPosition == viewHolder.getBindingAdapterPosition());

    Recording recording = recordings.get(viewHolder.getBindingAdapterPosition());
    viewHolder.fileNameTextView.setText(recording.getName());
    viewHolder.fileSizeTextView.setText(recording.getSizeInString());
    viewHolder.fileDateModifiedTextView.setText(recording.getModifiedDateInString());
    viewHolder.fileDurationTextView.setText(recording.getDurationShortInString());

    if (recording.isPlaying()) {
      viewHolder.filePlayPauseButton.setImageResource(R.drawable.ic_pause_white);
    } else {
      viewHolder.filePlayPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
    }
  }

  @Override
  public int getItemCount() {
    return this.recordings.size();
  }

  /**
   * Uses the list passed as a argument to this method for showing in file list view
   *
   * @param newRecordings The list of recordings to use in file list view
   */
  public void updateData(List<Recording> newRecordings) {
    Log.d(TAG, "Update the file list");
    resetRowSelection();
    recordings.clear();
    recordings.addAll(newRecordings);
    notifyDataSetChanged();
  }

  /** Clears any row selection */
  public void resetRowSelection() {
    Log.d(TAG, "Clear the file row selection");
    int oldSelectedRowPosition = this.selectedRowPosition;
    this.selectedRowPosition = RecyclerView.NO_POSITION;
    if (oldSelectedRowPosition > -1) notifyItemChanged(oldSelectedRowPosition);
  }

  /**
   * Used to keep the reference to list row elements to fetch faster. i.e. to avoid time consuming
   * findViewById
   */
  public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    private final TextView fileNameTextView;
    private final TextView fileSizeTextView;
    private final TextView fileDateModifiedTextView;
    private final TextView fileDurationTextView;
    private final ImageButton filePlayPauseButton;
    private final ImageButton fileOptionsMenuButton;
    private final FileBrowserOperationsListener fileBrowserOperationsListener;

    private ViewHolder(
        @NonNull View itemView, FileBrowserOperationsListener fileBrowserOperationsListener) {
      super(itemView);
      this.fileBrowserOperationsListener = fileBrowserOperationsListener;
      RelativeLayout fileInfoAreaView = itemView.findViewById(R.id.rl_file_info_area);
      fileInfoAreaView.setOnClickListener(this);

      // Note: you can also use the setOnClickListener on below child views
      // and perform the actions in onClick method using the instanceof check

      fileNameTextView = itemView.findViewById(R.id.tv_filename);
      fileSizeTextView = itemView.findViewById(R.id.tv_file_size);
      fileDateModifiedTextView = itemView.findViewById(R.id.tv_file_date_modified);
      fileDurationTextView = itemView.findViewById(R.id.tv_file_duration);

      filePlayPauseButton = itemView.findViewById(R.id.ib_file_play_pause);
      filePlayPauseButton.setOnClickListener(this);

      final String fileMenuOptionDelete =
          context.getResources().getString(R.string.file_menu_option_delete);
      final String fileMenuOptionInfo =
          context.getResources().getString(R.string.file_menu_option_info);
      final String fileMenuOptionRename =
          context.getResources().getString(R.string.file_menu_option_rename);
      final String fileMenuOptionShare =
          context.getResources().getString(R.string.file_menu_option_share);
      final String deleteDialogTitle =
          context.getResources().getString(R.string.dialog_delete_title);
      fileOptionsMenuButton = itemView.findViewById(R.id.ib_file_menu);
      fileOptionsMenuButton.setOnClickListener(
          v -> {
            Log.d(TAG, "Clicked on the file row options menu");
            final ListPopupWindow window;
            window = new ListPopupWindow(context);
            final List<String> data = new ArrayList<>();
            data.add(fileMenuOptionDelete);
            data.add(fileMenuOptionInfo);
            data.add(fileMenuOptionRename);
            data.add(fileMenuOptionShare);
            ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, R.layout.file_menu_item_layout, data);
            /* use ur custom layout which has only TextView along with style required*/
            window.setAdapter(adapter);
            window.setHeight(WRAP_CONTENT);
            window.setWidth(WRAP_CONTENT);
            window.setModal(false);
            window.setAnchorView(fileOptionsMenuButton); /*it will be the overflow view of yours*/
            window.setContentWidth(
                FileUtils.measureContentWidth(
                    adapter, context)); /* set width based on ur requirement*/
            window.setOnItemClickListener(
                (parent, view, position, id) -> {
                  String option = data.get(position);
                  final int adapterPosition = getBindingAdapterPosition();
                  if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                  }

                  if (option.equals(fileMenuOptionDelete)) {
                    handleDeleteClick(deleteDialogTitle, window, adapterPosition);
                  } else if (option.equals(fileMenuOptionInfo)) {
                    handleInfoClick(window, adapterPosition);
                  } else if (option.equals(fileMenuOptionRename)) {
                    handleRenameClick(window, adapterPosition);
                  } else if (option.equals(fileMenuOptionShare)) {
                    handleShareClick(window, adapterPosition);
                  }
                });
            window.show();
          });
    }

    private void handleDeleteClick(
        String deleteDialogTitle, ListPopupWindow window, int adapterPosition) {
      Log.d(TAG, "Clicked on the file row delete option menu");

      final String deleteDialogMessage =
          context
              .getResources()
              .getString(R.string.dialog_delete_message, recordings.get(adapterPosition).getPath());
      SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
      boolean confirmDelete = sharedPref.getBoolean(KEY_PREF_CONFIRM_DELETE, true);
      if (confirmDelete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder
            .setTitle(deleteDialogTitle)
            .setMessage(deleteDialogMessage)
            .setIcon(R.drawable.ic_warning_black)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteFile(adapterPosition))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
      } else {
        deleteFile(adapterPosition);
      }
      window.dismiss();
    }

    private void handleInfoClick(ListPopupWindow window, int adapterPosition) {
      Log.d(TAG, "Clicked on the file row info option menu");

      FileInformationDialog fileInformationDialog =
          new FileInformationDialog(context, recordings.get(adapterPosition));
      window.dismiss();
      fileInformationDialog.show();
    }

    private void handleShareClick(ListPopupWindow window, int adapterPosition) {
      Log.d(TAG, "Clicked on the file row share option menu");

      Uri uri =
          FileProvider.getUriForFile(
              context,
              "com.wirehall.fileprovider",
              new File(recordings.get(adapterPosition).getPath()));
      Intent share = new Intent(Intent.ACTION_SEND);
      share.setType(INTENT_AUDIO_TYPE);
      share.putExtra(Intent.EXTRA_STREAM, uri);
      share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      window.dismiss();
      String shareRec = context.getResources().getString(R.string.share_recording);
      context.startActivity(Intent.createChooser(share, shareRec));
    }

    private void handleRenameClick(ListPopupWindow window, int adapterPosition) {
      Log.d(TAG, "Clicked on the file row rename option menu");

      final Recording sourceRecording = recordings.get(adapterPosition);
      final FilenameInputDialog filenameInputDialog =
          new FilenameInputDialog(context, sourceRecording.getPath());
      filenameInputDialog.setOnDismissListener(
          dialog -> {
            Recording renamedRecording = filenameInputDialog.getRenamedRecording();
            if (renamedRecording != null) {
              sourceRecording.setName(renamedRecording.getName());
              sourceRecording.setPath(renamedRecording.getPath());
              notifyItemChanged(adapterPosition);
            }
          });
      window.dismiss();
      filenameInputDialog.show();
    }

    private void refreshRowSelection(int selectedRowPosition) {
      int oldSelectedRowPosition = FileListAdapter.this.selectedRowPosition;
      FileListAdapter.this.selectedRowPosition = selectedRowPosition;
      if (oldSelectedRowPosition > -1) notifyItemChanged(oldSelectedRowPosition);
      notifyItemChanged(selectedRowPosition);
    }

    private void deleteFile(int adapterPosition) {
      FileUtils.deleteFile(recordings.get(adapterPosition).getPath());
      recordings.remove(adapterPosition);
      if (adapterPosition < selectedRowPosition) {
        selectedRowPosition--;
      } else if (adapterPosition == selectedRowPosition) {
        selectedRowPosition = RecyclerView.NO_POSITION;
      }
      notifyItemRemoved(adapterPosition);

      fileBrowserOperationsListener.onDelete(adapterPosition);
    }

    @Override
    public void onClick(View view) {
      int position = getBindingAdapterPosition();
      refreshRowSelection(position);
      fileBrowserOperationsListener.onClick(view, position);
    }
  }
}
