package shark;

public abstract class ReferenceMatcher {

    public abstract ReferencePattern pattern;

    public static class LibraryLeakReferenceMatcher extends ReferenceMatcher {
        public ReferencePattern pattern;
        public String description;
        public Function1<HeapGraph, Boolean> patternApplies;

        public LibraryLeakReferenceMatcher(ReferencePattern pattern, String description, Function1<HeapGraph, Boolean> patternApplies) {
            this.pattern = pattern;
            this.description = description;
            this.patternApplies = patternApplies;
        }
    }

    public static class IgnoredReferenceMatcher extends ReferenceMatcher {
        public ReferencePattern pattern;

        public IgnoredReferenceMatcher(ReferencePattern pattern) {
            this.pattern = pattern;
        }
    }
}