
package com.google.samples.apps.sunflower.data;

import androidx.paging.PagingSource;
import androidx.paging.PagingState;
import com.google.samples.apps.sunflower.api.UnsplashService;

import java.util.List;

private static final int UNSPLASH_STARTING_PAGE_INDEX = 1;

public class UnsplashPagingSource extends PagingSource<Integer, UnsplashPhoto> {

    private UnsplashService service;
    private String query;

    public UnsplashPagingSource(UnsplashService service, String query) {
        this.service = service;
        this.query = query;
    }

    @Override
    public LoadResult<Integer, UnsplashPhoto> load(LoadParams<Integer> params) throws Exception {
        int page = params.getKey() != null ? params.getKey() : UNSPLASH_STARTING_PAGE_INDEX;
        try {
            UnsplashResponse response = service.searchPhotos(query, page, params.getLoadSize());
            List<UnsplashPhoto> photos = response.getResults();
            return LoadResult.Page(
                    photos,
                    page == UNSPLASH_STARTING_PAGE_INDEX ? null : page - 1,
                    page == response.getTotalPages() ? null : page + 1
            );
        } catch (Exception exception) {
            return LoadResult.Error(exception);
        }
    }

    @Override
    public Integer getRefreshKey(PagingState<Integer, UnsplashPhoto> state) {
        Integer anchorPosition = state.getAnchorPosition();
        if (anchorPosition != null) {
            Integer closestPage = state.closestPageToPosition(anchorPosition);
            if (closestPage != null) {
                return closestPage.prevKey;
            }
        }
        return null;
    }
}
