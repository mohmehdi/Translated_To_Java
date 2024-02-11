

package com.google.samples.apps.iosched.shared.util.testdata;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.util.ColorUtils;
import org.threeten.bp.ZonedDateTime;
import java.lang.reflect.Type;

public class BlockDeserializer implements JsonDeserializer<Block> {

    @Override
    public Block deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject obj = json.getAsJsonObject();
        int color = ColorUtils.parseColor(obj.get("color").getAsString());
        String strokeColorStr = obj.get("strokeColor").getAsString();
        int strokeColor = strokeColorStr != null ? ColorUtils.parseColor(strokeColorStr) : color;
        return new Block(
            obj.get("title").getAsString(),
            obj.get("type").getAsString(),
            color,
            obj.get("isDark").getAsBoolean(),
            strokeColor,
            ZonedDateTime.parse(obj.get("start").getAsString()),
            ZonedDateTime.parse(obj.get("end").getAsString())
        );
    }
}