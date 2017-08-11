package org.eclipse.jdt.ls.debug.adapter.formatter;

import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.ARRAY;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.CLASS_LOADER;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.CLASS_OBJECT;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.OBJECT;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.STRING;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.THREAD;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.THREAD_GROUP;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class ObjectFormatter implements IValueFormatter {

    /**
     * The format type function for this object.
     */
    protected final BiFunction<Type, Map<String, Object>, String> typeToStringFunction;

    public ObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeToStringFunction) {
        this.typeToStringFunction = typeToStringFunction;
    }

    @Override
    public String toString(Object obj, Map<String, Object> options) {
        return String.format("%s %s", getPrefix((ObjectReference) obj, options),
                getIdPostfix((ObjectReference) obj, options));
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char tag = type.signature().charAt(0);
        return (tag == OBJECT) || (tag == ARRAY) || (tag == STRING)
                || (tag == THREAD) || (tag == THREAD_GROUP)
                || (tag == CLASS_LOADER)
                || (tag == CLASS_OBJECT);
    }

    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        if (value == null || NullObjectFormatter.NULL_STRING.equals(value)) {
            return null;
        }
        throw new UnsupportedOperationException(String.format("Set value is not supported yet for type %s.", type.name()));
    }

    /**
     * The type with additional prefix before id=${id} of this object.(eg: class, array length)
     * @param value The object value.
     * @param options additional information about expected format
     * @return the type name with additional text
     */
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        return typeToStringFunction.apply(value.type(), options);
    }

    protected static String getIdPostfix(ObjectReference obj, Map<String, Object> options) {
        return String.format("(id=%s)", NumericFormatter.formatNumber(obj.uniqueID(), options));
    }
}
