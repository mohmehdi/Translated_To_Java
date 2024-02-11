

package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.result.ResultSuccess;
import org.junit.Assert;
import org.junit.Test;

public class LoadTagsUseCaseTest {

    @Test
    public void returnsListOfTags() {
        LoadTagsUseCase loadTagsUseCase = new LoadTagsUseCase(new TagRepository(new TestTagDataSource()));
        Result.Success<List<Tag>> tags = (ResultSuccess<List<Tag>>) loadTagsUseCase.executeNow(new Object());

        Assert.assertEquals(tags.getData(), new TagRepository(new TestTagDataSource()).getTags());
    }
}