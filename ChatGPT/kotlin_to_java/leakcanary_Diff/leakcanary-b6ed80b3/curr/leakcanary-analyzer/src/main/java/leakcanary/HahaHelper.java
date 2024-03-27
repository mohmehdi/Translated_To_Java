
package leakcanary;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Type;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class HahaHelper {

  private static final HashSet<String> WRAPPER_TYPES = new HashSet<>(
      Arrays.asList(
          Boolean.class.getName(), Character.class.getName(), Float.class.getName(),
          Double.class.getName(), Byte.class.getName(), Short.class.getName(),
          Integer.class.getName(), Long.class.getName()
      )
  );

  public static String threadName(Instance holder) {
    List<ClassInstance.FieldValue> values = classInstanceValues(holder);
    Object nameField = fieldValue(values, "name");
    if (nameField == null) {
      return "Thread name not available";
    }
    return asString(nameField);
  }

  public static boolean extendsThread(ClassObj clazz) {
    boolean extendsThread = false;
    ClassObj parentClass = clazz;
    while (parentClass.superClassObj != null) {
      if (parentClass.className.equals(Thread.class.getName())) {
        extendsThread = true;
        break;
      }
      parentClass = parentClass.superClassObj;
    }
    return extendsThread;
  }

  public static String valueAsString(Object value) {
    String stringValue;
    if (value == null) {
      stringValue = "null";
    } else if (value instanceof ClassInstance) {
      ClassInstance classInstance = (ClassInstance) value;
      String valueClassName = classInstance.getClassObj().className;
      if (valueClassName.equals(String.class.getName())) {
        stringValue = "\"" + asString(classInstance) + "\"";
      } else {
        stringValue = value.toString();
      }
    } else {
      stringValue = value.toString();
    }
    return stringValue;
  }

  public static List<String> asStringArray(ArrayInstance arrayInstance) {
    List<String> entries = new java.util.ArrayList<>();
    for (Instance arrayEntry : arrayInstance.getValues()) {
      entries.add(asString(arrayEntry));
    }
    return entries;
  }

  public static String asString(Object stringObject) {
    Instance instance = (Instance) stringObject;
    List<ClassInstance.FieldValue> values = classInstanceValues(instance);

    int count = fieldValue(values, "count");
    if (count == 0) {
      return "";
    }

    Object value = fieldValue(values, "value");

    Integer offset;
    ArrayInstance array;
    if (isCharArray(value)) {
      array = (ArrayInstance) value;
      offset = 0;

      if (hasField(values, "offset")) {
        offset = fieldValue(values, "offset");
      }

      char[] chars = array.asCharArray(offset, count);
      return new String(chars);
    } else if (isByteArray(value)) {
      array = (ArrayInstance) value;

      try {
        java.lang.reflect.Method asRawByteArray = ArrayInstance.class.getDeclaredMethod(
            "asRawByteArray", int.class, int.class
        );
        asRawByteArray.setAccessible(true);
        byte[] rawByteArray = (byte[]) asRawByteArray.invoke(array, 0, count);
        return new String(rawByteArray, Charset.forName("UTF-8"));
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

    } else {
      throw new UnsupportedOperationException("Could not find char array in " + instance);
    }
  }

  public static boolean isPrimitiveWrapper(Object value) {
    if (!(value instanceof ClassInstance)) {
      return false;
    }
    ClassInstance classInstance = (ClassInstance) value;
    return WRAPPER_TYPES.contains(classInstance.getClassObj().className);
  }

  public static boolean isPrimitiveOrWrapperArray(Object value) {
    if (!(value instanceof ArrayInstance)) {
      return false;
    }
    ArrayInstance arrayInstance = (ArrayInstance) value;
    if (arrayInstance.getArrayType() != Type.OBJECT) {
      return true;
    }
    ClassInstance classInstance = arrayInstance.getClassObj();
    return WRAPPER_TYPES.contains(classInstance.className);
  }

  private static boolean isCharArray(Object value) {
    return value instanceof ArrayInstance && ((ArrayInstance) value).getArrayType() == Type.CHAR;
  }

  private static boolean isByteArray(Object value) {
    return value instanceof ArrayInstance && ((ArrayInstance) value).getArrayType() == Type.BYTE;
  }

  public static List<ClassInstance.FieldValue> classInstanceValues(Instance instance) {
    ClassInstance classInstance = (ClassInstance) instance;
    return classInstance.getValues();
  }

  public static <T> T fieldValue(
    List<ClassInstance.FieldValue> values,
    String fieldName
  ) {
    for (ClassInstance.FieldValue fieldValue : values) {
      if (fieldValue.getField().getName().equals(fieldName)) {
        @SuppressWarnings("unchecked")
        T value = (T) fieldValue.getValue();
        return value;
      }
    }
    throw new IllegalArgumentException("Field " + fieldName + " does not exist");
  }

  public static boolean hasField(
    List<ClassInstance.FieldValue> values,
    String fieldName
  ) {
    for (ClassInstance.FieldValue fieldValue : values) {
      if (fieldValue.getField().getName().equals(fieldName)) {
        return true;
      }
    }
    return false;
  }

  public static <T> T staticFieldValue(
    ClassObj classObj,
    String fieldName
  ) {
    for (ClassObj.FieldValue entry : classObj.getStaticFieldValues().entrySet()) {
      if (entry.getKey().getName().equals(fieldName)) {
        @SuppressWarnings("unchecked")
        T value = (T) entry.getValue();
        return value;
      }
    }
    throw new IllegalArgumentException("Field " + fieldName + " does not exist");
  }
}