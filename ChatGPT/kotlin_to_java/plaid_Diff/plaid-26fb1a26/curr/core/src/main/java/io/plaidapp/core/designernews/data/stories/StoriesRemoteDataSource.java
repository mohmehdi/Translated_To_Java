package io.plaidapp.core.designernews.data.stories;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import retrofit2.Response;

import java.io.IOException;

public class StoriesRemoteDataSource {

    private DesignerNewsService service;

    public StoriesRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<StoryResponse>> loadStories(int page) {
        try {
            Response<List<StoryResponse>> response = service.getStories(page).execute();
            return getResult(response, () -> new Result.Error(
                    new IOException("Error getting stories " + response.code() + " " + response.message())
            ));
        } catch (Exception e) {
            return new Result.Error(new IOException("Error getting stories", e));
        }
    }

    public Result<List<StoryResponse>> search(String query, int page) {
        String queryWithoutPrefix = query.replace(DesignerNewsSearchSourceItem.DESIGNER_NEWS_QUERY_PREFIX, "");
        try {
            Response<List<String>> searchResults = service.search(queryWithoutPrefix, page).execute();
            List<String> ids = searchResults.body();
            if (searchResults.isSuccessful() && ids != null && !ids.isEmpty()) {
                String commaSeparatedIds = TextUtils.join(",", ids);
                return loadStories(commaSeparatedIds);
            } else {
                return new Result.Error(new IOException("Error searching " + queryWithoutPrefix));
            }
        } catch (Exception e) {
            return new Result.Error(new IOException("Error searching " + queryWithoutPrefix, e));
        }
    }

    private Result<List<StoryResponse>> loadStories(String commaSeparatedIds) {
        try {
            Response<List<StoryResponse>> response = service.getStories(commaSeparatedIds).execute();
            return getResult(response, () -> new Result.Error(
                    new IOException("Error getting stories " + response.code() + " " + response.message())
            ));
        } catch (Exception e) {
            return new Result.Error(new IOException("Error getting stories", e));
        }
    }

    private Result<List<StoryResponse>> getResult(Response<List<StoryResponse>> response, Runnable onError) {
        if (response.isSuccessful()) {
            List<StoryResponse> body = response.body();
            if (body != null) {
                return new Result.Success<>(body);
            }
        }
        return onError.run();
    }

    private static volatile StoriesRemoteDataSource INSTANCE;

    public static StoriesRemoteDataSource getInstance(DesignerNewsService service) {
        if (INSTANCE == null) {
            synchronized (StoriesRemoteDataSource.class) {
                if (INSTANCE == null) {
                    INSTANCE = new StoriesRemoteDataSource(service);
                }
            }
        }
        return INSTANCE;
    }
}