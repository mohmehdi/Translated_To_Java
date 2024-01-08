package com.google.samples.apps.iosched.util;

import android.databinding.BindingAdapter;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.ItemSessionTagBinding;
import com.google.samples.apps.iosched.shared.model.Tag;

public class BindingAdapters {

    @BindingAdapter("invisibleUnless")
    public static void invisibleUnless(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @BindingAdapter("goneUnless")
    public static void goneUnless(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("sessionTags")
    public static void sessionTags(LinearLayout container, List<Tag> sessionTags) {
        container.removeAllViews();
        if (sessionTags != null) {
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            for (Tag sessionTag : sessionTags) {
                container.addView(createSessionTagButton(inflater, container, sessionTag));
            }
        }
    }

    private static Button createSessionTagButton(LayoutInflater inflater, ViewGroup container, Tag sessionTag) {
        ItemSessionTagBinding tagBinding = ItemSessionTagBinding.inflate(inflater, container, false);
        tagBinding.setTag(sessionTag);
        return tagBinding.getTagButton();
    }

    @BindingAdapter("tagTint")
    public static void tagTint(TextView textView, int color) {
        int tintColor = (color != Color.TRANSPARENT) ? color : ContextCompat.getColor(textView.getContext(), R.color.default_tag_color);
        textView.getCompoundDrawablesRelative()[0].setTint(tintColor);
    }
}