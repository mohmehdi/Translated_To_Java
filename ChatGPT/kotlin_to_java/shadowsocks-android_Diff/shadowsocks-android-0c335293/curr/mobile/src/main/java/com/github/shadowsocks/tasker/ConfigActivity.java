package com.github.shadowsocks.tasker;

import android.app.Activity;
import android.content.res.Resources;
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
import com.github.shadowsocks.utils.resolveResourceId;

public class ConfigActivity extends AppCompatActivity {
    private class ProfileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Profile item;
        private CheckedTextView text;

        public ProfileViewHolder(View view) {
            super(view);
            text = itemView.findViewById(android.R.id.text1);
            view.setBackgroundResource(theme.resolveResourceId(android.R.attr.selectableItemBackground));
            itemView.setOnClickListener(this);
        }

        public void bindDefault() {
            item = null;
            text.setText(R.string.profile_default);
            text.setChecked(taskerOption.profileId < 0);
        }

        public void bind(Profile item) {
            this.item = item;
            text.setText(item.formattedName);
            text.setChecked(taskerOption.profileId == item.id);
        }

        @Override
        public void onClick(View v) {
            taskerOption.switchOn = switch.isChecked();
            Profile item = this.item;
            taskerOption.profileId = item != null ? item.id : -1;
            setResult(Activity.RESULT_OK, taskerOption.toIntent(ConfigActivity.this));
            finish();
        }
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfileViewHolder> {
        private final List<Profile> profiles;

        public ProfilesAdapter() {
            profiles = ProfileManager.getAllProfiles() != null ? ProfileManager.getAllProfiles().toMutableList() : new ArrayList<>();
        }

        @Override
        public ProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(Resources.getSystem()
                    .getIdentifier("select_dialog_singlechoice_material", "layout", "android"), parent, false);
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
    private ProfilesAdapter profilesAdapter;

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
        switch.setChecked(taskerOption.switchOn);
        RecyclerView profilesList = findViewById(R.id.list);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        profilesList.setLayoutManager(lm);
        profilesList.setItemAnimator(new DefaultItemAnimator());
        profilesAdapter = new ProfilesAdapter();
        profilesList.setAdapter(profilesAdapter);
        if (taskerOption.profileId >= 0) {
            lm.scrollToPosition(profilesAdapter.profiles.indexOfFirst(new Predicate<Profile>() {
                @Override
                public boolean test(Profile profile) {
                    return profile.id == taskerOption.profileId;
                }
            }) + 1);
        }
    }
}