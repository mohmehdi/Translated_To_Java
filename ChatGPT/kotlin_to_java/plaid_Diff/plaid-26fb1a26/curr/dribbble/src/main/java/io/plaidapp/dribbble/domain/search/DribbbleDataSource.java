package io.plaidapp.dribbble.domain.search;

import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.interfaces.PlaidDataSource;

public class DribbbleDataSource extends PlaidDataSource {

    private int page = 0;
    private ShotsRepository repository;

    public DribbbleDataSource(SourceItem sourceItem, ShotsRepository repository) {
        super(sourceItem);
        this.repository = repository;
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        Result<List<PlaidItem>> result = repository.search(sourceItem.getKey(), page);
        if (result instanceof Result.Success) {
            page++;
            return new Result.Success<>(result.getData());
        } else if (result instanceof Result.Error) {
            return result;
        }
        return null;
    }
}