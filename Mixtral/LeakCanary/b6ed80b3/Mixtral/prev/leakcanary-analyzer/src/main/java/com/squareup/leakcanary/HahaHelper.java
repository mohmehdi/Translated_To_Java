
//--------------------Class--------------------
//-------------------Extra---------------------
//---------------------------------------------
package com.squareup.leakcanary;

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
import java.util.Objects;

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
            String valueClassName = classInstance.classObj.className;
            if (valueClassName.equals(String.class.getName())) {
                stringValue = '"' + asString(value) + '"';
            } else {
                stringValue = value.toString();
            }
        } else {
            stringValue = value.toString();
        }
        return stringValue;
    }

    public static List<String> asStringArray(ArrayInstance arrayInstance) {
        List<String> entries = new ArrayList<>();
        for (Instance arrayEntry : arrayInstance.values) {
            entries.add(asString(arrayEntry));
        }
        return entries;
    }

    public static String asString(Object stringObject) {
        Instance instance = (Instance) stringObject;
        List<ClassInstance.FieldValue> values = classInstanceValues(instance);

        int count = (int) fieldValue(values, "count");
        if (count == 0) {
            return "";
        }

        Object value = fieldValue(values, "value");

        int offset;
        ArrayInstance array;
        if (isCharArray(value)) {
            array = (ArrayInstance) value;

            offset = 0;

            if (hasField(values, "offset")) {
                offset = (int) fieldValue(values, "offset");
            }

            char[] chars = array.asCharArray(offset, count);
            return new String(chars);
        } else if (isByteArray(value)) {

            array = (ArrayInstance) value;

            try {
                Object asRawByteArray = ArrayInstance.class.getDeclaredMethod(
                        "asRawByteArray", int.class, int.class
                );
                asRawByteArray.setAccessible(true);
                byte[] rawByteArray = (byte[]) asRawByteArray.invoke(array, 0, count);
                return new String(rawByteArray, Charset.forName("UTF-8"));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }

        } else {
            throw new UnsupportedOperationException("Could not find char array in " + instance);
        }
    }

    public static boolean isPrimitiveWrapper(Object value) {
        return value instanceof ClassInstance && WRAPPER_TYPES.contains(
                ((ClassInstance) value).classObj.className
        );
    }

    public static boolean isPrimitiveOrWrapperArray(Object value) {
        if (!(value instanceof ArrayInstance)) {
            return false;
        }
        return ((ArrayInstance) value).arrayType != Type.OBJECT
                || WRAPPER_TYPES.contains(((ArrayInstance) value).classObj.className);
    }

    private static boolean isCharArray(Object value) {
        return value instanceof ArrayInstance && ((ArrayInstance) value).arrayType == Type.CHAR;
    }

    private static boolean isByteArray(Object value) {
        return value instanceof ArrayInstance && ((ArrayInstance) value).arrayType == Type.BYTE;
    }

    public static List<ClassInstance.FieldValue> classInstanceValues(Instance instance) {
        ClassInstance classInstance = (ClassInstance) instance;
        return classInstance.values;
    }

    public static <T> Object fieldValue(
            List<ClassInstance.FieldValue> values,
            String fieldName
    ) {
        for (ClassInstance.FieldValue fieldValue : values) {
            if (Objects.equals(fieldValue.field.name, fieldName)) {
                @Suppress("UNCHECKED_CAST")
                return fieldValue.value;
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " does not exists");
    }

    public static boolean hasField(
            List<ClassInstance.FieldValue> values,
            String fieldName
    ) {
        for (ClassInstance.FieldValue fieldValue : values) {
            if (Objects.equals(fieldValue.field.name, fieldName)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T staticFieldValue(
            ClassObj classObj,
            String fieldName
    ) {
        for (Map.Entry<String, Object> entry : classObj.staticFieldValues.entrySet()) {
            if (entry.getKey().equals(fieldName)) {
                @Suppress("unchecked")
                return (T) entry.getValue();
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " does not exists");
    }
}