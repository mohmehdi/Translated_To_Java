




package io.plaidapp.core.ui.filter;

import androidx.annotation.DrawableRes;
import io.plaidapp.core.util.event.Event;

import java.util.List;

public class SourceUiModel {
    public final String id;
    public final String key;
    public final String name;
    public final boolean active;
    @DrawableRes
    public final int iconRes;
    public final boolean isSwipeDismissable;
    private final Consumer<SourceUiModel> onSourceClicked;
    private final Consumer<SourceUiModel> onSourceDismissed;

    public SourceUiModel(String id, String key, String name, boolean active,
                         @DrawableRes int iconRes, boolean isSwipeDismissable,
                         Consumer<SourceUiModel> onSourceClicked,
                         Consumer<SourceUiModel> onSourceDismissed) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.active = active;
        this.iconRes = iconRes;
        this.isSwipeDismissable = isSwipeDismissable;
        this.onSourceClicked = onSourceClicked;
        this.onSourceDismissed = onSourceDismissed;
    }


}

public class SourcesUiModel {
    public final List<SourceUiModel> sourceUiModels;
    public final Event<SourcesHighlightUiModel> highlightSources;

    public SourcesUiModel(List<SourceUiModel> sourceUiModels,
                          Event<SourcesHighlightUiModel> highlightSources) {
        this.sourceUiModels = sourceUiModels;
        this.highlightSources = highlightSources;
    }
}
public class SourcesHighlightUiModel {
    private final List<Integer> highlightPositions;
    private final int scrollToPosition;

    public SourcesHighlightUiModel(List<Integer> highlightPositions, int scrollToPosition) {
        this.highlightPositions = highlightPositions;
        this.scrollToPosition = scrollToPosition;
    }

    
}