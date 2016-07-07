package org.jboss.tools.vscode.ipc;

public enum MessageType {
	/**
	 * An error message
	 */
	Error (1),
	/**
	 * A warning message
	 */
	Warning (2),
	/**
	 * An info message
	 */
	Info (3),
	/**
	 * A basic logging message
	 */
	Log (4);
	
	int type;
	private MessageType(int type) {
		this.type = type;
	}
	
	public int getType() {
		return type;
	}
}
