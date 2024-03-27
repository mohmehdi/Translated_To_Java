
package leakcanary.internal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.squareup.leakcanary.R;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.M;
import static android.widget.Toast.LENGTH_LONG;

@TargetApi(M)
public class RequestStoragePermissionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            if (hasStoragePermission()) {
                finish();
                return;
            }
            String[] permissions = {WRITE_EXTERNAL_STORAGE};
            requestPermissions(permissions, 42);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (!hasStoragePermission()) {
            Toast.makeText(getApplicationContext(), R.string.leak_canary_permission_not_granted, LENGTH_LONG)
                    .show();
        }
        finish();
    }

    @Override
    public void finish() {
        overridePendingTransition(0, 0);
        super.finish();
    }

    private boolean hasStoragePermission() {
        return checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
    }

    public static PendingIntent createPendingIntent(Context context) {
        LeakCanaryInternals.setEnabledBlocking(
                context, RequestStoragePermissionActivity.class, true
        );
        Intent intent = new Intent(context, RequestStoragePermissionActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 1, intent, FLAG_UPDATE_CURRENT);
    }
}