
package com.google.samples.apps.iosched.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.google.samples.apps.iosched.ui.themedactivity.ThemedActivityDelegate;
import javax.inject.Inject;

public class MainActivityViewModel
  extends ViewModel
  implements SignInViewModelDelegate, ThemedActivityDelegate {

  private final MutableLiveData<Event<Unit>> _navigateToSignInDialogAction;
  public LiveData<Event<Unit>> navigateToSignInDialogAction;

  private final MutableLiveData<Event<Unit>> _navigateToSignOutDialogAction;
  public LiveData<Event<Unit>> navigateToSignOutDialogAction;

  @Inject
  public MainActivityViewModel(
    SignInViewModelDelegate signInViewModelDelegate,
    ThemedActivityDelegate themedActivityDelegate
  ) {
    _navigateToSignInDialogAction = new MutableLiveData<>();
    navigateToSignInDialogAction = _navigateToSignInDialogAction;

    _navigateToSignOutDialogAction = new MutableLiveData<>();
    navigateToSignOutDialogAction = _navigateToSignOutDialogAction;

    setSignInViewModelDelegate(signInViewModelDelegate);
    setThemedActivityDelegate(themedActivityDelegate);
  }

  public void onProfileClicked() {
    if (isSignedIn()) {
      _navigateToSignOutDialogAction.setValue(new Event<>(Unit.INSTANCE));
    } else {
      _navigateToSignInDialogAction.setValue(new Event<>(Unit.INSTANCE));
    }
  }
}
