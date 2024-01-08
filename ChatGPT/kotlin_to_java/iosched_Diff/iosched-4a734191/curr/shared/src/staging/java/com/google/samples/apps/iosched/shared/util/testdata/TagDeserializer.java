package com.google.samples.apps.iosched.shared.util.testdata;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.samples.apps.iosched.shared.model.Tag;
import java.lang.reflect.Type;

public class TagDeserializer implements JsonDeserializer<Tag> {

    @Override
    public Tag deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonElement obj = json.getAsJsonObject();
        return new Tag(
                obj.get("tag").getAsString(),
                obj.get("category").getAsString(),
                obj.get("order_in_category") != null ? obj.get("order_in_category").getAsInt() : 999,
                parseColor(obj.get("color") != null ? obj.get("color").getAsString() : null),
                obj.get("name").getAsString()
        );
    }
}