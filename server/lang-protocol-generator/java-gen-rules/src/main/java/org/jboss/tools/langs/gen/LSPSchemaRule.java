package org.jboss.tools.langs.gen;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;

/**
 * Applies a JSON schema.
 * 
 * @see <a
 *      href="http://tools.ietf.org/html/draft-zyp-json-schema-03#section-5">http://tools.ietf.org/html/draft-zyp-json-schema-03#section-5</a>
 */
public class LSPSchemaRule implements Rule<JClassContainer, JType> {

    private final RuleFactory ruleFactory;

    protected LSPSchemaRule(RuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    /**
     * Applies this schema rule to take the required code generation steps.
     * <p>
     * At the root of a schema document this rule should be applied (schema
     * documents contain a schema), but also in many places within the document.
     * Each property of type "object" is itself defined by a schema, the items
     * attribute of an array is a schema, the additionalProperties attribute of
     * a schema is also a schema.
     * <p>
     * Where the schema value is a $ref, the ref URI is assumed to be applicable
     * as a URL (from which content will be read). Where the ref URI has been
     * encountered before, the root Java type created by that schema will be
     * re-used (generation steps won't be repeated).
     * 
     * @param schema
     *            the schema within which this schema rule is being applied
     */
    public JType apply(String nodeName, JsonNode schemaNode, JClassContainer generatableType, Schema schema) {

        if (schemaNode.has("$ref")) {
            final String ref = schemaNode.get("$ref").asText();
			schema = ruleFactory.getSchemaStore().create(schema, ref);
            schemaNode = schema.getContent();

            if (schema.isGenerated()) {
                return schema.getJavaType();
            }
            if(ref.startsWith("#/")){
            	//Use the name from the reference instead of the first property
            	// this allows internal definitions such as definitions/MyObjectType 
            	//to create MyObjectType
            	nodeName = ref.substring(ref.lastIndexOf("/")+1);
            }
            return apply(nodeName, schemaNode, generatableType, schema);
        }

        JType javaType;
        if (schemaNode.has("enum")) {
            javaType = ruleFactory.getEnumRule().apply(nodeName, schemaNode, generatableType, schema);
        } else {
            javaType = ruleFactory.getTypeRule().apply(nodeName, schemaNode, generatableType.getPackage(), schema);
        }
        schema.setJavaTypeIfEmpty(javaType);

        return javaType;
    }
}
