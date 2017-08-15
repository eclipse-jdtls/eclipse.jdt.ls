package org.eclipse.jdt.ls.debug.adapter.formatter;

import java.util.HashMap;
import java.util.Map;

import com.sun.jdi.Type;

public class SimpleTypeFormatter implements ITypeFormatter {
    public static final String QUALIFIED_CLASS_NAME_OPTION = "qualified_class_name";
    private static final boolean DEFAULT_QUALIFIED_CLASS_NAME_OPTION = false;

    /**
     * Format a JDI type, using the <code>SimpleTypeFormatter.QUALIFIED_FORMAT_OPTION</code> to control whether or not
     * to use the fully qualified name. Set QUALIFIED_FORMAT_OPTION to true(<code>java.lang.Boolean</code>) to enable
     * fully qualified name, the default option for QUALIFIED_FORMAT_OPTION is false.
     *
     * @param type the Jdi type
     * @param options the format options
     * @return the type name
     */
    @Override
    public String toString(Object type, Map<String, Object> options) {
        if (type == null) {
            return NullObjectFormatter.NULL_STRING;
        }

        String typeName = ((Type)type).name();
        return showQualifiedClassName(options) ? typeName : trimTypeName(typeName);
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return true;
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(QUALIFIED_CLASS_NAME_OPTION, DEFAULT_QUALIFIED_CLASS_NAME_OPTION);
        return options;
    }
    
    /**
     * An utility method for convert fully qualified class name to the simplified class name. 
     * @param type the fully qualified class name  
     * @return the simplified class name
     */
    public static String trimTypeName(String type) {
        if (type.indexOf('.') >= 0) {
            type = type.substring(type.lastIndexOf('.') + 1);
        }
        return type;
    }

    private static boolean showQualifiedClassName(Map<String, Object> options) {
        return options.containsKey(QUALIFIED_CLASS_NAME_OPTION)
                ? (Boolean)options.get(QUALIFIED_CLASS_NAME_OPTION) : DEFAULT_QUALIFIED_CLASS_NAME_OPTION;
    }
}
