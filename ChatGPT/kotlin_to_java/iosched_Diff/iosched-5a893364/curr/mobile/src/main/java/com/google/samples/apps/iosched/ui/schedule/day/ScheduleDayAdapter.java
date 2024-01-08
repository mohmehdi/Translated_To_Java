package com.google.samples.apps.iosched.ui.schedule.day;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;
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
    private LiveData<Boolean> isLoggedIn;
    private LiveData<Boolean> isRegistered;
    private LifecycleOwner lifecycleOwner;

    public ScheduleDayAdapter(ScheduleEventListener eventListener, RecyclerView.RecycledViewPool tagViewPool,
                              LiveData<Boolean> isLoggedIn, LiveData<Boolean> isRegistered, LifecycleOwner lifecycleOwner) {
        super(SessionDiff);
        this.eventListener = eventListener;
        this.tagViewPool = tagViewPool;
        this.isLoggedIn = isLoggedIn;
        this.isRegistered = isRegistered;
        this.lifecycleOwner = lifecycleOwner;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.tags.setRecycledViewPool(tagViewPool);
        if (binding.tags.getLayoutManager() instanceof FlexboxLayoutManager) {
            ((FlexboxLayoutManager) binding.tags.getLayoutManager()).setRecycleChildrenOnDetach(true);
        }
        return new SessionViewHolder(binding, eventListener, isLoggedIn, isRegistered, lifecycleOwner);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public static class SessionViewHolder extends RecyclerView.ViewHolder {

        private ItemSessionBinding binding;
        private ScheduleEventListener eventListener;
        private LiveData<Boolean> isLoggedIn;
        private LiveData<Boolean> isRegistered;
        private LifecycleOwner lifecycleOwner;

        public SessionViewHolder(ItemSessionBinding binding, ScheduleEventListener eventListener,
                                 LiveData<Boolean> isLoggedIn, LiveData<Boolean> isRegistered, LifecycleOwner lifecycleOwner) {
            super(binding.getRoot());
            this.binding = binding;
            this.eventListener = eventListener;
            this.isLoggedIn = isLoggedIn;
            this.isRegistered = isRegistered;
            this.lifecycleOwner = lifecycleOwner;
        }

        public void bind(UserSession userSession) {
            binding.setSession(userSession.getSession());
            binding.setUserEvent(userSession.getUserEvent());
            binding.setEventListener(eventListener);
            binding.setIsLoggedIn(isLoggedIn);
            binding.setIsRegistered(isRegistered);
            binding.setLifecycleOwner(lifecycleOwner);
            binding.executePendingBindings();
        }
    }

    public static DiffUtil.ItemCallback<UserSession> SessionDiff = new DiffUtil.ItemCallback<UserSession>() {
        @Override
        public boolean areItemsTheSame(@NonNull UserSession oldItem, @NonNull UserSession newItem) {
            return oldItem.getSession().getId().equals(newItem.getSession().getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserSession oldItem, @NonNull UserSession newItem) {
            return oldItem.equals(newItem);
        }
    };
}