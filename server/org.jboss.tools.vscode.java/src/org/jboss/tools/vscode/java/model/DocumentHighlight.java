package org.jboss.tools.vscode.java.model;

import java.util.HashMap;
import java.util.Map;

public class DocumentHighlight {
	public static final int TEXT = 1;
	public static final int READ = 2;
	public static final int WRITE = 3;

	public Range range;
	public Integer kind;

	public Map<String, Object> convertForRPC(){
		Map<String, Object> result= new HashMap<>();
		result.put("range", range.convertForRPC());
		if (kind != null) {
			result.put("kind", kind);
		}
		return result;
	}
}
