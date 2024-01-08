package com.google.samples.apps.iosched.ui.schedule.day;

import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.samples.apps.iosched.databinding.ItemSessionBinding;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.ui.schedule.ScheduleEventListener;

public class ScheduleDayAdapter extends ListAdapter<UserSession, ScheduleDayAdapter.SessionViewHolder> {

    private ScheduleEventListener eventListener;
    private RecyclerView.RecycledViewPool tagViewPool;

    public ScheduleDayAdapter(ScheduleEventListener eventListener, RecyclerView.RecycledViewPool tagViewPool) {
        super(SessionDiff);
        this.eventListener = eventListener;
        this.tagViewPool = tagViewPool;
    }

    @Override
    public SessionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.tags.setRecycledViewPool(tagViewPool);
        if (binding.tags.getLayoutManager() instanceof FlexboxLayoutManager) {
            ((FlexboxLayoutManager) binding.tags.getLayoutManager()).setRecycleChildrenOnDetach(true);
        }
        return new SessionViewHolder(binding, eventListener);
    }

    @Override
    public void onBindViewHolder(SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public static class SessionViewHolder extends RecyclerView.ViewHolder {

        private ItemSessionBinding binding;
        private ScheduleEventListener eventListener;

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

    public static DiffUtil.ItemCallback<UserSession> SessionDiff = new DiffUtil.ItemCallback<UserSession>() {
        @Override
        public boolean areItemsTheSame(UserSession oldItem, UserSession newItem) {
            return oldItem.getSession().getId().equals(newItem.getSession().getId());
        }

        @Override
        public boolean areContentsTheSame(UserSession oldItem, UserSession newItem) {
            return oldItem.equals(newItem);
        }
    };
}