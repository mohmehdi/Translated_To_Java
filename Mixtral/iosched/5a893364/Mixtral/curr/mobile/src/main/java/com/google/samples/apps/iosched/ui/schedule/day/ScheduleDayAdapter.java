

package com.google.samples.apps.iosched.ui.schedule.day;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.recyclerview.extensions.DiffCallback;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.RecycledViewPool;
import android.support.v7.widget.RecyclerView.ViewHolder;
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
    private final LiveData<Boolean> isLoggedIn;
    private final LiveData<Boolean> isRegistered;
    private final LifecycleOwner lifecycleOwner;

    public ScheduleDayAdapter(
            @NonNull ScheduleEventListener eventListener,
            @NonNull RecycledViewPool tagViewPool,
            @NonNull LiveData<Boolean> isLoggedIn,
            @NonNull LiveData<Boolean> isRegistered,
            @NonNull LifecycleOwner lifecycleOwner) {
        super(new SessionDiff());
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
        FlexboxLayoutManager layoutManager = (FlexboxLayoutManager) binding.tags.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setRecycleChildrenOnDetach(true);
        }
        return new SessionViewHolder(binding, eventListener, isLoggedIn, isRegistered, lifecycleOwner);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
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

class SessionViewHolder extends ViewHolder {

    private final ItemSessionBinding binding;
    private final ScheduleEventListener eventListener;
    private final LiveData<Boolean> isLoggedIn;
    private final LiveData<Boolean> isRegistered;
    private final LifecycleOwner lifecycleOwner;

    public SessionViewHolder(
            @NonNull ItemSessionBinding binding,
            @NonNull ScheduleEventListener eventListener,
            @NonNull LiveData<Boolean> isLoggedIn,
            @NonNull LiveData<Boolean> isRegistered,
            @NonNull LifecycleOwner lifecycleOwner) {
        super(binding.getRoot());
        this.binding = binding;
        this.eventListener = eventListener;
        this.isLoggedIn = isLoggedIn;
        this.isRegistered = isRegistered;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void bind(@NonNull UserSession userSession) {
        binding.setSession(userSession.getSession());
        binding.setUserEvent(userSession.getUserEvent());
        binding.setEventListener(eventListener);
        binding.setIsLoggedIn(isLoggedIn);
        binding.setIsRegistered(isRegistered);
        binding.setLifecycleOwner(lifecycleOwner);
        binding.executePendingBindings();
    }
}