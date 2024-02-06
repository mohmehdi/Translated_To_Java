




package io.plaidapp.core.designernews.data.stories;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.stories.model.StoryResponse;
import retrofit2.Response;
import java.io.IOException;

public class StoriesRemoteDataSource {

    private final DesignerNewsService service;

    public StoriesRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<StoryResponse>> loadStories(int page) {
        try {
            Response<List<StoryResponse>> response = service.getStories(page).execute();
            return getResult(response, () -> new Result.Error(
                    new IOException("Error getting stories " + response.code() + " " + response.message())
            ));
        } catch (IOException e) {
            return new Result.Error(new IOException("Error getting stories", e));
        }
    }

    public Result<List<StoryResponse>> search(String query, int page) {
        String queryWithoutPrefix = query.replace(DesignerNewsSearchSourceItem.DESIGNER_NEWS_QUERY_PREFIX, "");
        try {
            Response<List<StoryResponse>> searchResults = service.search(queryWithoutPrefix, page).execute();
            if (searchResults.isSuccessful() && searchResults.body() != null && !searchResults.body().isEmpty()) {
                StringBuilder commaSeparatedIds = new StringBuilder();
                for (StoryResponse storyResponse : searchResults.body()) {
                    if (commaSeparatedIds.length() > 0) {
                        commaSeparatedIds.append(",");
                    }
                    commaSeparatedIds.append(storyResponse.getId());
                }
                return loadStories(Integer.parseInt(commaSeparatedIds.toString()));
            } else {
                return new Result.Error(new IOException("Error searching " + queryWithoutPrefix));
            }
        } catch (IOException e) {
            return new Result.Error(new IOException("Error searching " + queryWithoutPrefix, e));
        }
    }

    private Result<List<StoryResponse>> loadStories(int commaSeparatedIds) {
        try {
            Response<List<StoryResponse>> response = service.getStories(commaSeparatedIds).execute();
            return getResult(response, () -> new Result.Error(
                    new IOException("Error getting stories " + response.code() + " " + response.message())
            ));
        } catch (IOException e) {
            return new Result.Error(new IOException("Error getting stories", e));
        }
    }

    public Result<List<StoryResponse>> loadStories(String commaSeparatedIds) {
    try {
        Response<List<StoryResponse>> response = service.getStories(commaSeparatedIds).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Error getting stories " + response.code() + " " + response.message());
        }
        return Result.success(response.body());
    } catch (ExecutionException | InterruptedException e) {
        return Result.error(new IOException("Error getting stories", e));
    } catch (IOException e) {
        return Result.error(new IOException("Error getting stories", e));
    }
}

    private Result<List<StoryResponse>> getResult(Response<List<StoryResponse>> response, Supplier<Result.Error> onError) {
        if (response.isSuccessful()) {
            List<StoryResponse> body = response.body();
            if (body != null) {
                return new Result.Success(body);
            }
        }
        return onError.get();
    }

    public static StoriesRemoteDataSource getInstance(DesignerNewsService service) {
        StoriesRemoteDataSource INSTANCE = StoriesRemoteDataSourceHolder.INSTANCE;
        if (INSTANCE == null) {
            synchronized (StoriesRemoteDataSourceHolder.class) {
                INSTANCE = StoriesRemoteDataSourceHolder.INSTANCE;
                if (INSTANCE == null) {
                    INSTANCE = new StoriesRemoteDataSource(service);
                    StoriesRemoteDataSourceHolder.INSTANCE = INSTANCE;
                }
            }
        }
        return INSTANCE;
    }

}