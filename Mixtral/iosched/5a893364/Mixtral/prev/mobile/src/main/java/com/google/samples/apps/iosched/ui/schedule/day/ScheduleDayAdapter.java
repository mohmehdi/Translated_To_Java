

package com.google.samples.apps.iosched.ui.schedule.day;

import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.RecycledViewPool;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.samples.apps.iosched.databinding.ItemSessionBinding;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.ui.schedule.ScheduleEventListener;

public class ScheduleDayAdapter extends ListAdapter<UserSession, SessionViewHolder> {
    private final ScheduleEventListener eventListener;
    private final RecycledViewPool tagViewPool;

    public ScheduleDayAdapter(ScheduleEventListener eventListener, RecycledViewPool tagViewPool) {
        super(new SessionDiff());
        this.eventListener = eventListener;
        this.tagViewPool = tagViewPool;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.tags.setRecycledViewPool(tagViewPool);
        FlexboxLayoutManager layoutManager = (FlexboxLayoutManager) binding.tags.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setRecycleChildrenOnDetach(true);
        }
        return new SessionViewHolder(binding, eventListener);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        private final ItemSessionBinding binding;
        private final ScheduleEventListener eventListener;

        public SessionViewHolder(ItemSessionBinding binding, ScheduleEventListener eventListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.eventListener = eventListener;
        }

        public void bind(UserSession userSession) {
            binding.setSession(userSession.getSession());
            binding.setUserEvent(userSession.getUserEvent());
            binding.setEventListener(eventListener);
            binding.executePendingBindings();
        }
    }

    static class SessionDiff extends DiffUtil.ItemCallback<UserSession> {
        @Override
        public boolean areItemsTheSame(@NonNull UserSession oldItem, @NonNull UserSession newItem) {
            return oldItem.getSession().getId() == newItem.getSession().getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserSession oldItem, @NonNull UserSession newItem) {
            return oldItem.equals(newItem);
        }
    }
}