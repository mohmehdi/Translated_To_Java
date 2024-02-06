




package io.plaidapp.dribbble.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.util.exhaustive;

public class DribbbleDataSource implements PlaidDataSource {
    private final SourceItem sourceItem;
    private final ShotsRepository repository;
    private int page;

    public DribbbleDataSource(SourceItem sourceItem, ShotsRepository repository) {
        this.sourceItem = sourceItem;
        this.repository = repository;
        this.page = 0;
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        Result<List<PlaidItem>> result = repository.search(sourceItem.getKey(), page);
        if (result instanceof Result.Success) {
            page++;
        }
        return exhaustive().apply(result);
    }
}