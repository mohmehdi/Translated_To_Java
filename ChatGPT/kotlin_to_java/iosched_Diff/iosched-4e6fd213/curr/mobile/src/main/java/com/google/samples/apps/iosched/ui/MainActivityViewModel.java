package com.google.samples.apps.iosched.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import javax.inject.Inject;

public class MainActivityViewModel extends ViewModel implements SignInViewModelDelegate, ThemedActivityDelegate {

    private SignInViewModelDelegate signInViewModelDelegate;
    private ThemedActivityDelegate themedActivityDelegate;

    private MutableLiveData<Event<Unit>> _navigateToSignInDialogAction = new MutableLiveData<>();
    public LiveData<Event<Unit>> navigateToSignInDialogAction = _navigateToSignInDialogAction;

    private MutableLiveData<Event<Unit>> _navigateToSignOutDialogAction = new MutableLiveData<>();
    public LiveData<Event<Unit>> navigateToSignOutDialogAction = _navigateToSignOutDialogAction;

    @Inject
    public MainActivityViewModel(SignInViewModelDelegate signInViewModelDelegate, ThemedActivityDelegate themedActivityDelegate) {
        this.signInViewModelDelegate = signInViewModelDelegate;
        this.themedActivityDelegate = themedActivityDelegate;
    }

    public void onProfileClicked() {
        if (isSignedIn()) {
            _navigateToSignOutDialogAction.setValue(new Event<>(Unit.INSTANCE));
        } else {
            _navigateToSignInDialogAction.setValue(new Event<>(Unit.INSTANCE));
        }
    }
}