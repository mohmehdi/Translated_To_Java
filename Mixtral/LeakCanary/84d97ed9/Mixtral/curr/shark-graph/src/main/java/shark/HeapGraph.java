

package shark;

import java.util.List;
import java.util.Sequence;

public interface HeapGraph {
  int identifierByteSize();
  
  GraphContext context();
  
  List<GcRoot> gcRoots();
  
  Sequence<HeapObject> objects();
  
  Sequence<HeapClass> classes();
  
  Sequence<HeapInstance> instances();

  HeapObject findObjectById(long objectId) throws IllegalArgumentException;

  HeapClass findClassByName(String className);

  boolean objectExists(long objectId);
}