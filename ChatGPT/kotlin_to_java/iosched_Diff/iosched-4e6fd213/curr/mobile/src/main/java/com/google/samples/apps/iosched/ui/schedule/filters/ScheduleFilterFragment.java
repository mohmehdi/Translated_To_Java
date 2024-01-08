package com.google.samples.apps.iosched.ui.schedule.filters;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorCompat;
import androidx.core.view.ViewPropertyAnimatorListener;
import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableFloat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleFilterBinding;
import com.google.samples.apps.iosched.shared.util.parentViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.BottomSheetCallback;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_COLLAPSED;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_EXPANDED;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_HIDDEN;
import com.google.samples.apps.iosched.widget.SpaceDecoration;

import javax.inject.Inject;

public class ScheduleFilterFragment extends DaggerFragment {

    private static final float ALPHA_CHANGEOVER = 0.33f;
    private static final float ALPHA_DESC_MAX = 0f;
    private static final float ALPHA_HEADER_MAX = 0.67f;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;
    private ScheduleFilterAdapter filterAdapter;
    private FragmentScheduleFilterBinding binding;
    private BottomSheetBehavior<?> behavior;
    private ObservableFloat headerAlpha = new ObservableFloat(1f);
    private ObservableFloat descriptionAlpha = new ObservableFloat(1f);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_schedule_filter, container, false);
        binding.setLifecycleOwner(this);
        binding.setHeaderAlpha(headerAlpha);
        binding.setDescriptionAlpha(descriptionAlpha);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        viewModel = parentViewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);

        behavior = BottomSheetBehavior.from(binding.filterSheet);

        filterAdapter = new ScheduleFilterAdapter(viewModel);
        viewModel.getEventFilters().observe(this, new Observer<List<EventFilter>>() {
            @Override
            public void onChanged(List<EventFilter> eventFilters) {
                filterAdapter.submitEventFilterList(eventFilters);
            }
        });

        binding.recyclerview.setAdapter(filterAdapter);
        binding.recyclerview.setHasFixedSize(true);
        GridLayoutManager layoutManager = (GridLayoutManager) binding.recyclerview.getLayoutManager();
        layoutManager.setSpanSizeLookup(new ScheduleFilterSpanSizeLookup(filterAdapter));
        binding.recyclerview.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                binding.filtersHeaderShadow.setActivated(recyclerView.canScrollVertically(-1));
            }
        });

        behavior.addBottomSheetCallback(new BottomSheetCallback() {
            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
                updateFilterHeadersAlpha(slideOffset);
            }
        });

        binding.collapseArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                behavior.setState(behavior.skipCollapsed() ? STATE_HIDDEN : STATE_COLLAPSED);
            }
        });

        binding.expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                behavior.setState(STATE_EXPANDED);
            }
        });

        binding.filterSheet.doOnLayout(new ViewCompat.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                float slideOffset;
                switch (behavior.getState()) {
                    case STATE_EXPANDED:
                        slideOffset = 1f;
                        break;
                    case STATE_COLLAPSED:
                        slideOffset = 0f;
                        break;
                    default:
                        slideOffset = -1f;
                        break;
                }
                updateFilterHeadersAlpha(slideOffset);
            }
        });
    }

    private void updateFilterHeadersAlpha(float slideOffset) {
        headerAlpha.set(offsetToAlpha(slideOffset, ALPHA_CHANGEOVER, ALPHA_HEADER_MAX));
        descriptionAlpha.set(offsetToAlpha(slideOffset, ALPHA_CHANGEOVER, ALPHA_DESC_MAX));
    }

    private float offsetToAlpha(float value, float rangeMin, float rangeMax) {
        return Math.min(Math.max((value - rangeMin) / (rangeMax - rangeMin), 0f), 1f);
    }
}

@BindingAdapter("selectedFilters")
public static void selectedFilters(RecyclerView recyclerView, List<EventFilter> filters) {
    FilterChipAdapter filterChipAdapter;
    if (recyclerView.getAdapter() == null) {
        filterChipAdapter = new FilterChipAdapter();
        recyclerView.setAdapter(filterChipAdapter);
        recyclerView.addItemDecoration(new SpaceDecoration(recyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.spacing_normal)));
    } else {
        filterChipAdapter = (FilterChipAdapter) recyclerView.getAdapter();
    }
    filterChipAdapter.setFilters(filters != null ? filters : Collections.emptyList());
    filterChipAdapter.notifyDataSetChanged();
}

@BindingAdapter(value = {"hasFilters", "eventCount"}, requireAll = true)
public static void filterHeader(TextView textView, Boolean hasFilters, Integer eventCount) {
    if (hasFilters != null && hasFilters && eventCount != null) {
        textView.setText(textView.getResources().getQuantityString(R.plurals.filter_event_count, eventCount, eventCount));
    } else {
        textView.setText(R.string.filters);
    }
}

@BindingAdapter(value = {"eventFilter", "viewModel"}, requireAll = true)
public static void setClickListenerForFilter(EventFilterView filter, EventFilter eventFilter, ScheduleViewModel viewModel) {
    filter.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (eventFilter instanceof MyEventsFilter && !viewModel.isSignedIn()) {
                viewModel.onSignInRequired();
            } else {
                boolean checked = !filter.isChecked();
                filter.animateCheckedAndInvoke(checked, new Runnable() {
                    @Override
                    public void run() {
                        viewModel.toggleFilter(eventFilter, checked);
                    }
                });
            }
        }
    });
}

@BindingAdapter(value = {"eventFilters", "animatedOnClick"}, requireAll = false)
public static void setResetFiltersClickListener(Button reset, ViewGroup eventFilters, View.OnClickListener listener) {
    reset.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            for (int i = 0; i < eventFilters.getChildCount(); i++) {
                View outer = eventFilters.getChildAt(i);
                if (outer instanceof ViewGroup) {
                    ViewGroup outerGroup = (ViewGroup) outer;
                    for (int j = 0; j < outerGroup.getChildCount(); j++) {
                        View view = outerGroup.getChildAt(j);
                        if (view instanceof EventFilterView && ((EventFilterView) view).isChecked()) {
                            ((EventFilterView) view).animateCheckedAndInvoke(false, new Runnable() {
                                @Override
                                public void run() {
                                    listener.onClick(reset);
                                }
                            });
                        }
                    }
                }
            }
        }
    });
}

@BindingAdapter("eventFilterText")
public static void eventFilterText(EventFilterView view, EventFilter filter) {
    CharSequence text;
    if (filter.getTextResId() != 0) {
        text = view.getResources().getText(filter.getTextResId());
    } else {
        text = filter.getText();
    }
    view.setText(text);
}

@BindingAdapter("eventFilterTextShort")
public static void eventFilterTextShort(EventFilterView view, EventFilter filter) {
    CharSequence text;
    if (filter.getShortTextResId() != 0) {
        text = view.getResources().getText(filter.getShortTextResId());
    } else {
        text = filter.getShortText();
    }
    view.setText(text);
}