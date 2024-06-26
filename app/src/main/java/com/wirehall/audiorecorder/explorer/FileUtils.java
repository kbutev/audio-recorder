package com.wirehall.audiorecorder.explorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.wirehall.audiorecorder.MainActivity;
import com.wirehall.audiorecorder.R;
import com.wirehall.audiorecorder.explorer.model.Recording;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FileUtils {
  public static final String DEFAULT_REC_FILENAME_EXTENSION = ".m4a";
  private static final String TAG = FileUtils.class.getName();
  private static final String DEFAULT_REC_FILENAME_FORMAT = "yyyy.MM.dd HH-mm";

  private FileUtils() {
    throw new IllegalStateException("Utility class");
  }

  /** @return The current time string in "yyyy-MM-dd-HH-mm-ss" format */
  public static String generateDateString() {
    SimpleDateFormat df = new SimpleDateFormat(DEFAULT_REC_FILENAME_FORMAT, Locale.getDefault());
    return df.format(Calendar.getInstance().getTime());
  }

  public static String generateDefaultFileName() {
    return generateDateString().concat(DEFAULT_REC_FILENAME_EXTENSION);
  }

  /**
   * @param context Required for internal use
   * @param path Files are scanned from this specified path. Note: It is not a recursive
   * @param filenameFilter Used to filter the file matching the filter criteria
   * @return List of files from specified path which are matching the filter passed
   */
  @NonNull
  public static List<Recording> getAllFilesFromDirectory(
      Context context, String path, FilenameFilter filenameFilter) {
    List<Recording> recordings = new ArrayList<>();
    File directory = new File(path);
    if (!directory.exists()) {
      boolean dirCreated = directory.mkdirs();
      Log.d(TAG, "Directory created: " + dirCreated);
    }
    File[] files = directory.listFiles(filenameFilter);

    if (files == null) {
      // Means pathname does not denote a directory, or if an I/O error occurs.
      // Or could be due to missing storage permissions
      Log.e(TAG, "Problem accessing path: " + path);
      return recordings;
    }
    Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

    for (File file : files) {
      try {
        // Do not use instantiate MediaMetadataRetriever using try-with-resources
        // Just call release method manually
        // Since this class is updated with AutoClosable implementation in api 10
        // If instantiated using try-with-resources it fails on older apis
        // https://stackoverflow.com/questions/63631916/android-try-with-resources-no-method-found-close

        @SuppressWarnings("java:S2095")
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        Recording rec = new Recording();
        rec.setName(getFilenameWithoutExt(file.getName()));
        rec.setPath(file.getPath());
        rec.setSize(file.length());
        rec.setSizeInString(humanReadableByteCount(file.length(), true));
        rec.setModifiedDateMilliSec(file.lastModified());
        rec.setModifiedDateInString(humanReadableDate(file.lastModified()));
        mmr.setDataSource(file.getPath());
        long duration =
            Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        rec.setDuration(duration);
        rec.setDurationDetailedInString(humanReadableDurationDetailed(context, duration));
        rec.setDurationShortInString(humanReadableDurationShort(context, duration));
        recordings.add(rec);
        mmr.release();
      } catch (Exception e) {
        Log.e(TAG, "Error scanning file: " + e.getMessage());
      }
    }

    return recordings;
  }

  private static String getFilenameWithoutExt(String filename) {
    return filename.replaceFirst("[.][^.]+$", "");
  }

  /**
   * @param bytes file size in bytes
   * @param si The SI prefixes are standardized for use in the International System of Units (SI)
   * @return String returns the human readable file size
   *     <p>BITS SI BINARY
   *     <p>0: 0 B 0 B 27: 27 B 27 B 999: 999 B 999 B 1000: 1.0 kB 1000 B 1023: 1.0 kB 1023 B 1024:
   *     1.0 kB 1.0 KiB 1728: 1.7 kB 1.7 KiB 110592: 110.6 kB 108.0 KiB 7077888: 7.1 MB 6.8 MiB
   *     452984832: 453.0 MB 432.0 MiB 28991029248: 29.0 GB 27.0 GiB 1855425871872: 1.9 TB 1.7 TiB
   *     9223372036854775807: 9.2 EB 8.0 EiB (Long.MAX_VALUE)
   */
  public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  /**
   * @param date The date to be formatted to string
   * @return The date in "dd-MM-yyyy, hh:mm aa" format
   */
  public static String humanReadableDate(Date date) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy, hh:mm aa", Locale.getDefault());
    return dateFormat.format(date);
  }

  /**
   * @param timestamp The timestamp to be formatted to string
   * @return The date in "dd-MM-yyyy, hh:mm aa" format
   */
  public static String humanReadableDate(long timestamp) {
    Date date = new Date(timestamp);
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy, hh:mm aa", Locale.getDefault());
    return dateFormat.format(date);
  }

  /**
   * @param duration The total duration in long value
   * @return The duration of media file. The format is "%d min, %d sec" if minutes are available, if
   *     not then "%d sec" is the format
   */
  public static String humanReadableDurationDetailed(Context context, long duration) {
    long hours = TimeUnit.MILLISECONDS.toHours(duration);
    long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
    long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);

    if (seconds < 60) {
      return context.getResources().getString(R.string.duration_in_sec_long, seconds);
    }

    seconds -= TimeUnit.MINUTES.toSeconds(minutes);

    if (hours < 1) {
      return context.getResources().getString(R.string.duration_in_min_sec_long, minutes, seconds);
    }

    minutes -= TimeUnit.HOURS.toMinutes(hours);
    return context.getResources().getString(R.string.duration_in_hour_min_sec_long, hours, minutes, seconds);
  }

  /**
   * @param duration The total duration in long value
   * @return The duration of media file. The format is "%d min, %d sec" if minutes are available, if
   *     not then "%d sec" is the format
   */
  public static String humanReadableDurationShort(Context context, long duration) {
    long hours = TimeUnit.MILLISECONDS.toHours(duration);
    long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
    long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);

    if (seconds < 60) {
      return context.getResources().getString(R.string.duration_in_min_sec_short, 0, seconds);
    }

    seconds -= TimeUnit.MINUTES.toSeconds(minutes);

    if (hours < 1) {
      return context.getResources().getString(R.string.duration_in_min_sec_short, minutes, seconds);
    }

    minutes -= TimeUnit.HOURS.toMinutes(hours);
    return context.getResources().getString(R.string.duration_in_hour_min_sec_short, hours, minutes, seconds);
  }

  public static int measureContentWidth(final Adapter adapter, Context context) {
    ViewGroup measureParent = null;
    int maxWidth = 0;
    View itemView = null;
    int itemType = 0;

    final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    final int count = adapter.getCount();
    for (int i = 0; i < count; i++) {
      final int positionType = adapter.getItemViewType(i);
      if (positionType != itemType) {
        itemType = positionType;
        itemView = null;
      }

      if (measureParent == null) {
        measureParent = new FrameLayout(context);
      }

      itemView = adapter.getView(i, itemView, measureParent);
      itemView.measure(widthMeasureSpec, heightMeasureSpec);

      final int itemWidth = itemView.getMeasuredWidth();

      if (itemWidth > maxWidth) {
        maxWidth = itemWidth;
      }
    }

    return maxWidth;
  }

  public static void deleteFile(String filePath) {
    try {
      File file = new File(filePath);
      boolean deleted = file.delete();
      Log.d(TAG, "File deleted: " + deleted);
    } catch (Exception e) {
      Log.e(TAG, "Error deleting file: " + e.getMessage());
    }
  }

  public static String getRecordingStoragePath(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getString(
        MainActivity.KEY_PREF_RECORDING_STORAGE_PATH, FileListFragment.DEFAULT_STORAGE_PATH);
  }

  public static String getBaseStoragePath() {
    return getExternalStorageDirectory().getAbsolutePath();
  }

  public static String getBaseStorageName() {
    return getExternalStorageDirectory().getName();
  }

  private static File getExternalStorageDirectory() {
    return Environment.getExternalStorageDirectory();
  }

  public static String removeExtention(String filePath) {
    File f = new File(filePath);

    if (f.isDirectory()) { return f.getName(); }
    String name = f.getName();

    if (!name.contains(".")) { return name; }

    return name.substring(0, name.lastIndexOf('.'));
  }
}
