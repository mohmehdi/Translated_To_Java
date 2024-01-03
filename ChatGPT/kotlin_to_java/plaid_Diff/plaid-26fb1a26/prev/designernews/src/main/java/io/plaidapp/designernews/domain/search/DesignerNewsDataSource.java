package io.plaidapp.designernews.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.util.exhaustive;

import java.util.List;

public class DesignerNewsDataSource extends PlaidDataSource {

    private int page = 0;
    private StoriesRepository repository;

    public DesignerNewsDataSource(SourceItem sourceItem, StoriesRepository repository) {
        super(sourceItem);
        this.repository = repository;
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        Result<List<Story>> result = repository.search(getSourceItem().getKey(), page);
        if (result instanceof Result.Success) {
            page++;
            List<PlaidItem> stories = ((Result.Success<List<Story>>) result).getData().stream()
                    .map(Story::toStory)
                    .collect(Collectors.toList());
            return new Result.Success<>(stories);
        } else if (result instanceof Result.Error) {
            return (Result<List<PlaidItem>>) result;
        }
        return null;
    }
}