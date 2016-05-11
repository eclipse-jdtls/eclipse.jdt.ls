package org.jboss.tools.vscode.java.model;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/**
 * POJO for SymbolInformation
 * 
 * @author Gorkem Ercan
 *
 */
public class SymbolInformation {
	  
	private String name;
	private int kind;
	private Location location;
	private String containerName;
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the kind
	 */
	public int getKind() {
		return kind;
	}
	/**
	 * @param kind the kind to set
	 */
	public void setKind(int kind) {
		this.kind = kind;
	}
	/**
	 * @return the location
	 */
	public Location getLocation() {
		return location;
	}
	/**
	 * @param location the location to set
	 */
	public void setLocation(Location location) {
		this.location = location;
	}
	/**
	 * @return the containerName
	 */
	public String getContainerName() {
		return containerName;
	}
	/**
	 * @param containerName the containerName to set
	 */
	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}
	
	
	public static int mapKind(IJavaElement element) {
//		/**
//		* A symbol kind.
//		*/
//		export enum SymbolKind {
//		  File = 1,
//		  Module = 2,
//		  Namespace = 3,
//		  Package = 4,
//		  Class = 5,
//		  Method = 6,
//		  Property = 7,
//		  Field = 8,
//		  Constructor = 9,
//		  Enum = 10,
//		  Interface = 11,
//		  Function = 12,
//		  Variable = 13,
//		  Constant = 14,
//		  String = 15,
//		  Number = 16,
//		  Boolean = 17,
//		  Array = 18,
//		}
		switch (element.getElementType()) {
		case IJavaElement.ANNOTATION:
			return 7; // TODO: find a better mapping 
		case IJavaElement.CLASS_FILE:
		case IJavaElement.COMPILATION_UNIT:
			return 1;
		case IJavaElement.FIELD:
			return 8;
		case IJavaElement.IMPORT_CONTAINER:
		case IJavaElement.IMPORT_DECLARATION:
			return 2;
		case IJavaElement.INITIALIZER:
			return 9;
		case IJavaElement.LOCAL_VARIABLE:
		case IJavaElement.TYPE_PARAMETER:
			return 13;
		case IJavaElement.METHOD:
			return 12;
		case IJavaElement.PACKAGE_DECLARATION:
			return 3;
		case IJavaElement.TYPE:
			try {
				return ( ((IType)element).isInterface() ? 11 : 5);
			} catch (JavaModelException e) {
				return 5; //fallback 
			}
		}
		return 15;
	}
	
}
