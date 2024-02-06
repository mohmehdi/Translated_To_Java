




package io.plaidapp.core.data;

import io.plaidapp.core.data.prefs.SourcesRepository;
import io.plaidapp.core.data.prefs.SourceItem;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem.Companion;
import io.plaidapp.core.designernews.domain.LoadStoriesUseCase;
import io.plaidapp.core.designernews.domain.SearchStoriesUseCase;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem.Companion;
import io.plaidapp.core.producthunt.domain.LoadPostsUseCase;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import io.plaidapp.core.util.exhaustive;
import java.util.ArrayList;
import java.util.AtomicInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;

public class DataManager implements DataLoadingSubject {

    private static class InFlightRequestData {
        private final String key;
        private final int page;

        public InFlightRequestData(String key, int page) {
            this.key = key;
            this.page = page;
        }
    }

    private final LoadStoriesUseCase loadStories;
    private final LoadPostsUseCase loadPosts;
    private final SearchStoriesUseCase searchStories;
    private final ShotsRepository shotsRepository;
    private final SourcesRepository sourcesRepository;
    private final CoroutineDispatcherProvider dispatcherProvider;

    private final Map<InFlightRequestData, Job> parentJobs = new ConcurrentHashMap<>();
    private final AtomicInteger loadingCount = new AtomicInteger(0);
    private final List<DataLoadingSubject.DataLoadingCallbacks> loadingCallbacks = new ArrayList<>();
    private OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback;
    private final Map<String, Integer> pageIndexes = new HashMap<>();

    private final FiltersChangedCallback filterListener = new FiltersChangedCallback() {
        @Override
        public void onFiltersChanged(SourceItem changedFilter) {
            if (changedFilter.isActive()) {
                loadSource(changedFilter);
            } else {
                String key = changedFilter.getKey();
                for (Map.Entry<InFlightRequestData, Job> entry : parentJobs.entrySet()) {
                    if (entry.getKey().getKey().equals(key)) {
                        entry.getValue().cancel();
                        parentJobs.remove(entry.getKey());
                    }
                }

                pageIndexes.put(key, 0);
            }
        }
    };

    public DataManager(
            LoadStoriesUseCase loadStories,
            LoadPostsUseCase loadPosts,
            SearchStoriesUseCase searchStories,
            ShotsRepository shotsRepository,
            SourcesRepository sourcesRepository,
            CoroutinesDispatcherProvider dispatcherProvider
    ) {
        this.loadStories = loadStories;
        this.loadPosts = loadPosts;
        this.searchStories = searchStories;
        this.shotsRepository = shotsRepository;
        this.sourcesRepository = sourcesRepository;
        this.dispatcherProvider = dispatcherProvider;

        sourcesRepository.registerFilterChangedCallback(filterListener);

        for (SourceItem source : sourcesRepository.getSourcesSync()) {
            pageIndexes.put(source.getKey(), 0);
        }
    }

    public void setOnDataLoadedCallback(OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback) {
        this.onDataLoadedCallback = onDataLoadedCallback;
    }

    private void onDataLoaded(List<PlaidItem> data) {
        onDataLoadedCallback.onDataLoaded(data);
    }

    public void loadMore() {
        for (SourceItem source : sourcesRepository.getSources()) {
            loadSource(source);
        }
    }

    public void cancelLoading() {
        for (Job job : parentJobs.values()) {
            job.cancel();
        }
        parentJobs.clear();
    }

    private void loadSource(SourceItem source) {
        if (source.isActive()) {
            loadStarted();
            int page = getNextPageIndex(source.getKey());

            InFlightRequestData data = new InFlightRequestData(source.getKey(), page);
            switch (source.getKey()) {
                case SOURCE_DESIGNER_NEWS_POPULAR:
                    parentJobs.put(data, launchLoadDesignerNewsStories(data));
                    break;
                case SOURCE_PRODUCT_HUNT:
                    parentJobs.put(data, launchLoadProductHunt(data));
                    break;
                default:
                    if (source instanceof DribbbleSourceItem) {
                        parentJobs.put(data, loadDribbbleSearch((DribbbleSourceItem) source, data));
                    } else if (source instanceof DesignerNewsSearchSourceItem) {
                        parentJobs.put(data, loadDesignerNewsSearch((DesignerNewsSearchSourceItem) source, data));
                    }
            }
        }
    }

    private int getNextPageIndex(String dataSource) {
        int nextPage = 1;
        if (pageIndexes.containsKey(dataSource)) {
            nextPage = pageIndexes.get(dataSource) + 1;
        }
        pageIndexes.put(dataSource, nextPage);
        return nextPage;
    }

    private boolean sourceIsEnabled(String key) {
        return pageIndexes.getOrDefault(key, 0) != 0;
    }

    private void sourceLoaded(
            List<PlaidItem> data,
            String source,
            InFlightRequestData request
    ) {
        loadFinished();
        if (data != null && !data.isEmpty() && sourceIsEnabled(source)) {
            setPage(data, request.getPage());
            setDataSource(data, source);
            onDataLoaded(data);
        }
        parentJobs.remove(request);
    }

    private void loadFailed(InFlightRequestData request) {
        loadFinished();
        parentJobs.remove(request);
    }

    private Job launchLoadDesignerNewsStories(InFlightRequestData data) {
        return scope.launch(dispatcherProvider.computation) {
            Result<List<PlaidItem>> result = loadStories.execute(data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(((Result.Success<List<PlaidItem>>) result).getData(),
                        SOURCE_DESIGNER_NEWS_POPULAR,
                        data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        }.exhaustive();
    }

    private Job loadDesignerNewsSearch(
            DesignerNewsSearchSourceItem sourceItem,
            InFlightRequestData data
    ) {
        return scope.launch(dispatcherProvider.computation) {
            Result<List<PlaidItem>> result = searchStories.execute(sourceItem.getKey(), data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), sourceItem.getKey(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        }.exhaustive();
    }

    private Job loadDribbbleSearch(DribbbleSourceItem source, InFlightRequestData data) {
        return scope.launch(dispatcherProvider.computation) {
            Result<List<PlaidItem>> result = shotsRepository.search(source.getQuery(), data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), source.getKey(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        }.exhaustive();
    }

    private Job launchLoadProductHunt(InFlightRequestData data) {
        return scope.launch(dispatcherProvider.computation) {
            Result<List<PlaidItem>> result = loadPosts.execute(data.getPage() - 1);
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), SOURCE_PRODUCT_HUNT, data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        }.exhaustive();
    }

    @Override
    public void registerCallback(DataLoadingSubject.DataLoadingCallbacks callback) {
        loadingCallbacks.add(callback);
    }

    private void loadStarted() {
        if (loadingCount.incrementAndGet() == 1) {
            dispatchLoadingStartedCallbacks();
        }
    }

    private void loadFinished() {
        if (loadingCount.decrementAndGet() == 0) {
            dispatchLoadingFinishedCallbacks();
        }
    }

    private void setPage(List<PlaidItem> items, int page) {
        for (PlaidItem item : items) {
            item.setPage(page);
        }
    }

    private void setDataSource(List<PlaidItem> items, String dataSource) {
        for (PlaidItem item : items) {
            item.setDataSource(dataSource);
        }
    }

    private void dispatchLoadingStartedCallbacks() {
        for (DataLoadingSubject.DataLoadingCallbacks callback : loadingCallbacks) {
            callback.dataStartedLoading();
        }
    }

    private void dispatchLoadingFinishedCallbacks() {
        for (DataLoadingSubject.DataLoadingCallbacks callback : loadingCallbacks) {
            callback.dataFinishedLoading();
        }
    }
}