package com.google.samples.apps.iosched.ui.signin;

import android.app.Dialog;
import android.os.Bundle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.signin.SignInEvent.RequestSignIn;
import com.google.samples.apps.iosched.util.signin.SignInHandler;
import dagger.android.support.DaggerAppCompatDialogFragment;
import javax.inject.Inject;

public class SignInDialogFragment extends DaggerAppCompatDialogFragment {

    @Inject
    private SignInHandler signInHandler;

    @Inject
    private ViewModelProvider.Factory viewModelFactory;

    private SignInViewModel signInViewModel;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        signInViewModel = viewModelProvider(viewModelFactory);
        signInViewModel.getPerformSignInEvent().observe(this, new Observer<SignInEvent>() {
            @Override
            public void onChanged(SignInEvent request) {
                if (request.peekContent() == RequestSignIn) {
                    request.getContentIfNotHandled();
                    if (getActivity() != null) {
                        if (signInHandler.makeSignInIntent() != null) {
                            startActivityForResult(signInHandler.makeSignInIntent(), REQUEST_CODE_SIGN_IN);
                        }
                    }
                }
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_sign_in_title)
            .setMessage(R.string.dialog_sign_in_content)
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.sign_in, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    signInViewModel.onSignIn();
                }
            })
            .create();
    }

    public static final String DIALOG_SIGN_IN = "dialog_sign_in";
    public static final int REQUEST_CODE_SIGN_IN = 42;
}