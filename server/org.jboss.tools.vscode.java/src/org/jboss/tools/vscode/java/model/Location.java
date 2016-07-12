package org.jboss.tools.vscode.java.model;

/**
 * POJO for returning the location information
 * 
 * @author Gorkem Ercan
 *
 */
public class Location {

	private String uri;
	private int line;
	private  int column;
	private int endLine;
	private int endColumn;
	
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public int getLine() {
		return line;
	}
	public void setLine(int line) {
		this.line = line;
	}
	public int getColumn() {
		return column;
	}
	public void setColumn(int column) {
		this.column = column;
	}
	/**
	 * @return the endLine
	 */
	public int getEndLine() {
		return endLine;
	}
	/**
	 * @param endLine the endLine to set
	 */
	public void setEndLine(int endLine) {
		this.endLine = endLine;
	}
	/**
	 * @return the endColumn
	 */
	public int getEndColumn() {
		return endColumn;
	}
	/**
	 * @param endColumn the endColumn to set
	 */
	public void setEndColumn(int endColumn) {
		this.endColumn = endColumn;
	}
	@Override
	public String toString() {
		return line + ":" + column + " .. " + endLine + ":" + endColumn
				+ "\n" +  uri;
	}
}
