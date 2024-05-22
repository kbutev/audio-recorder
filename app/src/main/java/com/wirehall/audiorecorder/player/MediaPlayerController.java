package com.wirehall.audiorecorder.player;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.FileListFragment;
import com.wirehall.audiorecorder.explorer.model.Recording;
import com.wirehall.audiorecorder.visualizer.VisualizerFragment;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** This is a singleton class for controlling the media player operations */
public class MediaPlayerController {
  private static final String TAG = MediaPlayerController.class.getName();

  private static MediaPlayerController mediaPlayerController;
  private final Handler handler = new Handler(Looper.myLooper());
  private MediaPlayer mediaPlayer;
  private MediaPlayer.OnCompletionListener mPlayerOnCompletionListener;
  private Recording currentRecording = null;

  private BottomNavigationView navigationView;

  private MediaPlayerController() {
    // Private Constructor
  }

  /** @return The singleton instance of MediaPlayerController */
  public static MediaPlayerController getInstance() {
    if (mediaPlayerController == null) {
      mediaPlayerController = new MediaPlayerController();
    }
    return mediaPlayerController;
  }

  /**
   * Initialize the MediaPlayerController
   *
   * @param activity Activity required for internal operations
   */
  public void init(final AppCompatActivity activity) {
    navigationView = activity.findViewById(R.id.navigation);

    View playFragment = activity.findViewById(R.id.player_fragment);
    final TextView timerTextView = playFragment.findViewById(R.id.tv_timer);
    final SeekBar seekBar = playFragment.findViewById(R.id.sb_mp_seek_bar);
    seekBar.setEnabled(false);
    activity.runOnUiThread(
        new Runnable() {

          @Override
          public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
              if (!seekBar.isEnabled()) {
                seekBar.setEnabled(true);
              }
              seekBar.setMax(0);
              final int totalMediaDuration = mediaPlayer.getDuration();
              seekBar.setMax(totalMediaDuration);
              int currentPosition = mediaPlayer.getCurrentPosition();
              String playbackTimerString =
                  getFormattedTimeString(activity, currentPosition, totalMediaDuration);
              timerTextView.setText(playbackTimerString);
              seekBar.setProgress(currentPosition);
            }
            handler.postDelayed(this, 50);
          }
        });
    mPlayerOnCompletionListener = mediaPlayer -> onMediaHalt(activity);
    seekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            // No implementation required
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            // No implementation required
          }

          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (mediaPlayer != null && fromUser) {
              mediaPlayer.seekTo(progress);
            }
          }
        });
  }

  /**
   * Plays the audio file, also operates the seek-bar
   *
   * @param activity Activity required for internal operations
   * @param newRecording The media file
   */
  public void playPauseAudio(AppCompatActivity activity, Recording newRecording) {

    final SeekBar seekBar = activity.findViewById(R.id.sb_mp_seek_bar);
    try {
      if (currentRecording != null) {
        currentRecording.setPlaying(false);
      }
      if (mediaPlayer == null) {
        mediaPlayer = new MediaPlayer();
        currentRecording = newRecording;
      } else if (mediaPlayer.isPlaying() && newRecording.equals(currentRecording)) {
        mediaPlayer.pause();
        currentRecording = newRecording;
        newRecording.setPlaying(false);
        onPlayerStopped();
        return;
      } else if (!mediaPlayer.isPlaying()
          && mediaPlayer.getCurrentPosition() > 1
          && newRecording.equals(currentRecording)) {
        mediaPlayer.start();
        currentRecording = newRecording;
        newRecording.setPlaying(true);
        onPlayerStarted();
        return;
      } else {
        currentRecording = newRecording;
        releaseMediaPlayer();
        mediaPlayer = new MediaPlayer();
      }

      mediaPlayer.setOnCompletionListener(mPlayerOnCompletionListener);
      Log.d(TAG, "Playing audio file: " + newRecording.getPath());
      mediaPlayer.reset();
      mediaPlayer.setDataSource(newRecording.getPath());
      mediaPlayer.prepare();
      seekBar.setMax(0);
      seekBar.setMax(mediaPlayer.getDuration());
      seekBar.setEnabled(true);
      mediaPlayer.start();
      newRecording.setPlaying(true);
      setMPVisualizerView(activity);
      onPlayerStarted();


    } catch (IllegalArgumentException e) {
      Log.e(TAG, "ERROR: IllegalArgumentException: " + e.getMessage());
    } catch (IllegalStateException e) {
      Log.e(TAG, "ERROR: IllegalStateException: " + e.getMessage());
    } catch (IOException e) {
      Log.e(TAG, "ERROR: IOException: " + e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "ERROR:  " + e.getMessage());
    }
  }

  /**
   * Stops the media playback
   *
   * @param activity Activity required for internal operations
   */
  public void stopPlaying(AppCompatActivity activity) {
    releaseMediaPlayer();
    onMediaHalt(activity);
  }

  // Performs the operations required after the audio play is halted
  private void onMediaHalt(AppCompatActivity activity) {
    final TextView timerTextView = activity.findViewById(R.id.tv_timer);
    final SeekBar seekBar = activity.findViewById(R.id.sb_mp_seek_bar);

    seekBar.setMax(0);
    seekBar.setProgress(0);
    seekBar.setEnabled(false);
    timerTextView.setText("");

    if (currentRecording != null) currentRecording.setPlaying(false);

    FileListFragment fileListFragment =
        (FileListFragment)
            activity.getSupportFragmentManager().findFragmentById(R.id.list_fragment_container);
    if (fileListFragment != null) {
      fileListFragment.resetRowSelection();
    }

    onPlayerStopped();
  }

  /** Release the media player instance */
  public void releaseMediaPlayer() {
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.release();
      mediaPlayer = null;
    }
  }

  /** @return The audio session id of the media player instance */
  public int getAudioSessionId() {
    return mediaPlayer != null ? mediaPlayer.getAudioSessionId() : 0;
  }

  private void setMPVisualizerView(AppCompatActivity activity) {
    FragmentManager manager = activity.getSupportFragmentManager();
    VisualizerFragment visualizerFragment =
            (VisualizerFragment) manager.findFragmentById(R.id.visualizer_fragment_player_container);
    if (visualizerFragment != null) {
      visualizerFragment.setMPVisualizerView();
    }
  }

  private void onPlayerStarted() {
    enableNavigationBar(false);
  }

  private void onPlayerStopped() {
    enableNavigationBar(true);
  }

  private void enableNavigationBar(boolean enable) {
    Menu menu = navigationView.getMenu();
    for (int i = 0; i < menu.size(); i++) {
      menu.getItem(i).setEnabled(enable);
    }
  }

  private String getFormattedTimeString(
      Context context, int currentPosition, int totalMediaDuration) {
    long currentPositionMinutes = TimeUnit.MILLISECONDS.toMinutes(currentPosition);
    long currentPositionSeconds = TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60;
    long totalMediaDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMediaDuration);
    long totalMediaDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(totalMediaDuration) % 60;
    return context
        .getResources()
        .getString(
            R.string.duration_progress_in_min_sec_short,
            currentPositionMinutes,
            currentPositionSeconds,
            totalMediaDurationMinutes,
            totalMediaDurationSeconds);
  }
}
