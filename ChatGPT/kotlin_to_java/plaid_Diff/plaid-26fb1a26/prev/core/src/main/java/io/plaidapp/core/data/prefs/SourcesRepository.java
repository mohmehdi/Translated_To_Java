package io.plaidapp.core.data.prefs;

import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import kotlinx.coroutines.withContext;

import java.util.Collections;
import java.util.List;

public class SourcesRepository {

    private List<SourceItem> defaultSources;
    private SourcesLocalDataSource dataSource;
    private CoroutinesDispatcherProvider dispatcherProvider;
    private List<SourceItem> cache;
    private List<FiltersChangedCallback> callbacks;

    public SourcesRepository(List<SourceItem> defaultSources, SourcesLocalDataSource dataSource,
                             CoroutinesDispatcherProvider dispatcherProvider) {
        this.defaultSources = defaultSources;
        this.dataSource = dataSource;
        this.dispatcherProvider = dispatcherProvider;
        this.cache = new ArrayList<>();
        this.callbacks = new ArrayList<>();
    }

    public void registerFilterChangedCallback(FiltersChangedCallback callback) {
        callbacks.add(callback);
    }

    public List<SourceItem> getSources() {
        return withContext(dispatcherProvider.getIo()) {
            return getSourcesSync();
        }
    }

    @Deprecated
    public List<SourceItem> getSourcesSync() {
        if (!cache.isEmpty()) {
            return cache;
        }

        List<String> sourceKeys = dataSource.getKeys();
        if (sourceKeys == null) {
            addSources(defaultSources);
            return defaultSources;
        }

        List<SourceItem> sources = new ArrayList<>();
        for (String sourceKey : sourceKeys) {
            boolean activeState = dataSource.getSourceActiveState(sourceKey);
            if (sourceKey.startsWith(DribbbleSourceItem.DRIBBBLE_QUERY_PREFIX)) {
                String query = sourceKey.replace(DribbbleSourceItem.DRIBBBLE_QUERY_PREFIX, "");
                sources.add(new DribbbleSourceItem(query, activeState));
            } else if (sourceKey.startsWith(DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX)) {
                String query = sourceKey.replace(DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX, "");
                sources.add(new DesignerNewsSearchSource(query, activeState));
            } else if (isDeprecatedDesignerNewsSource(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else if (isDeprecatedDribbbleV1Source(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else {
                SourceItem source = getSourceFromDefaults(sourceKey, activeState);
                if (source != null) {
                    sources.add(source);
                }
            }
        }
        Collections.sort(sources, new SourceItem.SourceComparator());
        cache.addAll(sources);
        dispatchSourcesUpdated();
        return cache;
    }

    public void addSources(List<SourceItem> sources) {
        for (SourceItem source : sources) {
            dataSource.addSource(source.getKey(), source.isActive());
        }
        cache.addAll(sources);
        dispatchSourcesUpdated();
    }

    public void addOrMarkActiveSources(List<SourceItem> sources) {
        List<SourceItem> sourcesToAdd = new ArrayList<>();
        for (SourceItem toAdd : sources) {
            boolean sourcePresent = false;
            for (int i = 0; i < cache.size(); i++) {
                SourceItem existing = cache.get(i);
                if (existing.getClass() == toAdd.getClass() &&
                        existing.getKey().equalsIgnoreCase(toAdd.getKey())) {
                    sourcePresent = true;
                    if (!existing.isActive()) {
                        changeSourceActiveState(existing.getKey());
                    }
                }
            }
            if (!sourcePresent) {
                sourcesToAdd.add(toAdd);
            }
        }
        addSources(sourcesToAdd);
    }

    public void changeSourceActiveState(String sourceKey) {
        for (SourceItem item : cache) {
            if (item.getKey().equals(sourceKey)) {
                boolean newActiveState = !item.isActive();
                item.setActive(newActiveState);
                dataSource.updateSource(sourceKey, newActiveState);
                dispatchSourceChanged(item);
                break;
            }
        }
        dispatchSourcesUpdated();
    }

    public void removeSource(String sourceKey) {
        dataSource.removeSource(sourceKey);
        cache.removeIf(item -> item.getKey().equals(sourceKey));
        dispatchSourceRemoved(sourceKey);
        dispatchSourcesUpdated();
    }

    public int getActiveSourcesCount() {
        return getSourcesSync().stream().filter(SourceItem::isActive).count();
    }

    private SourceItem getSourceFromDefaults(String key, boolean active) {
        for (SourceItem source : defaultSources) {
            if (source.getKey().equals(key)) {
                source.setActive(active);
                return source;
            }
        }
        return null;
    }

    private void dispatchSourcesUpdated() {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFiltersUpdated(cache);
        }
    }

    private void dispatchSourceChanged(SourceItem source) {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFiltersChanged(source);
        }
    }

    private void dispatchSourceRemoved(String sourceKey) {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFilterRemoved(sourceKey);
        }
    }

    public static volatile SourcesRepository INSTANCE;

    public static SourcesRepository getInstance(List<SourceItem> defaultSources,
                                                SourcesLocalDataSource dataSource,
                                                CoroutinesDispatcherProvider dispatcherProvider) {
        if (INSTANCE == null) {
            synchronized (SourcesRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SourcesRepository(defaultSources, dataSource, dispatcherProvider);
                }
            }
        }
        return INSTANCE;
    }
}