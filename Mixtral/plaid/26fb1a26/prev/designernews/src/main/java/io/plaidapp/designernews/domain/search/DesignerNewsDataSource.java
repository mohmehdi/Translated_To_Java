




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
public suspend Result<List<PlaidItem>> loadMore() {
    Result<List<PlaidItemData>> result = repository.search(sourceItem.getKey(), page);
    if (result instanceof Result.Success) {
        page++;
        List<PlaidItemData> dataList = result.getData();
        List<PlaidItem> stories = new ArrayList<>();
        for (PlaidItemData data : dataList) {
            stories.add(data.toStory());
        }
        return new Result.Success<>(stories);
    } else {
        return (Result<List<PlaidItem>>) result;
    }
}
}