




package io.plaidapp.dribbble.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.interfaces.PlaidDataSource;

public class DribbbleDataSource implements PlaidDataSource {
    private final SourceItem sourceItem;
    private final ShotsRepository repository;
    private int page = 0;

    public DribbbleDataSource(SourceItem sourceItem, ShotsRepository repository) {
        this.sourceItem = sourceItem;
        this.repository = repository;
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        Result<List<PlaidItem>> result = repository.search(sourceItem.key, page);
        if (result instanceof Result.Success) {
            page++;
        }
        return result;
    }
}