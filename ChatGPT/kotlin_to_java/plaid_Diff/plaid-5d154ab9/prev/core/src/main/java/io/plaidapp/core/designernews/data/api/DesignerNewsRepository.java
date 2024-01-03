package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.LoadSourceCallback;
import io.plaidapp.core.data.prefs.SourceManager;
import io.plaidapp.core.designernews.data.api.model.Story;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesignerNewsRepository {
    private DesignerNewsService service;
    private Map<String, Call<?>> inflight;

    public DesignerNewsRepository(DesignerNewsService service) {
        this.service = service;
        this.inflight = new HashMap<>();
    }

    public void loadTopStories(int page, LoadSourceCallback callback) {
        Call<List<Story>> topStories;
        if (BuildConfig.DESIGNER_NEWS_V2) {
            topStories = service.getTopStoriesV2(page);
        } else {
            topStories = service.getTopStories(page);
        }
        topStories.enqueue(new Callback<List<Story>>() {
            @Override
            public void onResponse(Call<List<Story>> call, Response<List<Story>> response) {
                if (response.isSuccessful()) {
                    callback.sourceLoaded(response.body(), page, SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
                } else {
                    inflight.remove(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
                    callback.loadFailed(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
                }
            }

            @Override
            public void onFailure(Call<List<Story>> call, Throwable t) {
                inflight.remove(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
                callback.loadFailed(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR);
            }
        });
        inflight.put(SourceManager.SOURCE_DESIGNER_NEWS_POPULAR, topStories);
    }

    public void loadRecent(int page, LoadSourceCallback callback) {
        Call<List<Story>> recentStoriesCall;
        if (BuildConfig.DESIGNER_NEWS_V2) {
            recentStoriesCall = service.getRecentStoriesV2(page);
        } else {
            recentStoriesCall = service.getRecentStories(page);
        }
        recentStoriesCall.enqueue(new Callback<List<Story>>() {
            @Override
            public void onResponse(Call<List<Story>> call, Response<List<Story>> response) {
                if (response.isSuccessful()) {
                    callback.sourceLoaded(response.body(), page, SourceManager.SOURCE_DESIGNER_NEWS_RECENT);
                } else {
                    callback.loadFailed(SourceManager.SOURCE_DESIGNER_NEWS_RECENT);
                }
            }

            @Override
            public void onFailure(Call<List<Story>> call, Throwable t) {
                callback.loadFailed(SourceManager.SOURCE_DESIGNER_NEWS_RECENT);
            }
        });
        inflight.put(SourceManager.SOURCE_DESIGNER_NEWS_RECENT, recentStoriesCall);
    }

    public void search(String query, int page, LoadSourceCallback callback) {
        Call<List<Story>> searchCall = service.search(query, page);
        searchCall.enqueue(new Callback<List<Story>>() {
            @Override
            public void onResponse(Call<List<Story>> call, Response<List<Story>> response) {
                if (response.isSuccessful()) {
                    callback.sourceLoaded(response.body(), page, query);
                } else {
                    callback.loadFailed(query);
                }
            }

            @Override
            public void onFailure(Call<List<Story>> call, Throwable t) {
                callback.loadFailed(query);
            }
        });

        inflight.put(query, searchCall);
    }

    public void cancelAllRequests() {
        for (Call<?> request : inflight.values()) {
            request.cancel();
        }
    }

    public void cancelRequestOfSource(String source) {
        Call<?> request = inflight.get(source);
        if (request != null) {
            request.cancel();
        }
    }

    public static DesignerNewsRepository getInstance(DesignerNewsService service) {
        if (INSTANCE == null) {
            synchronized (DesignerNewsRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DesignerNewsRepository(service);
                }
            }
        }
        return INSTANCE;
    }
}