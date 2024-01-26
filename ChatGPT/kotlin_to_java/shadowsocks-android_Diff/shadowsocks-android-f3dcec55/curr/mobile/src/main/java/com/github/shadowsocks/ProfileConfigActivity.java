package com.github.shadowsocks;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.github.shadowsocks.plugin.PluginContract;
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment;
import com.github.shadowsocks.plugin.fragment.Empty;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.widget.ListHolderListener;

public class ProfileConfigActivity extends AppCompatActivity {
    private static class UnsavedChangesDialogFragment extends AlertDialogFragment<Empty, Empty> {
        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            builder.setTitle(R.string.unsaved_changes_prompt)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(android.R.string.cancel, null);
        }
    }

    private ProfileConfigFragment child;
    private OnBackPressedCallback unsavedChangesHandler;
    private ActivityResultLauncher<Intent> pluginHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_profile_config);
        ListHolderListener.setup(this);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_navigation_close);
        unsavedChangesHandler = new OnBackPressedCallback(DataStore.dirty) {
            @Override
            public void handleOnBackPressed() {
                new UnsavedChangesDialogFragment().apply {
                    key();
                }.show(getSupportFragmentManager(), null);
            }
        };
        getSupportFragmentManager().addOnBackPressedCallback(this, unsavedChangesHandler);
        child = (ProfileConfigFragment) getSupportFragmentManager().findFragmentById(R.id.content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        unsavedChangesHandler.setEnabled(DataStore.dirty);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (!super.onSupportNavigateUp()) {
            finish();
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.profile_config_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return child.onOptionsItemSelected(item);
    }

    private void handlePluginHelpResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            new AlertDialog.Builder(this)
                    .setTitle("?")
                    .setMessage(data.getCharSequenceExtra(PluginContract.EXTRA_HELP_MESSAGE))
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == pluginHelp.getRequestCode()) {
            handlePluginHelpResult(resultCode, data);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        pluginHelp = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::handlePluginHelpResult);
    }
}