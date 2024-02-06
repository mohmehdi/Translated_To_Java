




package io.plaidapp.designernews.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.util.Exhaustive;

public class DesignerNewsDataSource implements PlaidDataSource {
    private final SourceItem sourceItem;
    private final StoriesRepository repository;
    private int page = 0;

    public DesignerNewsDataSource(SourceItem sourceItem, StoriesRepository repository) {
        this.sourceItem = sourceItem;
        this.repository = repository;
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        Result<List<Story>> result = repository.search(sourceItem.getKey(), page);
        return switch (result) {
            case Result.Success<List<Story>> success -> {
                page++;
                List<PlaidItem> stories = success.getData().stream()
                        .map(story -> story.toStory())
                        .toList();
                yield Result.Success(stories);
            }
            case Result.Error error -> error;
        };
    }
}