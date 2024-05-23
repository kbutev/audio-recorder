package com.wirehall.audiorecorder.recorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.wirehall.audiorecorder.MainActivity;
import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.FileUtils;
import com.wirehall.audiorecorder.setting.SettingActivity;

import java.io.IOException;

import static com.wirehall.audiorecorder.App.CHANNEL_ID;

public class AudioRecorderLocalService extends Service {

  public static final int HIGH_QUALITY_ENCODING_BIT_RATE = 16*44100;
  public static final int HIGH_QUALITY_SAMPLING_RATE = 44100;

  public static final String EVENT_RECORDER_STATE_CHANGE = "EVENT_RECORDER_STATE_CHANGE";
  public static final String FLAG_IS_DISCARD_RECORDING = "FLAG_IS_DISCARD_RECORDING";
  public static final String KEY_RECORDING_FILE_PATH = "KEY_RECORDING_FILE_PATH";
  public static final String ACTION_START_RECORDING =
      "com.wirehall.audiorecorder.ACTION_START_RECORDING";
  public static final String ACTION_STOP_RECORDING =
      "com.wirehall.audiorecorder.ACTION_STOP_RECORDING";
  public static final String ACTION_PAUSE_RECORDING =
      "com.wirehall.audiorecorder.ACTION_PAUSE_RECORDING";
  public static final String ACTION_RESUME_RECORDING =
      "com.wirehall.audiorecorder.ACTION_RESUME_RECORDING";
  private static final String TAG = AudioRecorderLocalService.class.getName();
  private static final int SERVICE_ID = 1;
  public static MediaRecorderState mediaRecorderState = MediaRecorderState.STOPPED;
  public static MediaRecorder mediaRecorder;
  public static RecordingTime recordingTime;
  private final IBinder binder = new LocalBinder();
  private String recordingFilePath;

  @Override
  public void onCreate() {
    mediaRecorder = new MediaRecorder();
    recordingTime = new RecordingTime();
    super.onCreate();
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return super.onStartCommand(null, flags, startId);
    assert intent.getAction() != null;
    switch (intent.getAction()) {
      case ACTION_START_RECORDING:
        Log.d(TAG, "Received Start Recording Intent");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Intent stopRecordIntent = new Intent(this, AudioRecorderLocalService.class);
        stopRecordIntent.setAction(ACTION_STOP_RECORDING);
        PendingIntent stopRecordPendingIntent =
            PendingIntent.getService(this, 0, stopRecordIntent, 0);

        Notification notification =
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getBaseContext().getString(R.string.app_name))
                .setContentText(getBaseContext().getString(R.string.recording_in_progress))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(
                    R.drawable.ic_stop_white,
                    getBaseContext().getString(R.string.btn_stop_recording),
                    stopRecordPendingIntent)
                .build();

        boolean isRecording = startRecording(getBaseContext());
        if (isRecording) {
          broadcastRecorderStateChange();
          startForeground(SERVICE_ID, notification);
        }
        break;
      case ACTION_STOP_RECORDING:
        Log.i(TAG, "Received Stop Recording Intent");
        try {
          stopRecording();
          broadcastRecorderStateChange();
        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }
        stopForeground(true);
        stopSelf();
        break;
      case ACTION_PAUSE_RECORDING:
        Log.i(TAG, "Received Pause Foreground Intent");
        try {
          pauseRecording();
          broadcastRecorderStateChange();
        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }
        break;
      case ACTION_RESUME_RECORDING:
        Log.i(TAG, "Received Resume Foreground Intent");
        try {
          resumeRecording();
          broadcastRecorderStateChange();
        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }
        break;
      default:
        break;
    }

    return START_STICKY;
  }

  private void broadcastRecorderStateChange() {
    // broadcast state change so that the activity is notified
    // and it can make UI changes accordingly
    Intent recorderStateChangeIntent = new Intent(EVENT_RECORDER_STATE_CHANGE);
    recorderStateChangeIntent.putExtra(KEY_RECORDING_FILE_PATH, recordingFilePath);
    LocalBroadcastManager.getInstance(this).sendBroadcast(recorderStateChangeIntent);
  }

  private boolean startRecording(Context context) {
    try {
      String recordingStoragePath = FileUtils.getRecordingStoragePath(context);
      recordingFilePath = recordingStoragePath + '/' + FileUtils.generateDefaultFileName();
      Log.d(TAG, "Recording Path: " + recordingFilePath);

      mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
      String audioQualityNormal = context.getResources().getString(R.string.audio_quality_normal);
      String audioQualityPref =
          sharedPref.getString(SettingActivity.KEY_PREF_LIST_AUDIO_QUALITY, audioQualityNormal);
      mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
      mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      mediaRecorder.setAudioEncodingBitRate(HIGH_QUALITY_ENCODING_BIT_RATE);
      mediaRecorder.setAudioSamplingRate(HIGH_QUALITY_SAMPLING_RATE);
      mediaRecorder.setOutputFile(recordingFilePath);
      mediaRecorder.prepare();
      mediaRecorder.start();
      recordingTime.setRecStartTime(SystemClock.uptimeMillis());
      mediaRecorderState = MediaRecorderState.RECORDING;
      Toast.makeText(
              context, context.getString(R.string.message_recording_started), Toast.LENGTH_SHORT)
          .show();
    } catch (IOException e) {
      Toast.makeText(
              context,
              context.getString(R.string.message_recording_fail_io_error),
              Toast.LENGTH_LONG)
          .show();
      Log.e(TAG, "ERROR: IOException: " + e.getMessage());
      mediaRecorder.reset();
      return false;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      mediaRecorder.reset();
      return false;
    }
    return true;
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private void pauseRecording() {
    mediaRecorder.pause();
    mediaRecorderState = MediaRecorderState.PAUSED;
  }

  @TargetApi(Build.VERSION_CODES.N)
  @RequiresApi(api = Build.VERSION_CODES.N)
  private void resumeRecording() {
    mediaRecorder.resume();
    recordingTime.autoSetRecPauseTime();
    mediaRecorderState = MediaRecorderState.RESUMED;
  }

  private void stopRecording() {
    if (mediaRecorder != null) {
      try {
        mediaRecorder.stop();
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
      mediaRecorder.reset();
    }

    mediaRecorderState = MediaRecorderState.STOPPED;

    // Reset Timer
    recordingTime.reset();
  }

  @Override
  public void onDestroy() {
    try {
      if (mediaRecorder != null) {
        if (!mediaRecorderState.isStopped()) {
          mediaRecorder.stop();
          mediaRecorder.reset();
        }
        mediaRecorder.release();
        mediaRecorder = null;
      }
      mediaRecorderState = MediaRecorderState.STOPPED;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public class LocalBinder extends Binder {
    public AudioRecorderLocalService getService() {
      // Return this instance of this service so clients can call public methods
      return AudioRecorderLocalService.this;
    }
  }
}
