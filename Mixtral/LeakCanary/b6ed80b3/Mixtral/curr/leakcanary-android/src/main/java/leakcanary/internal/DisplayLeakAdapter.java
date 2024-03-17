
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
import leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.R.color;
import com.squareup.leakcanary.R.id;
import leakcanary.Reachability;
import leakcanary.internal.DisplayLeakConnectorView;
import leakcanary.internal.DisplayLeakConnectorView.Type;
import leakcanary.internal.DisplayLeakConnectorView.Type.END;
import leakcanary.internal.DisplayLeakConnectorView.Type.END_FIRST_UNREACHABLE;
import leakcanary.internal.DisplayLeakConnectorView.Type.HELP;
import leakcanary.internal.DisplayLeakConnectorView.Type.NODE_FIRST_UNREACHABLE;
import leakcanary.internal.DisplayLeakConnectorView.Type.NODE_LAST_REACHABLE;
import leakcanary.internal.DisplayLeakConnectorView.Type.NODE_REACHABLE;
import leakcanary.internal.DisplayLeakConnectorView.Type.NODE_UNKNOWN;
import leakcanary.internal.DisplayLeakConnectorView.Type.NODE_UNREACHABLE;
import leakcanary.internal.DisplayLeakConnectorView.Type.START;
import leakcanary.internal.DisplayLeakConnectorView.Type.START_LAST_REACHABLE;
import leakcanary.internal.MoreDetailsView;
import leakcanary.internal.SquigglySpan;

import java.util.BooleanArray;

public class DisplayLeakAdapter extends BaseAdapter {

  private BooleanArray opened;
  private LeakTrace leakTrace;
  private String referenceKey;
  private String referenceName;
  private final String classNameColorHexString;
  private final String leakColorHexString;
  private final String referenceColorHexString;
  private final String extraColorHexString;
  private final String helpColorHexString;

  public DisplayLeakAdapter(Resources resources) {
    classNameColorHexString = hexStringColor(resources, color.leak_canary_class_name);
    leakColorHexString = hexStringColor(resources, color.leak_canary_leak);
    referenceColorHexString = hexStringColor(resources, color.leak_canary_reference);
    extraColorHexString = hexStringColor(resources, color.leak_canary_extra);
    helpColorHexString = hexStringColor(resources, color.leak_canary_help);
    opened = new BooleanArray(0);
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    Context context = parent.getContext();

    if (getItemViewType(position) == TOP_ROW) {
      if (convertView == null) {
        LayoutInflater inflater = LayoutInflater.from(context);
        convertView = inflater.inflate(R.layout.leak_canary_ref_top_row, parent, false);
      }

      TextView textView = convertView.findViewById(id.leak_canary_row_text);
      textView.setText(context.getPackageName());
    } else {
      if (convertView == null) {
        LayoutInflater inflater = LayoutInflater.from(context);
        convertView = inflater.inflate(R.layout.leak_canary_ref_row, parent, false);
      }

      TextView titleView = convertView.findViewById(id.leak_canary_row_title);
      TextView detailView = convertView.findViewById(id.leak_canary_row_details);
      DisplayLeakConnectorView connector = convertView.findViewById(id.leak_canary_row_connector);
      MoreDetailsView moreDetailsView = convertView.findViewById(id.leak_canary_row_more);

      connector.setType(getConnectorType(position));
      moreDetailsView.setOpened(opened[position]);

      if (opened[position]) {
        detailView.setVisibility(View.VISIBLE);
      } else {
        detailView.setVisibility(View.GONE);
      }

      Resources resources = convertView.getResources();
      if (position == 1) {
        String helpColorHexString = "#your_help_color_hex_string";
        String htmlTitle = "<font color='" + helpColorHexString + "'>" + "<b>" + resources.getString(R.string.leak_canary_help_title) + "</b>" + "</font>";
        titleView.setText(Html.fromHtml(htmlTitle));

        String detailText = resources.getString(R.string.leak_canary_help_detail);
        SpannableStringBuilder spannableStringBuilder = (SpannableStringBuilder) Html.fromHtml(detailText);
        SquigglySpan.replaceUnderlineSpans(spannableStringBuilder, resources);
        detailView.setText(spannableStringBuilder);
      } else {
        boolean isLeakingInstance = position == count - 1;
        Object element = getItem(position);

        Reachability reachability = leakTrace.expectedReachability[elementIndex(position)];
        boolean maybeLeakCause;
        if (isLeakingInstance || reachability.status == Reachability.Status.UNREACHABLE) {
          maybeLeakCause = false;
        } else {
          Reachability nextReachability = leakTrace.expectedReachability[elementIndex(position + 1)];
          maybeLeakCause = nextReachability.status != Reachability.Status.REACHABLE;
        }

        String htmlTitle = htmlTitle(element, maybeLeakCause, resources);
        titleView.setText(Html.fromHtml(htmlTitle));

        if (opened[position]) {
          String htmlDetail = htmlDetails(isLeakingInstance, element);
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

      if (reference.getType() == Reference.TYPE_STATIC_FIELD) {
        referenceName = "<i>" + referenceName + "</i>";
      }

      String classAndReference = "$styledClassName.$referenceName";

      if (maybeLeakCause) {
        classAndReference = "<b>$classAndReference</b>";
      }

      htmlString += classAndReference;
    } else {
      htmlString += styledClassName;
    }

    Reference exclusion = element.getExclusion();
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
    if (element.extra != null) {
      htmlString += " <font color='" + extraColorHexString + "'>" + element.extra + "</font>";
    }

    Exclusion exclusion = element.exclusion;
    if (exclusion != null) {
      htmlString += "<br/><br/>Excluded by rule";
      if (exclusion.name != null) {
        htmlString += " <font color='#ffffff'>" + exclusion.name + "</font>";
      }
      htmlString += " matching <font color='#f3cf83'>" + exclusion.matching + "</font>";
      if (exclusion.reason != null) {
        htmlString += " because <font color='#f3cf83'>" + exclusion.reason + "</font>";
      }
    }
    htmlString += ("<br>" +
      "<font color='" + extraColorHexString + "'>" +
      element.toDetailedString().replace("\n", "<br>") +
      "</font>");

    if (isLeakingInstance && referenceName != null && !referenceName.isEmpty()) {
      htmlString += " <font color='" + extraColorHexString + "'>" + referenceName + "</font>";
    }

    return Html.fromHtml(htmlString);
  }

  private Type getConnectorType(int position) {
    if (position == 1) {
      return HELP;
    } else if (position == 2) {
      if (leakTrace != null && leakTrace.expectedReachability.size == 1) {
        Reachability.Status status = leakTrace.expectedReachability.get(elementIndex(position + 1)).status;
        if (status != Reachability.Status.REACHABLE) {
          return START_LAST_REACHABLE;
        }
      }
      return START;
    } else {
      boolean isLeakingInstance = position == count - 1;
      if (isLeakingInstance) {
        Reachability.Status previousStatus = Reachability.Status.UNREACHABLE;
        if (leakTrace != null) {
          previousStatus = leakTrace.expectedReachability.get(elementIndex(position - 1)).status;
        }
        if (previousStatus != Reachability.Status.UNREACHABLE) {
          return END_FIRST_UNREACHABLE;
        }
        return END;
      } else {
        Reachability reachability = null;
        if (leakTrace != null) {
          reachability = leakTrace.expectedReachability.get(elementIndex(position));
        }
        if (reachability == null) {
          throw new IllegalStateException("Unknown value: " + reachability.status);
        }
        Reachability.Status status = reachability.status;
        if (status == Reachability.Status.UNKNOWN) {
          return NODE_UNKNOWN;
        } else if (status == Reachability.Status.REACHABLE) {
          Reachability nextReachability = null;
          if (leakTrace != null) {
            nextReachability = leakTrace.expectedReachability.get(elementIndex(position + 1));
          }
          if (nextReachability == null || nextReachability.status != Reachability.Status.REACHABLE) {
            return NODE_LAST_REACHABLE;
          }
          return NODE_REACHABLE;
        } else if (status == Reachability.Status.UNREACHABLE) {
          Reachability previousReachability = null;
          if (leakTrace != null) {
            previousReachability = leakTrace.expectedReachability.get(elementIndex(position - 1));
          }
          if (previousReachability == null || previousReachability.status != Reachability.Status.UNREACHABLE) {
            return NODE_FIRST_UNREACHABLE;
          }
          return NODE_UNREACHABLE;
        } else {
          throw new IllegalStateException("Unknown value: " + status);
        }
      }
    }
  }

  public void update(LeakTrace leakTrace, String referenceKey, String referenceName) {
    if (referenceKey.equals(this.referenceKey)) {
      return;
    }
    this.referenceKey = referenceKey;
    this.referenceName = referenceName;
    this.leakTrace = leakTrace;
    this.opened = new boolean[2 + (leakTrace != null ? leakTrace.elements.size : 0)];
    notifyDataSetChanged();
  }

  public void toggleRow(int position) {
    opened[position] = !opened[position];
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return leakTrace != null ? 2 + leakTrace.elements.size : 2;
  }

  @Override
  public LeakTraceElement getItem(int position) {
    if (getItemViewType(position) == TOP_ROW) {
      return null;
    }
    if (position == 1) {
      return null;
    } else {
      return leakTrace != null ? leakTrace.elements.get(elementIndex(position)) : null;
    }
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
    return position == 0 ? TOP_ROW : NORMAL_ROW;
  }

  @Override
  public long getItemId(int position) {
    return (long) position;
  }

  private static final int TOP_ROW = 0;
  private static final int NORMAL_ROW = 1;

  private static String hexStringColor(Resources resources, @ColorRes int colorResId) {
    return String.format("#%06X", 0xFFFFFF & resources.getColor(colorResId));
  }

  @Suppress("unchecked")
  private static T findById(View view, int id) {
    return (T) view.findViewById(id);
  }

}