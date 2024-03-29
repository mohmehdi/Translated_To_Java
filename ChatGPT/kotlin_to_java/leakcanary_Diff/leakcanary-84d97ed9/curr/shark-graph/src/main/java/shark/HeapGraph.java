

package shark;

import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;

import java.util.List;
import java.util.Sequence;

public interface HeapGraph {
    int getIdentifierByteSize();

    GraphContext getContext();

    List<GcRoot> getGcRoots();

    Sequence<HeapObject> getObjects();

    Sequence<HeapClass> getClasses();

    Sequence<HeapInstance> getInstances();

    HeapObject findObjectById(long objectId) throws IllegalArgumentException;

    HeapClass findClassByName(String className);

    boolean objectExists(long objectId);
}