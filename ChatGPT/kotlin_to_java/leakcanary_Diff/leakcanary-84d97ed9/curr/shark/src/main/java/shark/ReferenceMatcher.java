
package shark;

import java.util.function.Predicate;

public abstract class ReferenceMatcher {

    public abstract ReferencePattern pattern;

}

public class LibraryLeakReferenceMatcher extends ReferenceMatcher {

    public ReferencePattern pattern;
    public String description = "";
    public Predicate<HeapGraph> patternApplies = heapGraph -> true;

    public LibraryLeakReferenceMatcher(ReferencePattern pattern) {
        this.pattern = pattern;
    }

}

public class IgnoredReferenceMatcher extends ReferenceMatcher {

    public ReferencePattern pattern;

    public IgnoredReferenceMatcher(ReferencePattern pattern) {
        this.pattern = pattern;
    }

}