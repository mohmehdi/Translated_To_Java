

package com.squareup.leakcanary;

import com.squareup.leakcanary.Reachability.Status;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LeakTrace implements Serializable {
  private final List<LeakTraceElement> elements;
  private final List<Reachability> expectedReachability;

  public LeakTrace(List<LeakTraceElement> elements, List<Reachability> expectedReachability) {
    this.elements = elements;
    this.expectedReachability = expectedReachability;
  }

  @Override
  public String toString() {
    StringBuilder leakInfo = new StringBuilder("┬\n");
    LeakTraceElement lastElement = elements.get(elements.size() - 1);
    Reachability lastReachability = expectedReachability.get(expectedReachability.size() - 1);
    List<LeakTraceElement> elementsWithoutLast = elements.subList(0, elements.size() - 1);
    List<Reachability> expectedReachabilityWithoutLast = expectedReachability.subList(0, expectedReachability.size() - 1);

    for (int index = 0; index < elementsWithoutLast.size(); index++) {
      LeakTraceElement leakTraceElement = elementsWithoutLast.get(index);
      Reachability currentReachability = expectedReachabilityWithoutLast.get(index);

      leakInfo.append(String.format(
          "├─ %s\n│%s│%s\n",
          leakTraceElement.getClassName(),
          getReachabilityString(currentReachability),
          getPossibleLeakString(currentReachability, leakTraceElement, index)
      ));
    }

    leakInfo.append(String.format(
        "╰→ %s\n%s%s",
        lastElement.getClassName(),
        DEFAULT_NEWLINE_SPACE,
        getReachabilityString(lastReachability)
    ));

    return leakInfo.toString();
  }

  public String toDetailedString() {
    StringBuilder detailedString = new StringBuilder();
    for (LeakTraceElement element : elements) {
      detailedString.append(element.toDetailedString());
      detailedString.append("\n");
    }
    return detailedString.toString();
  }

  private String getPossibleLeakString(
      Reachability reachability,
      LeakTraceElement leakTraceElement,
      int index) {
    boolean maybeLeakCause = false;
    if (reachability.getStatus() == Status.UNKNOWN) {
      maybeLeakCause = true;
    } else if (reachability.getStatus() == Status.REACHABLE) {
      if (index < elements.size() - 1) {
        Reachability nextReachability = expectedReachability.get(index + 1);
        maybeLeakCause = nextReachability.getStatus() != Status.REACHABLE;
      } else {
        maybeLeakCause = true;
      }
    } else {
      maybeLeakCause = false;
    }
    return String.format("%s↓ %s", DEFAULT_NEWLINE_SPACE, leakTraceElement.toString(maybeLeakCause));
  }

  private String getReachabilityString(Reachability reachability) {
    return String.format("%sLeaking: %s", DEFAULT_NEWLINE_SPACE, getStatusString(reachability.getStatus()));
  }



  private static final String DEFAULT_NEWLINE_SPACE = "                 ";
  private static final String ZERO_WIDTH_SPACE = "\u200b";
}