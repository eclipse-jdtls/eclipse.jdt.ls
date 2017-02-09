/*******************************************************************************
 * Copyright (c) 2005, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Pivotal Software, Inc. - adopted for Flux
 *     Red Hat - adopted for JDT Server
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.lsp4j.CompletionItem;

/**
 * Provides string labels for completionProposals.
 * Functionality is copied from org.eclipse.jdt.ui.text.java.CompletionProposalLabelProvider
 *
 * Copied from Flux project.
 *
 */
public class CompletionProposalDescriptionProvider {

	private static final String RETURN_TYPE_SEPARATOR = " : ";
	private static final String PACKAGE_NAME_SEPARATOR = " - ";
	private static final String VAR_TYPE_SEPARATOR = RETURN_TYPE_SEPARATOR;


	/**
	 * The completion context.
	 */
	private CompletionContext fContext;

	/**
	 * Creates a new label provider.
	 * @param iCompilationUnit
	 */
	public CompletionProposalDescriptionProvider(CompletionContext context) {
		super();
		fContext = context;
	}

	/**
	 * Creates and returns a parameter list of the given method or type proposal suitable for
	 * display. The list does not include parentheses. The lower bound of parameter types is
	 * returned.
	 * <p>
	 * Examples:
	 *
	 * <pre>
	 *   &quot;void method(int i, String s)&quot; -&gt; &quot;int i, String s&quot;
	 *   &quot;? extends Number method(java.lang.String s, ? super Number n)&quot; -&gt; &quot;String s, Number n&quot;
	 * </pre>
	 *
	 * </p>
	 *
	 * @param proposal the proposal to create the parameter list for
	 * @return the list of comma-separated parameters suitable for display
	 */
	public StringBuilder createParameterList(CompletionProposal proposal) {
		int kind= proposal.getKind();
		switch (kind) {
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return appendUnboundedParameterList(new StringBuilder(), proposal);
		case CompletionProposal.TYPE_REF:
		case CompletionProposal.JAVADOC_TYPE_REF:
			return appendTypeParameterList(new StringBuilder(), proposal);
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			return appendUnboundedParameterList(new StringBuilder(), proposal);
		default:
			Assert.isLegal(false);
			return null; // dummy
		}
	}

	/**
	 * Appends the parameter list to <code>buffer</code>.
	 *
	 * @param buffer the buffer to append to
	 * @param methodProposal the method proposal
	 * @return the modified <code>buffer</code>
	 */
	private StringBuilder appendUnboundedParameterList(StringBuilder buffer, CompletionProposal methodProposal) {
		// TODO remove once https://bugs.eclipse.org/bugs/show_bug.cgi?id=85293
		// gets fixed.
		char[] signature= SignatureUtil.fix83600(methodProposal.getSignature());
		char[][] parameterNames= methodProposal.findParameterNames(null);
		char[][] parameterTypes= Signature.getParameterTypes(signature);

		for (int i= 0; i < parameterTypes.length; i++)
			parameterTypes[i]= createTypeDisplayName(SignatureUtil.getLowerBound(parameterTypes[i]));

		if (Flags.isVarargs(methodProposal.getFlags())) {
			int index= parameterTypes.length - 1;
			parameterTypes[index]= convertToVararg(parameterTypes[index]);
		}
		return appendParameterSignature(buffer, parameterTypes, parameterNames);
	}

	/**
	 * Appends the type parameter list to <code>buffer</code>.
	 *
	 * @param buffer the buffer to append to
	 * @param typeProposal the type proposal
	 * @return the modified <code>buffer</code>
	 */
	private StringBuilder appendTypeParameterList(StringBuilder buffer, CompletionProposal typeProposal) {
		// TODO remove once https://bugs.eclipse.org/bugs/show_bug.cgi?id=85293
		// gets fixed.
		char[] signature= SignatureUtil.fix83600(typeProposal.getSignature());
		char[][] typeParameters= Signature.getTypeArguments(signature);
		for (int i= 0; i < typeParameters.length; i++) {
			char[] param= typeParameters[i];
			typeParameters[i]= Signature.toCharArray(param);
		}
		return appendParameterSignature(buffer, typeParameters, null);
	}

	/**
	 * Converts the display name for an array type into a variable arity display name.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li> "int[]" -> "int..."</li>
	 * <li> "Object[][]" -> "Object[]..."</li>
	 * <li> "String" -> "String"</li>
	 * </ul>
	 * </p>
	 * <p>
	 * If <code>typeName</code> does not include the substring "[]", it is returned unchanged.
	 * </p>
	 *
	 * @param typeName the type name to convert
	 * @return the converted type name
	 */
	private char[] convertToVararg(char[] typeName) {
		if (typeName == null)
			return typeName;
		final int len= typeName.length;
		if (len < 2)
			return typeName;

		if (typeName[len - 1] != ']')
			return typeName;
		if (typeName[len - 2] != '[')
			return typeName;

		char[] vararg= new char[len + 1];
		System.arraycopy(typeName, 0, vararg, 0, len - 2);
		vararg[len - 2]= '.';
		vararg[len - 1]= '.';
		vararg[len]= '.';
		return vararg;
	}

	/**
	 * Returns the display string for a java type signature.
	 *
	 * @param typeSignature the type signature to create a display name for
	 * @return the display name for <code>typeSignature</code>
	 * @throws IllegalArgumentException if <code>typeSignature</code> is not a
	 *         valid signature
	 * @see Signature#toCharArray(char[])
	 * @see Signature#getSimpleName(char[])
	 */
	private char[] createTypeDisplayName(char[] typeSignature) throws IllegalArgumentException {
		char[] displayName= Signature.getSimpleName(Signature.toCharArray(typeSignature));

		// XXX see https://bugs.eclipse.org/bugs/show_bug.cgi?id=84675
		boolean useShortGenerics= false;
		if (useShortGenerics) {
			StringBuilder buf= new StringBuilder();
			buf.append(displayName);
			int pos;
			do {
				pos= buf.indexOf("? extends "); //$NON-NLS-1$
				if (pos >= 0) {
					buf.replace(pos, pos + 10, "+"); //$NON-NLS-1$
				} else {
					pos= buf.indexOf("? super "); //$NON-NLS-1$
					if (pos >= 0)
						buf.replace(pos, pos + 8, "-"); //$NON-NLS-1$
				}
			} while (pos >= 0);
			return buf.toString().toCharArray();
		}
		return displayName;
	}

	/**
	 * Creates a display string of a parameter list (without the parentheses)
	 * for the given parameter types and names.
	 *
	 * @param buffer the string buffer
	 * @param parameterTypes the parameter types
	 * @param parameterNames the parameter names
	 * @return the display string of the parameter list defined by the passed arguments
	 */
	private final StringBuilder appendParameterSignature(StringBuilder buffer, char[][] parameterTypes, char[][] parameterNames) {
		if (parameterTypes != null) {
			for (int i = 0; i < parameterTypes.length; i++) {
				if (i > 0) {
					buffer.append(',');
					buffer.append(' ');
				}
				buffer.append(parameterTypes[i]);
				if (parameterNames != null && parameterNames[i] != null) {
					buffer.append(' ');
					buffer.append(parameterNames[i]);
				}
			}
		}
		return buffer;
	}

	/**
	 * Updates a display label for the given method proposal to item. The display label
	 * consists of:
	 * <ul>
	 *   <li>the method name</li>
	 *   <li>the parameter list (see {@link #createParameterList(CompletionProposal)})</li>
	 *   <li>the upper bound of the return type (see {@link SignatureUtil#getUpperBound(String)})</li>
	 *   <li>the raw simple name of the declaring type</li>
	 * </ul>
	 * <p>
	 * Examples:
	 * For the <code>get(int)</code> method of a variable of type <code>List<? extends Number></code>, the following
	 * display name is returned: <code>get(int index)  Number - List</code>.<br>
	 * For the <code>add(E)</code> method of a variable of type <code>List<? super Number></code>, the following
	 * display name is returned: <code>add(Number o)  void - List</code>.<br>
	 * </p>
	 *
	 * @param methodProposal the method proposal to display
	 * @param item to update
	 */
	private void createMethodProposalLabel(CompletionProposal methodProposal, CompletionItem item) {
		StringBuilder description = new StringBuilder();

		// method name
		description.append(methodProposal.getName());

		// parameters
		description.append('(');
		appendUnboundedParameterList(description, methodProposal);
		description.append(')');

		// return type
		if (!methodProposal.isConstructor()) {
			// TODO remove SignatureUtil.fix83600 call when bugs are fixed
			char[] returnType= createTypeDisplayName(SignatureUtil.getUpperBound(Signature.getReturnType(SignatureUtil.fix83600(methodProposal.getSignature()))));
			description.append(RETURN_TYPE_SEPARATOR);
			description.append(returnType);
		}

		item.setLabel(description.toString());
		// declaring type
		StringBuilder typeInfo = new StringBuilder();
		String declaringType= extractDeclaringTypeFQN(methodProposal);

		if (methodProposal.getRequiredProposals() != null) {
			String qualifier= Signature.getQualifier(declaringType);
			if (qualifier.length() > 0) {
				typeInfo.append(qualifier);
				typeInfo.append('.');
			}
		}

		declaringType= Signature.getSimpleName(declaringType);
		typeInfo.append(declaringType);
		item.setDetail(typeInfo.toString());

		setSignature(item, String.valueOf(methodProposal.getSignature()));
		setDeclarationSignature(item, String.valueOf(methodProposal.getDeclarationSignature()));
		setName(item, String.valueOf(methodProposal.getName()));

	}

	/**
	 * Updates the label and detail for {@link CompletionItem} with
	 * <ul>
	 * <li>the method name</li>
	 * <li>the raw simple name of the declaring type</li>
	 * </ul>
	 *
	 * @param methodProposal the method proposal to display
	 * @param item the item to set values to
	 */
	private void createJavadocMethodProposalLabel(CompletionProposal methodProposal, CompletionItem item) {
		// method name
		item.setLabel(String.valueOf(methodProposal.getCompletion()));
		// declaring type
		String declaringType= extractDeclaringTypeFQN(methodProposal);
		declaringType= Signature.getSimpleName(declaringType);
		item.setDetail(declaringType);
	}

	private void createOverrideMethodProposalLabel(CompletionProposal methodProposal, CompletionItem item) {
		StringBuilder nameBuffer= new StringBuilder();

		// method name
		nameBuffer.append(methodProposal.getName());
		// parameters
		nameBuffer.append('(');
		appendUnboundedParameterList(nameBuffer, methodProposal);
		nameBuffer.append(')');

		nameBuffer.append(RETURN_TYPE_SEPARATOR);

		// return type
		// TODO remove SignatureUtil.fix83600 call when bugs are fixed
		char[] returnType= createTypeDisplayName(SignatureUtil.getUpperBound(Signature.getReturnType(SignatureUtil.fix83600(methodProposal.getSignature()))));
		nameBuffer.append(returnType);
		item.setLabel(nameBuffer.toString());

		// declaring type
		StringBuilder typeBuffer = new StringBuilder();
		String declaringType= extractDeclaringTypeFQN(methodProposal);
		declaringType= Signature.getSimpleName(declaringType);
		typeBuffer.append(String.format("Override method in '%s'", declaringType));
		item.setDetail(typeBuffer.toString());

		setSignature(item, String.valueOf(methodProposal.getSignature()));
		setDeclarationSignature(item, String.valueOf(methodProposal.getDeclarationSignature()));
		setName(item, String.valueOf(methodProposal.getName()));
	}

	/**
	 * Extracts the fully qualified name of the declaring type of a method
	 * reference.
	 *
	 * @param methodProposal a proposed method
	 * @return the qualified name of the declaring type
	 */
	private String extractDeclaringTypeFQN(CompletionProposal methodProposal) {
		char[] declaringTypeSignature= methodProposal.getDeclarationSignature();
		// special methods may not have a declaring type: methods defined on arrays etc.
		// TODO remove when bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=84690 gets fixed
		if (declaringTypeSignature == null)
			return "java.lang.Object"; //$NON-NLS-1$
		return SignatureUtil.stripSignatureToFQN(String.valueOf(declaringTypeSignature));
	}

	/**
	 * Updates a display label for a given type proposal. The display label
	 * consists of:
	 * <ul>
	 *   <li>the simple type name (erased when the context is in javadoc)</li>
	 *   <li>the package name</li>
	 * </ul>
	 * <p>
	 * Examples:
	 * A proposal for the generic type <code>java.util.List&lt;E&gt;</code>, the display label
	 * is: <code>List<E> - java.util</code>.
	 * </p>
	 *
	 * @param typeProposal the method proposal to display
	 * @param item the completion to update
	 */
	private void createTypeProposalLabel(CompletionProposal typeProposal, CompletionItem item) {
		char[] signature;
		if (fContext != null && fContext.isInJavadoc())
			signature= Signature.getTypeErasure(typeProposal.getSignature());
		else
			signature= typeProposal.getSignature();
		char[] fullName= Signature.toCharArray(signature);
		createTypeProposalLabel(fullName, item);
		setDeclarationSignature(item, String.valueOf(signature));
	}

	private void createJavadocTypeProposalLabel(CompletionProposal typeProposal, CompletionItem item) {
		char[] fullName= Signature.toCharArray(typeProposal.getSignature());
		createJavadocTypeProposalLabel(fullName, item);
	}

	private void createJavadocSimpleProposalLabel(CompletionProposal proposal, CompletionItem item) {
		item.setLabel(createSimpleLabel(proposal).toString());
	}

	void createTypeProposalLabel(char[] fullName, CompletionItem item) {
		// only display innermost type name as type name, using any
		// enclosing types as qualification
		int qIndex= findSimpleNameStart(fullName);

		StringBuilder nameBuffer= new StringBuilder();
		nameBuffer.append(new String(fullName, qIndex, fullName.length - qIndex));
		nameBuffer.append(PACKAGE_NAME_SEPARATOR);
		nameBuffer.append(new String(fullName,0,qIndex-1));
		item.setLabel(nameBuffer.toString());
	}

	private void createJavadocTypeProposalLabel(char[] fullName, CompletionItem item) {
		// only display innermost type name as type name, using any
		// enclosing types as qualification
		int qIndex= findSimpleNameStart(fullName);

		StringBuilder nameBuffer= new StringBuilder();


		nameBuffer.append("{@link "); //$NON-NLS-1$
		nameBuffer.append(new String(fullName, qIndex, fullName.length - qIndex));
		nameBuffer.append('}');
		item.setLabel(nameBuffer.toString());

		if (qIndex > 0) {
			item.setDetail(new String(fullName, 0, qIndex - 1));
		}
	}

	private int findSimpleNameStart(char[] array) {
		int lastDot= 0;
		for (int i= 0, len= array.length; i < len; i++) {
			char ch= array[i];
			if (ch == '<') {
				return lastDot;
			} else if (ch == '.') {
				lastDot= i + 1;
			}
		}
		return lastDot;
	}

	private void createSimpleLabelWithType(CompletionProposal proposal, CompletionItem item) {
		StringBuilder nameBuffer= new StringBuilder();
		nameBuffer.append(proposal.getCompletion());
		char[] typeName= Signature.getSignatureSimpleName(proposal.getSignature());
		if (typeName.length > 0) {
			nameBuffer.append(VAR_TYPE_SEPARATOR);
			nameBuffer.append(typeName);
		}
		item.setLabel(nameBuffer.toString());
	}

	/**
	 * Returns whether the given string starts with "this.".
	 *
	 * @param string string to test
	 * @return <code>true</code> if the given string starts with "this."
	 */
	private boolean isThisPrefix(char[] string) {
		if (string == null || string.length < 5)
			return false;
		return string[0] == 't' && string[1] == 'h' && string[2] == 'i' && string[3] == 's' && string[4] == '.';
	}

	private void createLabelWithTypeAndDeclaration(CompletionProposal proposal, CompletionItem item) {
		char[] name= proposal.getCompletion();
		if (!isThisPrefix(name))
			name= proposal.getName();
		StringBuilder buf= new StringBuilder();

		buf.append(name);
		char[] typeName= Signature.getSignatureSimpleName(proposal.getSignature());
		if (typeName.length > 0) {
			buf.append(VAR_TYPE_SEPARATOR);
			buf.append(typeName);
		}
		item.setLabel(buf.toString());

		char[] declaration= proposal.getDeclarationSignature();
		if (declaration != null) {
			setDeclarationSignature(item, String.valueOf(declaration));
			StringBuilder declBuf = new StringBuilder();
			declaration= Signature.getSignatureSimpleName(declaration);
			if (declaration.length > 0) {
				if (proposal.getRequiredProposals() != null) {
					String declaringType= extractDeclaringTypeFQN(proposal);
					String qualifier= Signature.getQualifier(declaringType);
					if (qualifier.length() > 0) {
						declBuf.append(qualifier);
						declBuf.append('.');
					}
				}
				declBuf.append(declaration);
				item.setDetail(declBuf.toString());
			}
		}
		setName(item,String.valueOf(name));
	}

	private void createPackageProposalLabel(CompletionProposal proposal, CompletionItem item) {
		Assert.isTrue(proposal.getKind() == CompletionProposal.PACKAGE_REF);
		item.setLabel(String.valueOf(proposal.getDeclarationSignature()));
	}

	StringBuilder createSimpleLabel(CompletionProposal proposal) {
		StringBuilder buf= new StringBuilder();
		buf.append(String.valueOf(proposal.getCompletion()));
		return buf;
	}

	private void createAnonymousTypeLabel(CompletionProposal proposal, CompletionItem item) {
		char[] declaringTypeSignature= proposal.getDeclarationSignature();
		declaringTypeSignature= Signature.getTypeErasure(declaringTypeSignature);

		StringBuilder buf= new StringBuilder();

		buf.append(Signature.getSignatureSimpleName(declaringTypeSignature));
		buf.append('(');
		appendUnboundedParameterList(buf, proposal);
		buf.append(')');
		buf.append("  "); //$NON-NLS-1$
		buf.append("Anonymous Inner Type"); //TODO: consider externalization
		item.setLabel(buf.toString());

		if (proposal.getRequiredProposals() != null) {
			char[] signatureQualifier= Signature.getSignatureQualifier(declaringTypeSignature);
			if (signatureQualifier.length > 0) {
				item.setDetail(String.valueOf(signatureQualifier));
			}
		}
		setDeclarationSignature(item, String.valueOf(declaringTypeSignature));
	}

	/**
	 * Sets the signature for use on the resolve call.
	 * @param item
	 * @param signature
	 */
	@SuppressWarnings("unchecked")
	private void setSignature(CompletionItem item, String signature){
		((Map<String, String>)item.getData()).put(CompletionResolveHandler.DATA_FIELD_SIGNATURE,String.valueOf(signature));
	}
	/**
	 * Sets the declaration signature to data that is used on the resolve call.
	 * @param item
	 * @param signature
	 */
	@SuppressWarnings("unchecked")
	private void setDeclarationSignature(CompletionItem item, String signature){
		((Map<String, String>)item.getData()).put(CompletionResolveHandler.DATA_FIELD_DECLARATION_SIGNATURE,String.valueOf(signature));
	}

	/**
	 * Sets the name to data that is used on the resolve call.
	 * @param item
	 * @param name
	 */
	@SuppressWarnings("unchecked")
	private void setName(CompletionItem item, String name){
		((Map<String, String>)item.getData()).put(CompletionResolveHandler.DATA_FIELD_NAME,String.valueOf(name));
	}

	/**
	 * Updates the description fields of the item.
	 *
	 * @param proposal
	 * @param item
	 */
	public void updateDescription(CompletionProposal proposal, CompletionItem item) {
		switch (proposal.getKind()) {
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			if (fContext != null && fContext.isInJavadoc()){
				createJavadocMethodProposalLabel(proposal, item);
				break;
			}
			createMethodProposalLabel(proposal,item);
			break;
		case CompletionProposal.METHOD_DECLARATION:
			createOverrideMethodProposalLabel(proposal,item);
			break;
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			createAnonymousTypeLabel(proposal, item);
			break;
		case CompletionProposal.TYPE_REF:
			createTypeProposalLabel(proposal, item);
			break;
		case CompletionProposal.JAVADOC_TYPE_REF:
			createJavadocTypeProposalLabel(proposal, item);
			break;
		case CompletionProposal.JAVADOC_FIELD_REF:
		case CompletionProposal.JAVADOC_VALUE_REF:
		case CompletionProposal.JAVADOC_BLOCK_TAG:
		case CompletionProposal.JAVADOC_INLINE_TAG:
		case CompletionProposal.JAVADOC_PARAM_REF:
			createJavadocSimpleProposalLabel(proposal, item);
			break;
		case CompletionProposal.JAVADOC_METHOD_REF:
			createJavadocMethodProposalLabel(proposal, item);
			break;
		case CompletionProposal.PACKAGE_REF:
			createPackageProposalLabel(proposal, item);
			break;
		case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			createLabelWithTypeAndDeclaration(proposal, item);
			break;
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			createSimpleLabelWithType(proposal, item);
			break;
		case CompletionProposal.KEYWORD:
		case CompletionProposal.LABEL_REF:
			item.setLabel(createSimpleLabel(proposal).toString());
			break;
		default:
			Assert.isTrue(false);
		}
	}
}
