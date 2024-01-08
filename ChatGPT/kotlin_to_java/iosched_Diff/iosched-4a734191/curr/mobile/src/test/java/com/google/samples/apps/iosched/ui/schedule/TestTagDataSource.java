package com.google.samples.apps.iosched.ui.schedule;

import com.google.samples.apps.iosched.shared.data.tag.TagDataSource;
import com.google.samples.apps.iosched.shared.model.Tag;

public class TestTagDataSource implements TagDataSource {

    private Tag androidTag = new Tag("1", "TRACK", 0, "Android", 0xFFAED581);
    private Tag webTag = new Tag("2", "TRACK", 1, "Web", 0xFFFFF176);
    private Tag sessionsTag = new Tag("101", "TYPE", 0, "Sessions", 0);
    private Tag codelabsTag = new Tag("102", "TYPE", 1, "Codelabs", 0);
    private Tag beginnerTag = new Tag("201", "LEVEL", 0, "Beginner", 0);
    private Tag intermediateTag = new Tag("202", "LEVEL", 1, "Intermediate", 0);

    @Override
    public List<Tag> getTags() {
        return Arrays.asList(androidTag, webTag, sessionsTag, codelabsTag, beginnerTag, intermediateTag);
    }
}