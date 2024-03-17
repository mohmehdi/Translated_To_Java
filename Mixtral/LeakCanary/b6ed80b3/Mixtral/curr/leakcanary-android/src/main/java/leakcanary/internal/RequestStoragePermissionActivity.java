

package leakcanary.internal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

@TargetApi(Build.VERSION_CODES.M)
public class RequestStoragePermissionActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState == null) {
      if (hasStoragePermission()) {
        finish();
        return;
      }
      String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
      ActivityCompat.requestPermissions(this, permissions, 42);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (!hasStoragePermission()) {
      Toast.makeText(getApplicationContext(), R.string.leak_canary_permission_not_granted, Toast.LENGTH_LONG).show();
    }
    finish();
  }

  @Override
  public void finish() {
    super.finish();
    overridePendingTransition(0, 0);
  }

  private boolean hasStoragePermission() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }

  public static PendingIntent createPendingIntent(Context context) {
    LeakCanaryInternals.setEnabledBlocking(context, RequestStoragePermissionActivity.class, true);
    Intent intent = new Intent(context, RequestStoragePermissionActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}