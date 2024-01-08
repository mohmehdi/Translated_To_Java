package com.google.samples.apps.iosched.ui.speaker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.app.NavUtils;
import androidx.core.os.bundleOf;
import androidx.core.view.doOnLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentSpeakerBinding;
import com.google.samples.apps.iosched.model.SpeakerId;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.prefs.SnackbarPreferenceViewModel;
import com.google.samples.apps.iosched.ui.sessiondetail.PushUpScrollListener;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.setUpSnackbar;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.util.postponeEnterTransition;
import dagger.android.support.DaggerFragment;
import javax.inject.Inject;
import javax.inject.Named;

public class SpeakerFragment extends DaggerFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    @Inject
    SnackbarMessageManager snackbarMessageManager;

    @Inject
    AnalyticsHelper analyticsHelper;

    @Inject
    @Named("tagViewPool")
    RecycledViewPool tagRecycledViewPool;

    private SpeakerViewModel speakerViewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        speakerViewModel = activityViewModelProvider(viewModelFactory);
        speakerViewModel.setSpeakerId(requireNotNull(getArguments()).getString(SPEAKER_ID));

        getActivity().postponeEnterTransition(500L);

        FragmentSpeakerBinding binding = FragmentSpeakerBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(speakerViewModel);

        speakerViewModel.hasProfileImage.observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean it) {
                if (it != true) {
                    getActivity().startPostponedEnterTransition();
                }
            }
        });

        speakerViewModel.navigateToEventAction.observe(this, new EventObserver<String>() {
            @Override
            public void onEvent(String sessionId) {
                startActivity(SessionDetailActivity.starterIntent(requireContext(), sessionId));
            }
        });

        speakerViewModel.navigateToSignInDialogAction.observe(this, new EventObserver<Void>() {
            @Override
            public void onEvent(Void v) {
                SignInDialogFragment dialog = new SignInDialogFragment();
                dialog.show(requireActivity().getSupportFragmentManager(), SignInDialogFragment.DIALOG_SIGN_IN);
            }
        });

        SnackbarPreferenceViewModel snackbarPrefViewModel = viewModelProvider(viewModelFactory);
        setUpSnackbar(
                speakerViewModel.snackBarMessage,
                binding.snackbar,
                snackbarMessageManager,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbarPrefViewModel.onStopClicked();
                    }
                }
        );

        ImageLoadListener headshotLoadListener = new ImageLoadListener() {
            @Override
            public void onImageLoaded() {
                getActivity().startPostponedEnterTransition();
            }

            @Override
            public void onImageLoadFailed() {
                getActivity().startPostponedEnterTransition();
            }
        };
        SpeakerAdapter speakerAdapter = new SpeakerAdapter(this, speakerViewModel, headshotLoadListener, tagRecycledViewPool);
        binding.speakerDetailRecyclerView.setAdapter(speakerAdapter);
        RecyclerView.ItemAnimator itemAnimator = binding.speakerDetailRecyclerView.getItemAnimator();
        if (itemAnimator != null) {
            itemAnimator.addDuration = 120L;
            itemAnimator.moveDuration = 120L;
            itemAnimator.changeDuration = 120L;
            itemAnimator.removeDuration = 100L;
        }
        binding.speakerDetailRecyclerView.doOnLayout(new Runnable() {
            @Override
            public void run() {
                binding.speakerDetailRecyclerView.addOnScrollListener(
                        new PushUpScrollListener(binding.up, it, R.id.speaker_name, R.id.speaker_grid_image)
                );
            }
        });

        speakerViewModel.speakerUserSessions.observe(this, new Observer<List<Session>>() {
            @Override
            public void onChanged(List<Session> it) {
                speakerAdapter.speakerSessions = it != null ? it : Collections.emptyList();
            }
        });

        binding.up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavUtils.navigateUpFromSameTask(requireActivity());
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        speakerViewModel.speaker.observe(this, new Observer<Speaker>() {
            @Override
            public void onChanged(Speaker it) {
                if (it != null) {
                    String pageName = "Speaker Details: " + it.getName();
                    analyticsHelper.sendScreenView(pageName, requireActivity());
                }
            }
        });
    }

    public static SpeakerFragment newInstance(SpeakerId speakerId) {
        SpeakerFragment fragment = new SpeakerFragment();
        fragment.setArguments(bundleOf(SPEAKER_ID to speakerId));
        return fragment;
    }
}