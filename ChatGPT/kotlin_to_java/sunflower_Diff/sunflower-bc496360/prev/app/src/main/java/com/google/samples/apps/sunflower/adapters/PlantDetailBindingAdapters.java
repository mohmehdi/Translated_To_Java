
package com.google.samples.apps.sunflower.adapters;

import android.text.method.LinkMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT;
import androidx.databinding.BindingAdapter;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.samples.apps.sunflower.R;

import coil.Coil;
import coil.load;

public class BindingAdapters {

    @BindingAdapter("imageFromUrl")
    public static void bindImageFromUrl(ImageView view, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Coil.load(view.getContext(), imageUrl) {
                crossfade(true);
            }
        }
    }

    @BindingAdapter("isGone")
    public static void bindIsGone(FloatingActionButton view, Boolean isGone) {
        if (isGone == null || isGone) {
            view.hide();
        } else {
            view.show();
        }
    }

    @BindingAdapter("renderHtml")
    public static void bindRenderHtml(TextView view, String description) {
        if (description != null) {
            view.setText(HtmlCompat.fromHtml(description, FROM_HTML_MODE_COMPACT));
            view.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            view.setText("");
        }
    }

    @BindingAdapter("wateringText")
    public static void bindWateringText(TextView textView, int wateringInterval) {
        Resources resources = textView.getContext().getResources();
        String quantityString = resources.getQuantityString(
            R.plurals.watering_needs_suffix,
            wateringInterval, wateringInterval
        );

        textView.setText(quantityString);
    }
}
