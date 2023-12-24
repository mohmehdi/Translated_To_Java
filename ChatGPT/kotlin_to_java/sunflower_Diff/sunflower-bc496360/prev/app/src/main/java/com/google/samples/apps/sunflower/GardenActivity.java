package com.google.samples.apps.sunflower;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.databinding.DataBindingUtil;
import com.google.samples.apps.sunflower.databinding.ActivityGardenBinding;

public class GardenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ActivityGardenBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_garden);
    }
}