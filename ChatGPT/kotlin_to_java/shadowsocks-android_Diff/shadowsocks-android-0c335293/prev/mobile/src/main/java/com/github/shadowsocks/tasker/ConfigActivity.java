package com.github.shadowsocks.tasker;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.Switch;

import com.github.shadowsocks.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;

public class ConfigActivity extends AppCompatActivity {
    private class ProfileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Profile item = null;
        private CheckedTextView text = itemView.findViewById(android.R.id.text1);

        public ProfileViewHolder(View view) {
            super(view);
            Resources.Theme theme = view.getContext().getTheme();
            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            TypedArray typedArray = theme.obtainStyledAttributes(attrs);
            view.setBackgroundResource(typedArray.getResourceId(0, 0));
            typedArray.recycle();
            itemView.setOnClickListener(this);
        }

        public void bindDefault() {
            item = null;
            text.setText(R.string.profile_default);
            text.setChecked(taskerOption.getProfileId() < 0);
        }

        public void bind(Profile item) {
            this.item = item;
            text.setText(item.getFormattedName());
            text.setChecked(taskerOption.getProfileId() == item.getId());
        }

        @Override
        public void onClick(View v) {
            taskerOption.setSwitchOn(switch.isChecked());
            Profile item = this.item;
            taskerOption.setProfileId(item != null ? item.getId() : -1);
            setResult(Activity.RESULT_OK, taskerOption.toIntent(ConfigActivity.this));
            finish();
        }
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfileViewHolder> {
        private List<Profile> profiles = ProfileManager.getAllProfiles() != null ? ProfileManager.getAllProfiles().toMutableList() : new ArrayList<>();
        private String name = "select_dialog_singlechoice_" + (Build.VERSION.SDK_INT >= 21 ? "material" : "holo");

        @Override
        public ProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(Resources.getSystem().getIdentifier(name, "layout", "android"), parent, false);
            return new ProfileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ProfileViewHolder holder, int position) {
            if (position == 0) {
                holder.bindDefault();
            } else {
                holder.bind(profiles.get(position - 1));
            }
        }

        @Override
        public int getItemCount() {
            return 1 + profiles.size();
        }
    }

    private Settings taskerOption;
    private Switch switch;
    private ProfilesAdapter profilesAdapter = new ProfilesAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            taskerOption = Settings.fromIntent(getIntent());
        } catch (Exception e) {
            finish();
            return;
        }
        setContentView(R.layout.layout_tasker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        switch = findViewById(R.id.serviceSwitch);
        switch.setChecked(taskerOption.isSwitchOn());
        RecyclerView profilesList = findViewById(R.id.list);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        profilesList.setLayoutManager(lm);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesList.setAdapter(profilesAdapter);
        if (taskerOption.getProfileId() >= 0) {
            lm.scrollToPosition(profilesAdapter.profiles.indexOfFirst(new Predicate<Profile>() {
                @Override
                public boolean test(Profile profile) {
                    return profile.getId() == taskerOption.getProfileId();
                }
            }) + 1);
        }
    }
}