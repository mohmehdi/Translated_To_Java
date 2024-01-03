package io.plaidapp.core.ui.filter;

import androidx.annotation.DrawableRes;
import io.plaidapp.core.util.event.Event;

public class SourceUiModel {
    private String id;
    private String key;
    private String name;
    private boolean active;
    @DrawableRes
    private int iconRes;
    private boolean isSwipeDismissable;
    private OnSourceClickedListener onSourceClicked;
    private OnSourceDismissedListener onSourceDismissed;

    public SourceUiModel(String id, String key, String name, boolean active, int iconRes,
                         boolean isSwipeDismissable, OnSourceClickedListener onSourceClicked,
                         OnSourceDismissedListener onSourceDismissed) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.active = active;
        this.iconRes = iconRes;
        this.isSwipeDismissable = isSwipeDismissable;
        this.onSourceClicked = onSourceClicked;
        this.onSourceDismissed = onSourceDismissed;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public int getIconRes() {
        return iconRes;
    }

    public boolean isSwipeDismissable() {
        return isSwipeDismissable;
    }

    public OnSourceClickedListener getOnSourceClicked() {
        return onSourceClicked;
    }

    public OnSourceDismissedListener getOnSourceDismissed() {
        return onSourceDismissed;
    }

    public interface OnSourceClickedListener {
        void onSourceClicked(SourceUiModel source);
    }

    public interface OnSourceDismissedListener {
        void onSourceDismissed(SourceUiModel source);
    }
}

public class SourcesUiModel {
    private List<SourceUiModel> sourceUiModels;
    private Event<SourcesHighlightUiModel> highlightSources;

    public SourcesUiModel(List<SourceUiModel> sourceUiModels, Event<SourcesHighlightUiModel> highlightSources) {
        this.sourceUiModels = sourceUiModels;
        this.highlightSources = highlightSources;
    }

    public List<SourceUiModel> getSourceUiModels() {
        return sourceUiModels;
    }

    public Event<SourcesHighlightUiModel> getHighlightSources() {
        return highlightSources;
    }
}

public class SourcesHighlightUiModel {
    private List<Integer> highlightPositions;
    private int scrollToPosition;

    public SourcesHighlightUiModel(List<Integer> highlightPositions, int scrollToPosition) {
        this.highlightPositions = highlightPositions;
        this.scrollToPosition = scrollToPosition;
    }

    public List<Integer> getHighlightPositions() {
        return highlightPositions;
    }

    public int getScrollToPosition() {
        return scrollToPosition;
    }
}