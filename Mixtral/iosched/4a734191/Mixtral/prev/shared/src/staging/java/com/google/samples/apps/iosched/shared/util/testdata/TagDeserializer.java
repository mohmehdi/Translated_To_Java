

package com.google.samples.apps.iosched.shared.util.testdata;

import android.graphics.Color;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.ColorUtils;
import timber.log.Timber;
import java.lang.reflect.Type;

public class TagDeserializer implements JsonDeserializer<Tag> {

    @Override
    public Tag deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject obj = json.getAsJsonObject();
        String colorString = obj.get("color").getAsString();
        int color;
        try {
            color = ColorUtils.parseHexColor(colorString);
        } catch (Throwable t) {
            Timber.d(t, "Failed to parse tag color");
            color = Color.TRANSPARENT;
        }
        return new Tag(
                obj.get("tag").getAsString(),
                obj.get("category").getAsString(),
                obj.get("order_in_category") == null ? 999 : obj.get("order_in_category").getAsInt(),
                color,
                obj.get("name").getAsString()
        );
    }
}