
package leakcanary.internal;

import android.content.res.Resources;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import leakcanary.LeakTrace;
import leakcanary.LeakTraceElement;
import leakcanary.Reachability;
import leakcanary.internal.DisplayLeakConnectorView.Type;

public class DisplayLeakAdapter extends BaseAdapter {

    private boolean[] opened = new boolean[0];

    private LeakTrace leakTrace;
    private String referenceKey;
    private String referenceName = "";

    private final String classNameColorHexString;
    private final String leakColorHexString;
    private final String referenceColorHexString;
    private final String extraColorHexString;
    private final String helpColorHexString;

    public DisplayLeakAdapter(Resources resources) {
        classNameColorHexString = hexStringColor(resources, R.color.leak_canary_class_name);
        leakColorHexString = hexStringColor(resources, R.color.leak_canary_leak);
        referenceColorHexString = hexStringColor(resources, R.color.leak_canary_reference);
        extraColorHexString = hexStringColor(resources, R.color.leak_canary_extra);
        helpColorHexString = hexStringColor(resources, R.color.leak_canary_help);
    }

@Override
public View getView(int position, View convertView, ViewGroup parent) {
    Context context = parent.getContext();
    if (getItemViewType(position) == TOP_ROW) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.leak_canary_ref_top_row, parent, false);
        }
        TextView textView = convertView.findViewById(R.id.leak_canary_row_text);
        textView.setText(context.getPackageName());
    } else {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.leak_canary_ref_row, parent, false);
        }

        TextView titleView = convertView.findViewById(R.id.leak_canary_row_title);
        TextView detailView = convertView.findViewById(R.id.leak_canary_row_details);
        DisplayLeakConnectorView connector = convertView.findViewById(R.id.leak_canary_row_connector);
        MoreDetailsView moreDetailsView = convertView.findViewById(R.id.leak_canary_row_more);

        connector.setType(getConnectorType(position));
        moreDetailsView.setOpened(opened[position]);

        if (opened[position]) {
            detailView.setVisibility(View.VISIBLE);
        } else {
            detailView.setVisibility(View.GONE);
        }

        Resources resources = convertView.getResources();
        if (position == 1) {
            titleView.setText(Html.fromHtml(
                    "<font color='" + helpColorHexString + "'>" +
                            "<b>" + resources.getString(R.string.leak_canary_help_title) + "</b>" +
                            "</font>"));
            SpannableStringBuilder detailText = (SpannableStringBuilder) Html.fromHtml(
                    resources.getString(R.string.leak_canary_help_detail));
            SquigglySpan.replaceUnderlineSpans(detailText, resources);
            detailView.setText(detailText);
        } else {
            boolean isLeakingInstance = (position == getCount() - 1);
            LeakTraceElement element = getItem(position);

            Reachability reachability = leakTrace.getExpectedReachability().get(elementIndex(position));
            boolean maybeLeakCause;
            if (isLeakingInstance || reachability.getStatus() == Reachability.Status.UNREACHABLE) {
                maybeLeakCause = false;
            } else {
                Reachability nextReachability = leakTrace.getExpectedReachability().get(elementIndex(position + 1));
                maybeLeakCause = nextReachability.getStatus() != Reachability.Status.REACHABLE;
            }

            Spanned htmlTitle = htmlTitle(element, maybeLeakCause, resources);

            titleView.setText(htmlTitle);

            if (opened[position]) {
                Spanned htmlDetail = htmlDetails(isLeakingInstance, element);
                detailView.setText(htmlDetail);
            }
        }
    }

    return convertView;
}

private Spanned htmlTitle(LeakTraceElement element, boolean maybeLeakCause, Resources resources) {
    String htmlString = "";

    String simpleName = element.getSimpleClassName();
    simpleName = simpleName.replace("[]", "[ ]");

    String styledClassName = "<font color='" + classNameColorHexString + "'>" + simpleName + "</font>";

    Reference reference = element.getReference();
    if (reference != null) {
        String referenceName = reference.getDisplayName().replace("<", "&lt;")
                .replace(">", "&gt;");

        if (maybeLeakCause) {
            referenceName = "<u><font color='" + leakColorHexString + "'>" + referenceName + "</font></u>";
        } else {
            referenceName = "<font color='" + referenceColorHexString + "'>" + referenceName + "</font>";
        }

        if (reference.getType() == Reference.Type.STATIC_FIELD) {
            referenceName = "<i>" + referenceName + "</i>";
        }

        String classAndReference = styledClassName + "." + referenceName;

        if (maybeLeakCause) {
            classAndReference = "<b>" + classAndReference + "</b>";
        }

        htmlString += classAndReference;
    } else {
        htmlString += styledClassName;
    }

    Exclusion exclusion = element.getExclusion();
    if (exclusion != null) {
        htmlString += " (excluded)";
    }
    SpannableStringBuilder builder = (SpannableStringBuilder) Html.fromHtml(htmlString);
    if (maybeLeakCause) {
        SquigglySpan.replaceUnderlineSpans(builder, resources);
    }

    return builder;
}

private Spanned htmlDetails(boolean isLeakingInstance, LeakTraceElement element) {
    String htmlString = "";
    if (element.getExtra() != null) {
        htmlString += " <font color='" + extraColorHexString + "'>" + element.getExtra() + "</font>";
    }

    Exclusion exclusion = element.getExclusion();
    if (exclusion != null) {
        htmlString += "<br/><br/>Excluded by rule";
        if (exclusion.getName() != null) {
            htmlString += " <font color='#ffffff'>" + exclusion.getName() + "</font>";
        }
        htmlString += " matching <font color='#f3cf83'>" + exclusion.getMatching() + "</font>";
        if (exclusion.getReason() != null) {
            htmlString += " because <font color='#f3cf83'>" + exclusion.getReason() + "</font>";
        }
    }
    htmlString += ("<br>"
            + "<font color='" + extraColorHexString + "'>"
            + element.toDetailedString().replace("\n", "<br>")
            + "</font>");

    if (isLeakingInstance && referenceName != "") {
        htmlString += " <font color='" + extraColorHexString + "'>" + referenceName + "</font>";
    }

    return Html.fromHtml(htmlString);
}

private Type getConnectorType(int position) {
    if (position == 1) {
        return Type.HELP;
    } else if (position == 2) {
        if (leakTrace.getExpectedReachability().size() == 1) {
            return Type.START_LAST_REACHABLE;
        }
        Reachability nextReachability = leakTrace.getExpectedReachability().get(elementIndex(position + 1));
        return (nextReachability.getStatus() != Reachability.Status.REACHABLE) ?
                Type.START_LAST_REACHABLE : Type.START;
    } else {
        boolean isLeakingInstance = (position == getCount() - 1);
        if (isLeakingInstance) {
            Reachability previousReachability = leakTrace.getExpectedReachability().get(elementIndex(position - 1));
            return (previousReachability.getStatus() != Reachability.Status.UNREACHABLE) ?
                    Type.END_FIRST_UNREACHABLE : Type.END;
        } else {
            Reachability reachability = leakTrace.getExpectedReachability().get(elementIndex(position));
            switch (reachability.getStatus()) {
                case UNKNOWN:
                    return Type.NODE_UNKNOWN;
                case REACHABLE:
                    Reachability nextReachability = leakTrace.getExpectedReachability().get(elementIndex(position + 1));
                    return (nextReachability.getStatus() != Reachability.Status.REACHABLE) ?
                            Type.NODE_LAST_REACHABLE : Type.NODE_REACHABLE;
                case UNREACHABLE:
                    Reachability previousReachability = leakTrace.getExpectedReachability().get(elementIndex(position - 1));
                    return (previousReachability.getStatus() != Reachability.Status.UNREACHABLE) ?
                            Type.NODE_FIRST_UNREACHABLE : Type.NODE_UNREACHABLE;
                default:
                    throw new IllegalStateException("Unknown value: " + reachability.getStatus());
            }
        }
    }
}
    public void update(LeakTrace leakTrace, String referenceKey, String referenceName) {
        if (referenceKey.equals(this.referenceKey)) {
            // Same data, nothing to change.
            return;
        }
        this.referenceKey = referenceKey;
        this.referenceName = referenceName;
        this.leakTrace = leakTrace;
        opened = new boolean[2 + leakTrace.getElements().size()];
        notifyDataSetChanged();
    }

    public void toggleRow(int position) {
        opened[position] = !opened[position];
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return (leakTrace == null) ? 2 : 2 + leakTrace.getElements().size();
    }

    @Override
    public LeakTraceElement getItem(int position) {
        if (getItemViewType(position) == TOP_ROW) {
            return null;
        }
        return (position == 1) ? null : leakTrace.getElements().get(elementIndex(position));
    }

    private int elementIndex(int position) {
        return position - 2;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? TOP_ROW : NORMAL_ROW;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static String hexStringColor(Resources resources, @ColorRes int colorResId) {
        return String.format("#%06X", 0xFFFFFF & resources.getColor(colorResId));
    }

    private static <T extends View> T findById(View view, int id) {
        return (T) view.findViewById(id);
    }
}