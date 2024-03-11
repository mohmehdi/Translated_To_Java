package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.result.Result.Success;
import org.junit.Assert;
import org.junit.Test;

public class LoadTagsUseCaseTest {

    @Test
    public void returnsListOfTags() {
        LoadTagsUseCase loadTagsUseCase = new LoadTagsUseCase(new TagRepository(TestTagDataSource.INSTANCE));
        Result.Success<List<Tag>> tags = (Success<List<Tag>>) loadTagsUseCase.executeNow(Unit.INSTANCE);

        Assert.assertEquals(tags.getData(), new TagRepository(TestTagDataSource.INSTANCE).getTags());
    }
}