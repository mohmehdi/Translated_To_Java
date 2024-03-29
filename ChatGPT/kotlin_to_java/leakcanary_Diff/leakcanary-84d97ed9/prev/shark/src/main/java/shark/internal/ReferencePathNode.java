package shark.internal;

import shark.GcRoot;
import shark.LeakReference;
import shark.ReferenceMatcher.LibraryLeakReferenceMatcher;

public abstract class ReferencePathNode {
    public abstract long instance;

    public static class RootNode extends ReferencePathNode {
        public final GcRoot gcRoot;
        public final long instance;

        public RootNode(GcRoot gcRoot, long instance) {
            this.gcRoot = gcRoot;
            this.instance = instance;
        }
    }

    public static abstract class ChildNode extends ReferencePathNode {
        public abstract ReferencePathNode parent;
        public abstract LeakReference referenceFromParent;

        public static class LibraryLeakNode extends ChildNode {
            public final long instance;
            public final ReferencePathNode parent;
            public final LeakReference referenceFromParent;
            public final LibraryLeakReferenceMatcher matcher;

            public LibraryLeakNode(long instance, ReferencePathNode parent, LeakReference referenceFromParent, LibraryLeakReferenceMatcher matcher) {
                this.instance = instance;
                this.parent = parent;
                this.referenceFromParent = referenceFromParent;
                this.matcher = matcher;
            }
        }

        public static class NormalNode extends ChildNode {
            public final long instance;
            public final ReferencePathNode parent;
            public final LeakReference referenceFromParent;

            public NormalNode(long instance, ReferencePathNode parent, LeakReference referenceFromParent) {
                this.instance = instance;
                this.parent = parent;
                this.referenceFromParent = referenceFromParent;
            }
        }
    }
}