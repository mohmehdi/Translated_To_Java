package com.google.samples.apps.iosched.util.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

public interface SignInHandler {

    Intent makeSignInIntent();

    void signIn(int resultCode, Intent data, SignInResult onComplete);

    void signOut(Context context, Runnable onComplete);
}


public class DefaultSignInHandler implements SignInHandler {


    @Override
    public Intent makeSignInIntent() {


        List<AuthUI.IdpConfig> providers = new ArrayList<>();
        providers.add(
            new AuthUI.IdpConfig.GoogleBuilder().setSignInOptions(
                new GoogleSignInOptions.Builder()
                    .requestId()
                    .requestEmail()
                    .build()
            ).build()
        );

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build();
    }


    @SuppressWarnings("unused")
    @Override
    public void signIn(
        int resultCode,
        Intent data,
        SignInResult onComplete
    ) {
        switch (resultCode) {
            case Activity.RESULT_OK:
                onComplete.onComplete(SignInSuccess.INSTANCE);
                break;
            default:
                onComplete.onComplete(new SignInFailed(IdpResponse.fromResultIntent(data).getError()));
                break;
        }
    }


    @Override
    public void signOut(Context context, Runnable onComplete) {
        AuthUI.getInstance()
            .signOut(context)
            .addOnCompleteListener(task -> onComplete.run());
    }
}