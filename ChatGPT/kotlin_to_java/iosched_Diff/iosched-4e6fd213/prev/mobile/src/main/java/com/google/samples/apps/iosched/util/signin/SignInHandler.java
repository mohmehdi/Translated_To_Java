package com.google.samples.apps.iosched.util.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.util.Collections;

public interface SignInHandler {

    Intent makeSignInIntent();

    void signIn(int resultCode, Intent data, SignInCallback onComplete);

    void signOut(Context context, SignOutCallback onComplete);
}

class DefaultSignInHandler implements SignInHandler {

    @Override
    public Intent makeSignInIntent() {
        AuthUI.IdpConfig googleProvider = new AuthUI.IdpConfig.GoogleBuilder()
                .setSignInOptions(new GoogleSignInOptions.Builder()
                        .requestId()
                        .requestEmail()
                        .build())
                .build();

        return AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(Collections.singletonList(googleProvider))
                .build();
    }

    @SuppressWarnings("unused")
    @Override
    public void signIn(int resultCode, Intent data, SignInCallback onComplete) {
        if (resultCode == Activity.RESULT_OK) {
            onComplete.onComplete(SignInResult.INSTANCE.getSignInSuccess());
        } else {
            onComplete.onComplete(SignInResult.INSTANCE.getSignInFailed(
                    IdpResponse.fromResultIntent(data).getError()));
        }
    }

    @Override
    public void signOut(Context context, SignOutCallback onComplete) {
        AuthUI.getInstance()
                .signOut(context)
                .addOnCompleteListener(task -> onComplete.onComplete());
    }
}
