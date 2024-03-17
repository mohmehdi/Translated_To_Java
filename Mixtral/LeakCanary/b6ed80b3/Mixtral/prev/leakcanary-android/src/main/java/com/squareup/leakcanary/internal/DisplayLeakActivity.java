

package com.squareup.leakcanary.internal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.text.format.DateUtils.FORMAT_SHOW_DATE;
import android.text.format.DateUtils.FORMAT_SHOW_TIME;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.GONE;
import android.view.View.VISIBLE;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.squareup.leakcanary.AnalysisResult;
import com.squareup.leakcanary.AnalyzedHeap;
import com.squareup.leakcanary.BuildConfig;
import com.squareup.leakcanary.CanaryLog;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.LeakDirectoryProvider;
import com.squareup.leakcanary.R;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DisplayLeakActivity extends Activity {

    private List<AnalyzedHeap> leaks;
    private String visibleLeakRefKey;

    private ListView listView;
    private TextView failureView;
    private Button actionButton;
    private Button shareButton;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            visibleLeakRefKey = savedInstanceState.getString("visibleLeakRefKey");
        } else {
            Intent intent = getIntent();
            if (intent.hasExtra(SHOW_LEAK_EXTRA)) {
                visibleLeakRefKey = intent.getStringExtra(SHOW_LEAK_EXTRA);
            }
        }

        leaks = (List<AnalyzedHeap>) getLastNonConfigurationInstance();

        setContentView(R.layout.leak_canary_display_leak);

        listView = findViewById(R.id.leak_canary_display_leak_list);
        failureView = findViewById(R.id.leak_canary_display_leak_failure);
        actionButton = findViewById(R.id.leak_canary_action);
        shareButton = findViewById(R.id.leak_canary_share);

        updateUi();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return leaks;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("visibleLeakRefKey", visibleLeakRefKey);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LoadLeaks.load(this, getLeakDirectoryProvider(this));
    }

    @Override
    public void setTheme(int resid) {
        if (resid != R.style.leak_canary_LeakCanary_Base) {
            return;
        }
        super.setTheme(resid);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LoadLeaks.forgetActivity();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        AnalyzedHeap visibleLeak = getVisibleLeak();
        if (visibleLeak != null) {
            menu.add(R.string.leak_canary_share_leak)
                    .setOnMenuItemClickListener(item -> {
                        shareLeak();
                        return true;
                    });
            if (visibleLeak.heapDumpFileExists) {
                menu.add(R.string.leak_canary_share_heap_dump)
                        .setOnMenuItemClickListener(item -> {
                            shareHeapDump();
                            return true;
                        });
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            visibleLeakRefKey = null;
            updateUi();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (visibleLeakRefKey != null) {
            visibleLeakRefKey = null;
            updateUi();
        } else {
            super.onBackPressed();
        }
    }

    private void shareLeak() {
        AnalyzedHeap visibleLeak = getVisibleLeak();
        String leakInfo = LeakCanary.leakInfo(this, visibleLeak.heapDump, visibleLeak.result, true);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, leakInfo);
        startActivity(Intent.createChooser(intent, getString(R.string.leak_canary_share_with)));
    }

    private void shareHeapDump() {
        AnalyzedHeap visibleLeak = getVisibleLeak();
        File heapDumpFile = visibleLeak.heapDump.heapDumpFile;
        AsyncTask.execute(() -> {
            heapDumpFile.setReadable(true, false);
            Uri heapDumpUri = FileProvider.getUriForFile(
                    getBaseContext(),
                    "com.squareup.leakcanary.fileprovider." + getPackageName(),
                    heapDumpFile
            );
            runOnUiThread(() -> startShareIntentChooser(heapDumpUri));
        });
    }

    private void startShareIntentChooser(Uri heapDumpUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, heapDumpUri);
        startActivity(Intent.createChooser(intent, getString(R.string.leak_canary_share_with)));
    }

    private void deleteVisibleLeak() {
        AnalyzedHeap visibleLeak = getVisibleLeak();
        AsyncTask.execute(() -> {
            File heapDumpFile = visibleLeak.heapDump.heapDumpFile;
            File resultFile = visibleLeak.selfFile;
            boolean resultDeleted = resultFile.delete();
            if (!resultDeleted) {
                CanaryLog.d("Could not delete result file %s", resultFile.getPath());
            }
            boolean heapDumpDeleted = heapDumpFile.delete();
            if (!heapDumpDeleted) {
                CanaryLog.d("Could not delete heap dump file %s", heapDumpFile.getPath());
            }
        });
        visibleLeakRefKey = null;
        if (leaks != null) {
            leaks.remove(visibleLeak);
        }
        updateUi();
    }

    private void deleteAllLeaks() {
        LeakDirectoryProvider leakDirectoryProvider = getLeakDirectoryProvider(this);
        AsyncTask.execute(() -> leakDirectoryProvider.clearLeakDirectory());
        leaks = new ArrayList<>();
        updateUi();
    }

    private void updateUi() {
        if (leaks == null) {
            setTitle("Loading leaks...");
            return;
        }
        if (leaks.isEmpty()) {
            visibleLeakRefKey = null;
        }

        AnalyzedHeap visibleLeak = getVisibleLeak();
        if (visibleLeak == null) {
            visibleLeakRefKey = null;
        }

        BaseAdapter listAdapter = listView.getAdapter();

        listView.setVisibility(VISIBLE);
        failureView.setVisibility(GONE);

        if (visibleLeak != null) {
            AnalysisResult result = visibleLeak.result;
            actionButton.setVisibility(VISIBLE);
            actionButton.setText(R.string.leak_canary_delete);
            actionButton.setOnClickListener(view -> deleteVisibleLeak());
            shareButton.setVisibility(VISIBLE);
            shareButton.setText(getString(R.string.leak_canary_stackoverflow_share));
            shareButton.setOnClickListener(view -> shareLeakToStackOverflow());
            invalidateOptionsMenu();
            setDisplayHomeAsUpEnabled(true);

            if (result.leakFound) {
                DisplayLeakAdapter adapter = new DisplayLeakAdapter(getResources());
                listView.setAdapter(adapter);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    adapter.toggleRow(position);
                });
                adapter.update(result.leakTrace, result.referenceKey, result.referenceName);
                if (result.retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
                    String className = classSimpleName(result.className);
                    setTitle(getString(R.string.leak_canary_class_has_leaked, className));
                } else {
                    long size = result.retainedHeapSize;
                    String className = classSimpleName(result.className);
                    setTitle(getString(R.string.leak_canary_class_has_leaked_retaining, className, Formatter.formatShortFileSize(this, size)));
                }
            } else {
                listView.setVisibility(GONE);
                failureView.setVisibility(VISIBLE);
                listView.setAdapter(null);

                StringBuilder failureMessage = new StringBuilder();
                if (result.failure != null) {
                    setTitle(R.string.leak_canary_analysis_failed);
                    failureMessage.append(getString(R.string.leak_canary_failure_report))
                            .append(BuildConfig.LIBRARY_VERSION)
                            .append(" ")
                            .append(BuildConfig.GIT_SHA)
                            .append("\n")
                            .append(Log.getStackTraceString(result.failure));
                } else {
                    String className = classSimpleName(result.className);
                    setTitle(getString(R.string.leak_canary_class_no_leak, className));
                    failureMessage.append(getString(R.string.leak_canary_no_leak_details));
                }
                String path = visibleLeak.heapDump.heapDumpFile.getAbsolutePath();
                failureMessage.append("\n\n").append(getString(R.string.leak_canary_download_dump, path));
                failureView.setText(failureMessage.toString());
            }
        } else {
            if (listAdapter instanceof LeakListAdapter) {
                ((LeakListAdapter) listAdapter).notifyDataSetChanged();
            } else {
                LeakListAdapter adapter = new LeakListAdapter();
                listView.setAdapter(adapter);
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    visibleLeakRefKey = leaks.get(position).result.referenceKey;
                    updateUi();
                });
                invalidateOptionsMenu();
                setTitle(getString(R.string.leak_canary_leak_list_title, getPackageName()));
                setDisplayHomeAsUpEnabled(false);
                actionButton.setText(R.string.leak_canary_delete_all);
                actionButton.setOnClickListener(view -> deleteAllLeaks());
            }
            actionButton.setVisibility(leaks.size == 0 ? GONE : VISIBLE);
            shareButton.setVisibility(GONE);
        }
    }

    private void shareLeakToStackOverflow() {
        AnalyzedHeap visibleLeak = getVisibleLeak();
        String leakInfo = LeakCanary.leakInfo(this, visibleLeak.heapDump, visibleLeak.result, false);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        AsyncTask.execute(() -> {
            ClipData clip = ClipData.newPlainText(
                    getString(R.string.leak_canary_leak_clipdata_label),
                    "\n" + leakInfo + ""
            );
            clipboard.setPrimaryClip(clip);
        });
        Toast.makeText(this, R.string.leak_canary_leak_copied, Toast.LENGTH_LONG).show();
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(STACKOVERFLOW_QUESTION_URL));
        startActivity(browserIntent);
    }

    private void setDisplayHomeAsUpEnabled(boolean enabled) {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }

    class LeakListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return leaks.size();
        }

        @Override
        public AnalyzedHeap getItem(int position) {
            return leaks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(DisplayLeakActivity.this)
                        .inflate(R.layout.leak_canary_leak_row, parent, false);
            }
            TextView titleView = convertView.findViewById(R.id.leak_canary_row_text);
            TextView timeView = convertView.findViewById(R.id.leak_canary_row_time);
            AnalyzedHeap leak = getItem(position);

            String index = (leaks.size() - position) + ". ";

            String title;
            if (leak.result.failure != null) {
                title = index + leak.result.failure.getClass().getSimpleName() + " " + leak.result.failure.getMessage();
            } else {
                String className = classSimpleName(leak.result.className);
                if (leak.result.leakFound) {
                    title = index + (leak.result.retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED ?
                            getString(R.string.leak_canary_class_has_leaked, className) :
                            getString(R.string.leak_canary_class_has_leaked_retaining, className, Formatter.formatShortFileSize(DisplayLeakActivity.this, leak.result.retainedHeapSize)));
                    if (leak.result.excludedLeak) {
                        title = getString(R.string.leak_canary_excluded_row, title);
                    }
                } else {
                    title = index + getString(R.string.leak_canary_class_no_leak, className);
                }
            }
            titleView.setText(title);
            String time = DateUtils.formatDateTime(DisplayLeakActivity.this, leak.selfLastModified,
                    FORMAT_SHOW_TIME | FORMAT_SHOW_DATE);
            timeView.setText(time);
            return convertView;
        }
    }

    static class LoadLeaks implements Runnable {

        private DisplayLeakActivity activityOrNull;
        private LeakDirectoryProvider leakDirectoryProvider;
        private Handler mainHandler = new Handler(Looper.getMainLooper());

        public static List<LoadLeaks> inFlight = new ArrayList<>();
        private static Executor backgroundExecutor = Executors.newSingleThreadExecutor();

        public static void load(DisplayLeakActivity activity, LeakDirectoryProvider leakDirectoryProvider) {
            LoadLeaks loadLeaks = new LoadLeaks(activity, leakDirectoryProvider);
            inFlight.add(loadLeaks);
            backgroundExecutor.execute(loadLeaks);
        }

        public static void forgetActivity() {
            for (LoadLeaks loadLeaks : inFlight) {
                loadLeaks.activityOrNull = null;
            }
            inFlight.clear();
        }

        public LoadLeaks(DisplayLeakActivity activity, LeakDirectoryProvider leakDirectoryProvider) {
            this.activityOrNull = activity;
            this.leakDirectoryProvider = leakDirectoryProvider;
        }

        @Override
        public void run() {
            List<AnalyzedHeap> leaks = new ArrayList<>();
            File[] files = leakDirectoryProvider.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".result");
                }
            });
            for (File resultFile : files) {
                AnalyzedHeap leak = AnalyzedHeap.load(resultFile);
                if (leak != null) {
                    leaks.add(leak);
                }
            }
            leaks.sort(Comparator.comparingLong(o -> o.selfFile.lastModified()));
            mainHandler.post(() -> {
                inFlight.remove(this);
                if (activityOrNull != null) {
                    activityOrNull.leaks = leaks;
                    activityOrNull.updateUi();
                }
            });
        }
    }
    private static final String SHOW_LEAK_EXTRA = "show_latest";
    private static final String STACKOVERFLOW_QUESTION_URL =
            "http://stackoverflow.com/questions/64326274/how-to-translate-kotlin-companion-object-into-java";

    public static PendingIntent createPendingIntent(Context context) {
        return createPendingIntent(context, null);
    }

    public static PendingIntent createPendingIntent(Context context, String referenceKey) {
        setEnabledBlocking(context, DisplayLeakActivity.class, true);
        Intent intent = new Intent(context, DisplayLeakActivity.class);
        intent.putExtra(SHOW_LEAK_EXTRA, referenceKey);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public static String classSimpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator == -1 ? className : className.substring(separator + 1);
    }
}