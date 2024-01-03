package com.github.shadowsocks;

import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class ScannerActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    private ZXingScannerView scannerView;

    private void navigateUp() {
        android.content.Intent intent = getParentActivityIntent();
        if (shouldUpRecreateTask(intent) || isTaskRoot())
            TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities();
        else finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_scanner);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getTitle());
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> navigateUp());
        scannerView = findViewById(R.id.scanner);
        if (Build.VERSION.SDK_INT >= 25) getSystemService(ShortcutManager.class).reportShortcutUsed("scan");
    }

    @Override
    protected void onResume() {
        super.onResume();
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            scannerView.setResultHandler(this);
            scannerView.startCamera();
        } else ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                MY_PERMISSIONS_REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA)
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scannerView.setResultHandler(this);
                scannerView.startCamera();
            } else {
                Toast.makeText(this, R.string.add_profile_scanner_permission_required, Toast.LENGTH_SHORT).show();
                finish();
            }
        else super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scannerView.stopCamera();
    }

    @Override
    public void handleResult(Result rawResult) {
        Profile.findAll(rawResult != null ? rawResult.getText() : null).forEach(ProfileManager::createProfile);
        navigateUp();
    }
}