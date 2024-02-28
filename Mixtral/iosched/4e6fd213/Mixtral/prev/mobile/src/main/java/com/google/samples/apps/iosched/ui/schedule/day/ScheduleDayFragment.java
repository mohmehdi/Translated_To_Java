//!#!
//--------------------Class--------------------
//-------------------Functions-----------------
//Functions extra in Java:
//+ onChanged(Event<String>)
//+ run()
//+ onChanged(SessionTimeData)
//+ onEventUnhandled(Event<String>)
//+ onApplyWindowInsets(View, WindowInsets)
//+ execute(View)

//-------------------Extra---------------------
//---------------------------------------------
package com.google.samples.apps.iosched.ui.schedule.day;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecycledViewPool;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleDayBinding;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.util.ActivityViewModelProvider;
import com.google.samples.apps.iosched.shared.util.LazyFast;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.ui.schedule.SessionTimeData;
import com.google.samples.apps.iosched.ui.schedule.SessionViewHolder;
import com.google.samples.apps.iosched.ui.schedule.TimeItemDecoration;
import com.google.samples.apps.iosched.util.ClearDecorations;
import com.google.samples.apps.iosched.util.ExecuteAfter;
import dagger.android.support.DaggerFragment;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

public class ScheduleDayFragment extends DaggerFragment {

  private static final String TAG = "ScheduleDayFragment";
  private static final String ARG_CONFERENCE_DAY = "arg.CONFERENCE_DAY";

  @Inject
  ViewModelProvider.Factory viewModelFactory;

  private ScheduleViewModel viewModel;
  private FragmentScheduleDayBinding binding;

  @Inject
  @Named("sessionViewPool")
  RecycledViewPool sessionViewPool;

  @Inject
  @Named("tagViewPool")
  RecycledViewPool tagViewPool;

  private int conferenceDay;
  private ScheduleDayAdapter adapter;

  public static ScheduleDayFragment newInstance(int day) {
    Bundle args = new Bundle();
    args.putInt(ARG_CONFERENCE_DAY, day);
    ScheduleDayFragment fragment = new ScheduleDayFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState
  ) {
    viewModel =
      ActivityViewModelProvider
        .of(requireActivity(), viewModelFactory)
        .get(ScheduleViewModel.class);
    binding = FragmentScheduleDayBinding.inflate(inflater, container, false);
    binding.setLifecycleOwner(this);
    binding.setViewModel(viewModel);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(
    @NonNull View view,
    @Nullable Bundle savedInstanceState
  ) {
    super.onViewCreated(view, savedInstanceState);
    adapter =
      new ScheduleDayAdapter(
        viewModel,
        tagViewPool,
        viewModel.showReservations,
        viewModel.timeZoneId,
        this
      );

    binding.recyclerview.setAdapter(adapter);
    binding.recyclerview.setRecycledViewPool(sessionViewPool);
    LinearLayoutManager layoutManager = new LinearLayoutManager(
      binding.getRoot().getContext()
    );
    binding.recyclerview.setLayoutManager(layoutManager);
    layoutManager.setRecycleChildrenOnDetach(true);
    DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
    itemAnimator.setSupportsChangeAnimations(false);
    itemAnimator.setAddDuration(160);
    itemAnimator.setMoveDuration(160);
    itemAnimator.setChangeDuration(160);
    itemAnimator.setRemoveDuration(120);
    binding.recyclerview.setItemAnimator(itemAnimator);

    viewModel.currentEvent.observe(
      this,
      new Observer<Event<String>>() {
        @Override
        public void onChanged(Event<String> eventLocation) {
          if (
            eventLocation != null &&
            !viewModel.userHasInteracted &&
            eventLocation.getData() != null &&
            eventLocation.getData().day == conferenceDay &&
            eventLocation.getData().sessionIndex != -1
          ) {
            binding.recyclerview.post(
              new Runnable() {
                @Override
                public void run() {
                  layoutManager.scrollToPositionWithOffset(
                    eventLocation.getData().sessionIndex,
                    binding
                      .getRoot()
                      .getContext()
                      .getResources()
                      .getDimensionPixelSize(R.dimen.margin_normal)
                  );
                }
              }
            );
          }
        }
      }
    );
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewModel
      .getSessionTimeDataForDay(conferenceDay)
      .observe(
        requireActivity(),
        new Observer<SessionTimeData>() {
          @Override
          public void onChanged(SessionTimeData sessionTimeData) {
            initializeList(sessionTimeData);
          }
        }
      );

    viewModel.errorMessage.observe(
      this,
      new EventObserver<String>() {
        @Override
        public void onEventUnhandled(Event<String> event) {
          Toast
            .makeText(getContext(), event.getData(), Toast.LENGTH_LONG)
            .show();
        }
      }
    );
  }

  private void initializeList(SessionTimeData sessionTimeData) {
    List<SessionViewHolder.Session> list = sessionTimeData.getList();
    String timeZoneId = sessionTimeData.getTimeZoneId();

    if (list != null && timeZoneId != null) {
      adapter.submitList(list);

      binding.recyclerview.addOnLayoutChangeListener(
        new OnApplyWindowInsetsListener() {
          @Override
          public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
            if (list.size() > 0) {
              binding.recyclerview.addItemDecoration(
                new TimeItemDecoration(
                  binding.recyclerview.getContext(),
                  list,
                  timeZoneId
                )
              );
            }
            return insets;
          }
        }
      );

      binding.executeAfter(
        new Runnable() {
          @Override
          public void run() {
            isEmpty = list.isEmpty();
          }
        }
      );
    }
  }
}
