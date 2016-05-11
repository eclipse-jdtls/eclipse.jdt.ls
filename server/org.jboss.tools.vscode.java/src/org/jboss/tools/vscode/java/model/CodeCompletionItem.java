package org.jboss.tools.vscode.java.model;

import org.eclipse.jdt.core.CompletionProposal;

/**
 * VS Code specific code completion
 * 
 * @author Gorkem Ercan
 *
 */
final public class CodeCompletionItem {
	
	private final CompletionProposal kernel;
	private String insertText;
	private String label;
	
	public CodeCompletionItem(CompletionProposal proposal) {
		this.kernel = proposal;
	}

	public CompletionProposal getRootProposal() {
		return kernel;
	}
	
	public int getKind(){
		return mapKind(kernel.getKind());
	}

	/**
	 * @return the insertText
	 */
	public String getInsertText() {
		return insertText;
	}

	/**
	 * @param insertText the insertText to set
	 */
	public void setInsertText(String insertText) {
		this.insertText = insertText;
	}
	
	/**
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @param label the label to set
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	private int mapKind(final int kind) {
		switch (kind) {

		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return 4;//Constructor
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.TYPE_REF:
			return 7;// Class
		case CompletionProposal.FIELD_IMPORT:
		case CompletionProposal.METHOD_IMPORT:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.PACKAGE_REF:
		case CompletionProposal.TYPE_IMPORT:
			return 9;//Module
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			return 5;//Field
		case CompletionProposal.KEYWORD:
			return 14;//Keyword
		case CompletionProposal.LABEL_REF:
			return 18;//Reference
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return 6; //Variable
		case CompletionProposal.METHOD_DECLARATION:
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return 3;//Function
		//text
		case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
		case CompletionProposal.JAVADOC_BLOCK_TAG:
		case CompletionProposal.JAVADOC_FIELD_REF:
		case CompletionProposal.JAVADOC_INLINE_TAG:
		case CompletionProposal.JAVADOC_METHOD_REF:
		case CompletionProposal.JAVADOC_PARAM_REF:
		case CompletionProposal.JAVADOC_TYPE_REF:
		case CompletionProposal.JAVADOC_VALUE_REF:
		default:
			return 1; //Text
		}
// vscode kinds		
//			Text = 1,
//				  Method = 2,
//				  Function = 3,
//				  Constructor = 4,
//				  Field = 5,
//				  Variable = 6,
//				  Class = 7,
//				  Interface = 8,
//				  Module = 9,
//				  Property = 10,
//				  Unit = 11,
//				  Value = 12,
//				  Enum = 13,
//				  Keyword = 14,
//				  Snippet = 15,
//				  Color = 16,
//				  File = 17,
//				  Reference = 18
	}
	
}
