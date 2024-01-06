package com.google.samples.apps.sunflower.adapters;

import android.text.method.LinkMovementMethod;
import android.widget.FloatingActionButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.text.HtmlCompat;
import androidx.databinding.BindingAdapter;
import coil.load;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.samples.apps.sunflower.R;

public class PlantDetailBindingAdapters {

  @BindingAdapter("imageFromUrl")
  public static void bindImageFromUrl(ImageView view, String imageUrl) {
    if (imageUrl != null && !imageUrl.isEmpty()) {
      view.load(imageUrl, new RequestOptions().crossfade(true));
    }
  }

  @BindingAdapter("isFabGone")
  public static void bindIsFabGone(FloatingActionButton view, Boolean isGone) {
    if (isGone == null || isGone) {
      view.setVisibility(View.GONE);
    } else {
      view.setVisibility(View.VISIBLE);
    }
  }

  @BindingAdapter("renderHtml")
  public static void bindRenderHtml(TextView view, String description) {
    if (description != null) {
      view.setText(
        HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT)
      );
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
      wateringInterval,
      wateringInterval
    );

    textView.setText(quantityString);
  }
}
