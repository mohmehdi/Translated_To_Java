package com.github.shadowsocks.preference;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.BottomSheetDialog;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.shadowsocks.R;

public class BottomSheetPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private class IconListViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private int index = 0;
        private TextView text1;
        private TextView text2;
        private ImageView icon;

        public IconListViewHolder(BottomSheetDialog dialog, View view) {
            super(view);
            text1 = view.findViewById(android.R.id.text1);
            text2 = view.findViewById(android.R.id.text2);
            icon = view.findViewById(android.R.id.icon);

            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }

        public void bind(int i, boolean selected) {
            text1.setText(preference.entries[i]);
            text2.setText(preference.entryValues[i]);
            int typeface = selected ? Typeface.BOLD : Typeface.NORMAL;
            text1.setTypeface(null, typeface);
            text2.setTypeface(null, typeface);
            text2.setVisibility(preference.entryValues[i].isNotEmpty() &&
                    preference.entries[i] != preference.entryValues[i] ? View.VISIBLE : View.GONE);
            icon.setImageDrawable(preference.entryIcons.get(i));
            index = i;
        }

        @Override
        public void onClick(View p0) {
            clickedIndex = index;
            dialog.dismiss();
        }

        @Override
        public boolean onLongClick(View p0) {
            String pn = preference.entryPackageNames.get(index);
            if (pn == null) {
                return false;
            }
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pn)));
                return true;
            } catch (ActivityNotFoundException e) {
                return false;
            }
        }
    }

    private class IconListAdapter extends RecyclerView.Adapter<IconListViewHolder> {
        private BottomSheetDialog dialog;

        public IconListAdapter(BottomSheetDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public int getItemCount() {
            return preference.entries.size();
        }

        @Override
        public IconListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.icon_list_item_2, parent, false);
            return new IconListViewHolder(dialog, view);
        }

        @Override
        public void onBindViewHolder(IconListViewHolder holder, int position) {
            if (preference.selectedEntry < 0) {
                holder.bind(position);
            } else {
                switch (position) {
                    case 0:
                        holder.bind(preference.selectedEntry, true);
                        break;
                    default:
                        if (position >= preference.selectedEntry + 1 && position <= Integer.MAX_VALUE) {
                            holder.bind(position);
                        } else {
                            holder.bind(position - 1);
                        }
                        break;
                }
            }
        }
    }

    private IconListPreference preference;
    private int clickedIndex = -1;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new BottomSheetDialog(getActivity(), getTheme());
        RecyclerView recycler = new RecyclerView(getActivity());
        recycler.setHasFixedSize(true);
        recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
        recycler.setAdapter(new IconListAdapter((BottomSheetDialog) dialog));
        recycler.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(recycler);
        return dialog;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (clickedIndex >= 0 && clickedIndex != preference.selectedEntry) {
            String value = preference.entryValues.get(clickedIndex).toString();
            if (preference.callChangeListener(value)) {
                preference.value = value;
            }
        }
    }
}