package org.jboss.tools.langs.gen;

import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;

public class LSPRulesFactory extends RuleFactory {
	
    public Rule<JClassContainer, JType> getTypeRule() {
        return new LSPTypeRule(this);
    }
    
    @Override
    public Rule<JClassContainer, JType> getSchemaRule() {
    	return new LSPSchemaRule(this);
    }

}
