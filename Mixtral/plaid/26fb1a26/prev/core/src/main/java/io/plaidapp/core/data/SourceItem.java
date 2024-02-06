




package io.plaidapp.core.data;

import androidx.annotation.DrawableRes;
import java.util.Comparator;

public abstract class SourceItem {
    protected final String key;
    protected final int sortOrder;
    protected final String name;
    @DrawableRes
    protected final int iconRes;
    protected boolean active;
    protected final boolean isSwipeDismissable;

    public SourceItem(String key, int sortOrder, String name,
                      @DrawableRes int iconRes, boolean active, boolean isSwipeDismissable) {
        this.key = key;
        this.sortOrder = sortOrder;
        this.name = name;
        this.iconRes = iconRes;
        this.active = active;
        this.isSwipeDismissable = isSwipeDismissable;
    }

    @Override
    public String toString() {
        return "SourceItem{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                '}';
    }

    public static class SourceComparator implements Comparator<SourceItem> {
        @Override
        public int compare(SourceItem lhs, SourceItem rhs) {
            return Integer.compare(lhs.sortOrder, rhs.sortOrder);
        }
    }
}