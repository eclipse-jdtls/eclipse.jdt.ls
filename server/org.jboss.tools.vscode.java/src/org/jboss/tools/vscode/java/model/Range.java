package org.jboss.tools.vscode.java.model;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO for returning Range.
 */
public class Range {
	public Position start;
	public Position end;
	public Map<String, Object> convertForRPC() {
		Map<String, Object> result= new HashMap<>();
		result.put("start", start.convertForRPC());
		result.put("end", end.convertForRPC());
		return result;
	}
}
