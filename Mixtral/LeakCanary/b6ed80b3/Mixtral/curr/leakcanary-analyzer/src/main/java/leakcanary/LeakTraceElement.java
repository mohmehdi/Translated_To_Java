

package leakcanary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class LeakTraceElement implements Serializable {

    private LeakReference reference;
    private Holder holder;
    private List<String> classHierarchy;
    private String extra;
    private Exclusion exclusion;
    private List<LeakReference> fieldReferences;

    public LeakTraceElement(
            LeakReference reference,
            Holder holder,
            List<String> classHierarchy,
            String extra,
            Exclusion exclusion,
            List<LeakReference> fieldReferences
    ) {
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

    public String getFieldReferenceValue(String referenceName) {
        return fieldReferences.stream()
                .filter(fieldReference -> fieldReference.getName().equals(referenceName))
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
        return (separator == -1) ? className : className.substring(separator + 1);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean maybeLeakCause) {
        String staticString = (reference != null && reference.getType() == STATIC_FIELD) ? "static" : "";
        String holderString = (holder == Holder.ARRAY || holder == Holder.THREAD) ? holder.name.toLowerCase(Locale.US) + " " : "";
        String simpleClassName = getSimpleClassName();
        String referenceName = (reference != null) ? "." + reference.getDisplayName() : "";
        String extraString = (extra != null) ? " " + extra : "";
        String exclusionString = (exclusion != null) ? " , matching exclusion " + exclusion.getMatching() : "";
        int requiredSpaces = staticString.length() + holderString.length() + simpleClassName.length();
        String leakString = (maybeLeakCause) ?
                "\n│                   " + " ".repeat(requiredSpaces) + "~".repeat(referenceName.length()) :
                "";

        return staticString + holderString + simpleClassName + referenceName + leakString + extraString + exclusionString;
    }

    public String toDetailedString() {
        String startingStarString = "*";
        String typeString = (holder == Holder.ARRAY) ? "Array of" :
                (holder == Holder.CLASS) ? "Class" : "Instance of";
        String classNameString = " " + className + "\n";
        String leakReferenceString = fieldReferences.stream()
                .map(LeakReference::toString)
                .collect(Collectors.joining("\n", "|   ", ""));
        return startingStarString + typeString + classNameString + leakReferenceString;
    }
}