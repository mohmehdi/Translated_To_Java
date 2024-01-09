package com.google.samples.apps.sunflower.adapters;

import android.text.method.LinkMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import coil.load;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.samples.apps.sunflower.R;

public class PlantDetailBindingAdapters {

  @BindingAdapter("imageFromUrl")
  public static void bindImageFromUrl(
    ImageView view,
    @Nullable String imageUrl
  ) {
    if (imageUrl != null && !imageUrl.isEmpty()) {
      view.load(
        imageUrl,
        new ImageLoader.Builder(view.getContext()).crossfade(true).build()
      );
    }
  }

  @BindingAdapter("isGone")
  public static void bindIsGone(
    FloatingActionButton view,
    @Nullable Boolean isGone
  ) {
    if (isGone == null || isGone) {
      view.hide();
    } else {
      view.show();
    }
  }

  @BindingAdapter("renderHtml")
  public static void bindRenderHtml(
    TextView view,
    @Nullable String description
  ) {
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
    android.content.res.Resources resources = textView
      .getContext()
      .getResources();
    String quantityString = resources.getQuantityString(
      R.plurals.watering_needs_suffix,
      wateringInterval,
      wateringInterval
    );

    textView.setText(quantityString);
  }
}
