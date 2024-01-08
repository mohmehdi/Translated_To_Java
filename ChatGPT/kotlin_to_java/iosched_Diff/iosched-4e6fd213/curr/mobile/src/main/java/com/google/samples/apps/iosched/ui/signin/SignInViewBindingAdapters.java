package com.google.samples.apps.iosched.ui.signin;

import android.view.View;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;

public class SignInViewBindingAdapters {

    @BindingAdapter("navHeaderTitle")
    public static void setNavHeaderTitle(TextView view, AuthenticatedUserInfo user) {
        if (user != null && user.isSignedIn()) {
            view.setText(user.getDisplayName());
        } else {
            view.setText(R.string.welcome);
        }
    }

    @BindingAdapter("navHeaderSubtitle")
    public static void setNavHeaderSubtitle(TextView view, AuthenticatedUserInfo user) {
        if (user != null && user.isSignedIn()) {
            view.setText(user.getEmail());
        } else {
            view.setText(R.string.sign_in_hint);
        }
    }

    @BindingAdapter("navHeaderContentDescription")
    public static void setNavHeaderContentDescription(View view, AuthenticatedUserInfo user) {
        if (user != null && user.isSignedIn()) {
            view.setContentDescription(view.getResources().getString(R.string.a11y_signed_in_content_description, user.getDisplayName()));
        } else {
            view.setContentDescription(view.getResources().getString(R.string.a11y_signed_out_content_description));
        }
    }
}