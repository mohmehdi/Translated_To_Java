
package com.google.samples.apps.iosched.util.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public interface SignInHandler {

    Intent makeSignInIntent();

    void signIn(int resultCode, Intent data, OnCompleteListener<SignInResult> onComplete);

    void signOut(Context context, OnCompleteListener<Void> onComplete);
}

class DefaultSignInHandler implements SignInHandler {

    @Override
    public Intent makeSignInIntent() {

        List<AuthUI.IdpConfig> providers = new ArrayList<>();
        providers.add(AuthUI.IdpConfig.GoogleBuilder()
                .setSignInOptions(
                        GoogleSignInOptions.Builder()
                                .requestId()
                                .requestEmail()
                                .build()
                )
                .build());

        return AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build();
    }

    @Override
    public void signIn(int resultCode, Intent data, OnCompleteListener<SignInResult> onComplete) {
        if (resultCode == Activity.RESULT_OK) {
            onComplete.onComplete(SignInSuccess.getInstance());
        } else {
            IdpResponse idpResponse = IdpResponse.fromResultIntent(data);
            onComplete.onComplete(new SignInFailed(idpResponse.getError()));
        }
    }

    @Override
    public void signOut(Context context, OnCompleteListener<Void> onComplete) {
        AuthUI.getInstance()
                .signOut(context)
                .addOnCompleteListener(onComplete);
    }
}
