




package io.plaidapp.designernews.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.interfaces.PlaidDataSource;

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
        if (result instanceof Result.Success) {
            page++;
            List<Story> stories = result.getData();
            return new Result.Success<>(Story.toPlaidItems(stories));
        } else {
            return (Result<List<PlaidItem>>) result;
        }
    }
}