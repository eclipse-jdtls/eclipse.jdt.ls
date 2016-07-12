package org.jboss.tools.vscode.java.model;

import java.util.HashMap;
import java.util.Map;

public class Position {
	/**
	 * Line position in a document (zero-based).
	 */
	public int line;
	/**
	 * Character offset on a line in a document (zero-based).
	 */
	public int character;
	public Position() {
	}
	public Position(int line, int character) {
		this.line= line;
		this.character= character;
	}
	
	public Map<String, Object> convertForRPC() {
		Map<String, Object> result= new HashMap<>();
		result.put("line", line);
		result.put("character", character);
		return result;
	}
	@Override
	public String toString() {
		return line + ":" + character;
	}
}