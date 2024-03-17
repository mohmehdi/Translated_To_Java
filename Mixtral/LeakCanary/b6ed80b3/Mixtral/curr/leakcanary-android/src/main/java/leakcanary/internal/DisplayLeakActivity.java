

package leakcanary.internal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
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

    public AnalyzedHeap getVisibleLeak() {
        if (leaks == null) {
            return null;
        }
        for (AnalyzedHeap leak : leaks) {
            if (leak.result.referenceKey.equals(visibleLeakRefKey)) {
                return leak;
            }
        }
        return null;
    }

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
        intent.type = "text/plain";
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
                    "com.squareup.leakcanary.fileprovider." + getApplication().getPackageName(),
                    heapDumpFile
            );
            runOnUiThread(() -> startShareIntentChooser(heapDumpUri));
        });
    }

    private void startShareIntentChooser(Uri heapDumpUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.type = "application/octet-stream";
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

    internal void updateUi() {
        if (leaks == null) {
            title = "Loading leaks...";
            return;
        }

        if (leaks.isEmpty()) {
            visibleLeakRefKey = null;
        }

        DisplayLeakResult visibleLeak = visibleLeak;
        if (visibleLeak == null) {
            visibleLeakRefKey = null;
        }

        ListView listView = findViewById(R.id.listView);
        View failureView = findViewById(R.id.failureView);

        listView.setVisibility(View.VISIBLE);
        failureView.setVisibility(View.GONE);

        if (visibleLeak != null) {
            DisplayLeakResult.LeakTrace result = visibleLeak.getResult();
            Button actionButton = findViewById(R.id.actionButton);
            Button shareButton = findViewById(R.id.shareButton);

            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.leak_canary_delete);
            actionButton.setOnClickListener(v -> deleteVisibleLeak());
            shareButton.setVisibility(View.VISIBLE);
            shareButton.setText(Html.fromHtml(getString(R.string.leak_canary_stackoverflow_share)));
            shareButton.setOnClickListener(v -> shareLeakToStackOverflow());
            invalidateOptionsMenu();
            setDisplayHomeAsUpEnabled(true);

            if (result.leakFound) {
                DisplayLeakAdapter adapter = new DisplayLeakAdapter(getResources());
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                });
                adapter.update(result.leakTrace, result.referenceKey, result.referenceName);
                long retainedHeapSize = result.retainedHeapSize;
                if (retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
                    String className = classSimpleName(result.className);
                    title = getString(R.string.leak_canary_class_has_leaked, className);
                } else {
                    String size = formatShortFileSize(this, retainedHeapSize);
                    String className = classSimpleName(result.className);
                    title = getString(R.string.leak_canary_class_has_leaked_retaining, className, size);
                }
            } else {
                listView.setVisibility(View.GONE);
                failureView.setVisibility(View.VISIBLE);
                listView.setAdapter(null);

                String failureMessage;
                if (result.failure != null) {
                    setTitle(R.string.leak_canary_analysis_failed);
                    failureMessage = getString(R.string.leak_canary_failure_report)
                            + LIBRARY_VERSION
                            + " "
                            + GIT_SHA
                            + "\n"
                            + Log.getStackTraceString(result.failure);
                } else {
                    String className = classSimpleName(result.className);
                    title = getString(R.string.leak_canary_class_no_leak, className);
                    failureMessage = getString(R.string.leak_canary_no_leak_details);
                }
                File heapDumpFile = visibleLeak.heapDump.heapDumpFile;
                String path = heapDumpFile.getAbsolutePath();
                failureMessage += "\n\n" + getString(R.string.leak_canary_download_dump, path);
                ((TextView) failureView).setText(failureMessage);
            }
        } else {
            LeakListAdapter listAdapter = (LeakListAdapter) listView.getAdapter();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            } else {
                LeakListAdapter adapter = new LeakListAdapter();
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                });
                invalidateOptionsMenu();
                title = getString(R.string.leak_canary_leak_list_title, packageName);
                setDisplayHomeAsUpEnabled(false);
                Button actionButton = findViewById(R.id.actionButton);
                actionButton.setText(R.string.leak_canary_delete_all);
                actionButton.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(DisplayLeakActivity.this);
                    builder.setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.leak_canary_delete_all)
                            .setMessage(R.string.leak_canary_delete_all_leaks_title)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteAllLeaks())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            }
            Button actionButton = findViewById(R.id.actionButton);
            actionButton.setVisibility(leaks.size == 0 ? View.GONE : View.VISIBLE);
            shareButton.setVisibility(View.GONE);
        }
    }
    private void shareLeakToStackOverflow() {
        VisibleLeak visibleLeak = visibleLeak;
        LeakInfo leakInfo = LeakCanary.getInstance().getLeakInfo(this, visibleLeak.getHeapDump(), visibleLeak.getResult(), false);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        new AsyncTask<Void, Void, Void>() {

        }.execute();

        Toast.makeText(this, R.string.leak_canary_leak_copied, Toast.LENGTH_LONG).show();

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                STACKOVERFLOW_QUESTION_URL
        ));
        startActivity(browserIntent);
    }

    private void setDisplayHomeAsUpEnabled(boolean enabled) {
        ActionBar actionBar = actionBar != null
                ? actionBar
                : return;
        actionBar.setDisplayHomeAsUpEnabled(enabled);
    }

    public class LeakListAdapter extends BaseAdapter {
        private DisplayLeakActivity displayLeakActivity;
        private List leaks;

        public LeakListAdapter(DisplayLeakActivity displayLeakActivity) {
            this.displayLeakActivity = displayLeakActivity;
        }

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
            return (long) position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View convertView = convertView;
            if (convertView == null) {
            convertView = LayoutInflater.from(displayLeakActivity)
                .inflate(R.layout.leak_canary_leak_row, parent, false);
            }
            TextView titleView = convertView.findViewById(R.id.leak_canary_row_text);
            TextView timeView = convertView.findViewById(R.id.leak_canary_row_time);
            AnalyzedHeap leak = getItem(position);

            String index = (leaks.size() - position) + ". ";

            String title;
            if (leak.getResult().getFailure() != null) {
            title = (index

                leak.getResult().getFailure().getClass().getSimpleName()
                " "
                leak.getResult().getFailure().getMessage());
            } else {
            String className =
                classSimpleName(leak.getResult().getClassName());
            if (leak.getResult().isLeakFound()) {
                long retainedHeapSize = leak.getResult().getRetainedHeapSize();
                String size;
                if (retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
                title = displayLeakActivity.getString(R.string.leak_canary_class_has_leaked, className);
                } else {
                size = formatShortFileSize(displayLeakActivity, retainedHeapSize);
                title = displayLeakActivity.getString(R.string.leak_canary_class_has_leaked_retaining, className, size);
                }
                if (leak.getResult().isExcludedLeak()) {
                title = displayLeakActivity.getString(R.string.leak_canary_excluded_row, title);
                }
                title = index + title;
            } else {
                title = index + displayLeakActivity.getString(R.string.leak_canary_class_no_leak, className);
            }
            }
            titleView.setText(title);
            String time = DateUtils.formatDateTime(displayLeakActivity, leak.getSelfLastModified(),
            DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE);
            timeView.setText(time);
            return convertView;
        }
    }
    public class LoadLeaks implements Runnable {
    private DisplayLeakActivity activityOrNull;
    private final LeakDirectoryProvider leakDirectoryProvider;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor("LoadLeaks");
    private static final List<LoadLeaks> inFlight = new ArrayList<>();

    public LoadLeaks(DisplayLeakActivity activityOrNull, LeakDirectoryProvider leakDirectoryProvider) {
        this.activityOrNull = activityOrNull;
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
        leaks.sort(new Comparator<AnalyzedHeap>() {

        });
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                inFlight.remove(LoadLeaks.this);
                if (activityOrNull != null) {
                    activityOrNull.setLeaks(leaks);
                    activityOrNull.updateUi();
                }
            }
        });
    }

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
        return (separator == -1) ? className : className.substring(separator + 1);
    }
}