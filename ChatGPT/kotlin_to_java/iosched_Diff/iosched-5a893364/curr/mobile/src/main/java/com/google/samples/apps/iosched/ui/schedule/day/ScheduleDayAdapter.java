package com.google.samples.apps.iosched.ui.schedule.day;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.samples.apps.iosched.databinding.ItemSessionBinding;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.ui.schedule.ScheduleEventListener;

public class ScheduleDayAdapter extends ListAdapter<UserSession, SessionViewHolder> {

    private final ScheduleEventListener eventListener;
    private final RecyclerView.RecycledViewPool tagViewPool;
    private final LiveData<Boolean> isLoggedIn;
    private final LiveData<Boolean> isRegistered;
    private final LifecycleOwner lifecycleOwner;

    public ScheduleDayAdapter(ScheduleEventListener eventListener, RecyclerView.RecycledViewPool tagViewPool,
                              LiveData<Boolean> isLoggedIn, LiveData<Boolean> isRegistered, LifecycleOwner lifecycleOwner) {
        super(SessionDiff);
        this.eventListener = eventListener;
        this.tagViewPool = tagViewPool;
        this.isLoggedIn = isLoggedIn;
        this.isRegistered = isRegistered;
        this.lifecycleOwner = lifecycleOwner;
    }

    @Override
    public SessionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemSessionBinding binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.tags.setRecycledViewPool(tagViewPool);
        if (binding.tags.getLayoutManager() instanceof FlexboxLayoutManager) {
            ((FlexboxLayoutManager) binding.tags.getLayoutManager()).setRecycleChildrenOnDetach(true);
        }
        return new SessionViewHolder(binding, eventListener, isLoggedIn, isRegistered, lifecycleOwner);
    }

    @Override
    public void onBindViewHolder(SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }
}

class SessionViewHolder extends RecyclerView.ViewHolder {

    private final ItemSessionBinding binding;
    private final ScheduleEventListener eventListener;
    private final LiveData<Boolean> isLoggedIn;
    private final LiveData<Boolean> isRegistered;
    private final LifecycleOwner lifecycleOwner;

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

class SessionDiff extends DiffUtil.ItemCallback<UserSession> {

    @Override
    public boolean areItemsTheSame(UserSession oldItem, UserSession newItem) {
        return oldItem.getSession().getId().equals(newItem.getSession().getId());
    }

    @Override
    public boolean areContentsTheSame(UserSession oldItem, UserSession newItem) {
        return oldItem.equals(newItem);
    }
}