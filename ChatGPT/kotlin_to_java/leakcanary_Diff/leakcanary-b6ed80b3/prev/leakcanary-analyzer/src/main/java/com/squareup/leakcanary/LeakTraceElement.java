
package com.squareup.leakcanary;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import static com.squareup.leakcanary.LeakTraceElement.Holder.ARRAY;
import static com.squareup.leakcanary.LeakTraceElement.Holder.CLASS;
import static com.squareup.leakcanary.LeakTraceElement.Holder.THREAD;
import static com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;

public class LeakTraceElement implements Serializable {

  public LeakReference reference;
  public Holder holder;
  public List<String> classHierarchy;
  public String extra;
  public Exclusion exclusion;
  public List<LeakReference> fieldReferences;
  public String className = classHierarchy.get(0);

  public LeakTraceElement(LeakReference reference, Holder holder, List<String> classHierarchy,
                          String extra, Exclusion exclusion, List<LeakReference> fieldReferences) {
    this.reference = reference;
    this.holder = holder;
    this.classHierarchy = classHierarchy;
    this.extra = extra;
    this.exclusion = exclusion;
    this.fieldReferences = fieldReferences;

    List<String> stringFields = new ArrayList<>();
    for (LeakReference leakReference : fieldReferences) {
      stringFields.add(leakReference.toString());
    }
  }

  public String getFieldReferenceValue(String referenceName) {
    return fieldReferences.stream()
        .filter(fieldReference -> fieldReference.name.equals(referenceName))
        .findFirst()
        .map(LeakReference::getValue)
        .orElse(null);
  }

  public boolean isInstanceOf(Class<? extends Object> expectedClass) {
    return isInstanceOf(expectedClass.getName());
  }

  public boolean isInstanceOf(String expectedClassName) {
    return classHierarchy.stream()
        .anyMatch(className -> className.equals(expectedClassName));
  }

  public String getSimpleClassName() {
    int separator = className.lastIndexOf('.');
    return separator == -1 ? className : className.substring(separator + 1);
  }

  @Override
  public String toString() {
    return toString(false);
  }

  public String toString(boolean maybeLeakCause) {
    String staticString = (reference != null && reference.type == STATIC_FIELD) ? "static" : "";
    String holderString = (holder == ARRAY || holder == THREAD) ? holder.name().toLowerCase(Locale.US) + " " : "";
    String simpleClassName = getSimpleClassName();
    String referenceName = (reference != null) ? "." + reference.displayName : "";
    String extraString = (extra != null) ? " " + extra : "";
    String exclusionString = (exclusion != null) ? " , matching exclusion " + exclusion.matching : "";
    int requiredSpaces = staticString.length() + holderString.length() + simpleClassName.length();
    String leakString = maybeLeakCause ? "\n│                   " + " ".repeat(requiredSpaces) + "~".repeat(referenceName.length()) : "";

    return staticString + holderString + simpleClassName + referenceName + leakString + extraString + exclusionString;
  }

  public String toDetailedString() {
    String startingStarString = "*";
    String typeString = (holder == ARRAY) ? "Array of" : (holder == CLASS) ? "Class" : "Instance of";
    String classNameString = " " + className + "\n";
    String leakReferenceString = fieldReferences.stream()
        .map(LeakReference::toString)
        .collect(Collectors.joining("\n", "|   ", ""));
    return startingStarString + typeString + classNameString + leakReferenceString;
  }

  public enum Type {
    INSTANCE_FIELD,
    STATIC_FIELD,
    LOCAL,
    ARRAY_ENTRY
  }

  public enum Holder {
    OBJECT,
    CLASS,
    THREAD,
    ARRAY
  }
}