package com.wirehall.audiorecorder.explorer;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.model.Recording;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

public class FileListFragment extends Fragment {
  public static final String DEFAULT_STORAGE_PATH =
      FileUtils.getBaseStoragePath() + "/Audio/Recordings";
  private static final String TAG = FileListFragment.class.getName();
  private FileListFragmentListener context;
  private FileListAdapter fileListAdapter;
  private TextView empty_list_label;

  private List<Recording> recordings;

  /** @return The singleton instance of FileListFragment */
  public static FileListFragment newInstance() {
    return new FileListFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    this.context = (FileListFragmentListener) context;
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    FragmentActivity activity = getActivity();
    if (activity != null) {
      empty_list_label = activity.findViewById(R.id.tv_empty_list_message);
    }

    return inflater.inflate(R.layout.file_list_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setup();
  }

  public void setup() {
    String recordingStoragePath = FileUtils.getRecordingStoragePath(getContext());
    recordings =
            FileUtils.getAllFilesFromDirectory(
                    getContext(), recordingStoragePath, new FileExtensionFilter());

    RecyclerView recyclerView = requireActivity().findViewById(R.id.recycler_view);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
    recyclerView.setLayoutManager(linearLayoutManager);
    FileBrowserOperationsListener fileBrowserOperationsListener = new FileBrowserOperationsListener() {
      @Override
      public void onClick(View view, int position) {
        onItemClick(position);
      }

      @Override
      public void onDelete(int position) {
        onItemDelete(position);
      }
    };

    fileListAdapter = new FileListAdapter(getContext(), recordings, fileBrowserOperationsListener);
    recyclerView.setAdapter(fileListAdapter);
    updateEmptyLabel();
  }

  /** Refresh the file list view by updating the adapter associated with it */
  public void refreshAdapter() {
    Thread thread =
        new Thread() {
          @Override
          public void run() {
            try {
              FragmentActivity activity = getActivity();
              if (activity != null) {
                String recordingStoragePath = FileUtils.getRecordingStoragePath(getContext());
                final List<Recording> recordings =
                    FileUtils.getAllFilesFromDirectory(
                        getContext(), recordingStoragePath, new FileExtensionFilter());
                activity.runOnUiThread(() -> updateData(recordings));
              }
            } catch (Exception e) {
              Log.e(TAG, e.getMessage());
            }
          }
        };
    thread.start();
  }

  public void updateData(List<Recording> recordings) {
    this.recordings = recordings;
    fileListAdapter.updateData(recordings);
    updateEmptyLabel();
  }

  public void updateEmptyLabel() {
    empty_list_label.setVisibility(recordings.isEmpty() ? View.VISIBLE : View.GONE);
  }

  /** Clears any row selection */
  public void resetRowSelection() {
    fileListAdapter.resetRowSelection();
  }

  private void onItemClick(int position) {
    context.onFileItemClicked(recordings.get(position));
  }

  private void onItemDelete(int position) {
    setup();
  }

  /** Interface used to invoke the file item's click handler from activity */
  public interface FileListFragmentListener {
    void onFileItemClicked(Recording filePath);
  }

  /** Class used to filter files with .rec extension */
  static class FileExtensionFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
      return (name.endsWith(FileUtils.DEFAULT_REC_FILENAME_EXTENSION));
    }
  }
}
