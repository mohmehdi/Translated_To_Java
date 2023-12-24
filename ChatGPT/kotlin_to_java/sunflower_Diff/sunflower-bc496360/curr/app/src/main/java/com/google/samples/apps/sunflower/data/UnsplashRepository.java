
package com.google.samples.apps.sunflower.data;

import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import com.google.samples.apps.sunflower.api.UnsplashService;
import kotlinx.coroutines.flow.Flow;
import javax.inject.Inject;

public class UnsplashRepository {

    private UnsplashService service;

    @Inject
    public UnsplashRepository(UnsplashService service) {
        this.service = service;
    }

    public Flow<PagingData<UnsplashPhoto>> getSearchResultStream(String query) {
        return new Pager<>(
                new PagingConfig(false, NETWORK_PAGE_SIZE),
                () -> new UnsplashPagingSource(service, query)
        ).flow;
    }

    private static final int NETWORK_PAGE_SIZE = 25;
}
