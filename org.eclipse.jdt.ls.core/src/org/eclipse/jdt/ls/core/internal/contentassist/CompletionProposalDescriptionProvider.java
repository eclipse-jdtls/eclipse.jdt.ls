/*******************************************************************************
 * Copyright (c) 2005, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Pivotal Software, Inc. - adopted for Flux
 *     Red Hat - adopted for JDT Server
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.lang.reflect.Field;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;

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
	private static final String OBJECT = "java.lang.Object";


	/**
	 * The completion context.
	 */
	private CompletionContext fContext;
	private ICompilationUnit fUnit;

	/**
	 * Creates a new label provider.
	 *
	 */
	public CompletionProposalDescriptionProvider(ICompilationUnit unit, CompletionContext context) {
		super();
		fContext = context;
		fUnit = unit;
	}

	public CompletionProposalDescriptionProvider(CompletionContext context) {
		super();
		fContext = context;
	}

	/**
	 * Creates and returns the method signature suitable for display.
	 *
	 * @param proposal
	 *            the proposal to create the description for
	 * @return the string of method signature suitable for display
	 */
	public StringBuilder createMethodProposalDescription(CompletionProposal proposal) {
		int kind = proposal.getKind();
		StringBuilder description = new StringBuilder();
		switch (kind) {
			case CompletionProposal.METHOD_REF:
			case CompletionProposal.METHOD_NAME_REFERENCE:
			case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			case CompletionProposal.CONSTRUCTOR_INVOCATION:

				// method name
				description.append(proposal.getName());

				// parameters
				description.append('(');
				appendUnboundedParameterList(description, proposal);
				description.append(')');

				// return type
				if (!proposal.isConstructor()) {
					description.append(RETURN_TYPE_SEPARATOR);
					appendReturnType(description, proposal);
				}
		}
		return description; // dummy
	}

	private StringBuilder appendReturnType(StringBuilder description, CompletionProposal proposal){
		// TODO remove SignatureUtil.fix83600 call when bugs are fixed
		char[] returnType = createTypeDisplayName(SignatureUtil.getUpperBound(Signature.getReturnType(SignatureUtil.fix83600(proposal.getSignature()))));
		description.append(returnType);
		return description;
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
		char[][] parameterNames;
		try {
			parameterNames = methodProposal.findParameterNames(null);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			parameterNames = CompletionEngine.createDefaultParameterNames(Signature.getParameterCount(signature));
			methodProposal.setParameterNames(parameterNames);
		}
		char[][] parameterTypes= Signature.getParameterTypes(signature);

		for (int i= 0; i < parameterTypes.length; i++) {
			parameterTypes[i]= createTypeDisplayName(SignatureUtil.getLowerBound(parameterTypes[i]));
		}

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
		if (typeName == null) {
			return typeName;
		}
		final int len= typeName.length;
		if (len < 2) {
			return typeName;
		}

		if (typeName[len - 1] != ']') {
			return typeName;
		}
		if (typeName[len - 2] != '[') {
			return typeName;
		}

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
					{
						buf.replace(pos, pos + 8, "-"); //$NON-NLS-1$
					}
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
		StringBuilder description = this.createMethodProposalDescription(methodProposal);
		if (isCompletionItemLabelDetailsSupport()){
			StringBuilder methodParams = new StringBuilder();
			methodParams.append('(');
			appendUnboundedParameterList(methodParams, methodProposal);
			methodParams.append(')');
			if (methodProposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION) {
				StringBuilder returnType = new StringBuilder();
				appendReturnType(returnType, methodProposal);
				setLabelDetails(item, String.valueOf(methodProposal.getName()), methodParams.toString(), returnType.toString());
			} else {
				setLabelDetails(item, String.valueOf(methodProposal.getName()), methodParams.toString(), null);
			}
		} else {
			item.setLabel(description.toString());
		}
		item.setInsertText(String.valueOf(methodProposal.getName()));

		// declaring type
		StringBuilder typeInfo = new StringBuilder();
		String declaringType= extractDeclaringTypeFQN(methodProposal);

		String qualifier = null;
		if (methodProposal.getRequiredProposals() != null) {
			qualifier = Signature.getQualifier(declaringType);
			if (qualifier.length() > 0) {
				typeInfo.append(qualifier);
				typeInfo.append('.');
				qualifier = qualifier + ".";
			}
		}

		declaringType= Signature.getSimpleName(declaringType);
		typeInfo.append(declaringType);
		StringBuilder detail = new StringBuilder();
		if (typeInfo.length() > 0) {
			detail.append(typeInfo);
			detail.append('.');
		}
		detail.append(description);
		item.setDetail(detail.toString());

		if (fUnit != null && methodProposal.isConstructor() && typeInfo.length() > 0 && item.getData() != null && methodProposal.getRequiredProposals() != null && methodProposal.getRequiredProposals().length > 0) {
			CompletionProposal requiredProposal = methodProposal.getRequiredProposals()[0];
			try {
				IDocument document = JsonRpcHelpers.toDocument(fUnit.getBuffer());
				String prefix = document.get(requiredProposal.getReplaceStart(), requiredProposal.getReplaceEnd() - requiredProposal.getReplaceStart());
				if (prefix != null && prefix.indexOf(".") > -1) {
					description.insert(0, qualifier);
					item.setFilterText(description.toString());
				}
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
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
		// don't set completion item description in the case of constructor
		if (isCompletionItemLabelDetailsSupport() && methodProposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION){
			StringBuilder returnType = new StringBuilder();
			appendReturnType(returnType, methodProposal);
			setLabelDetails(item, null, null, returnType.toString());
		}
	}

	private void createOverrideMethodProposalLabel(CompletionProposal methodProposal, CompletionItem item) {
		// method name
		String name = new String(methodProposal.getName());
		item.setInsertText(name);
		// parameters
		StringBuilder parameters = new StringBuilder();
		parameters.append('(');
		appendUnboundedParameterList(parameters, methodProposal);
		parameters.append(')');
		// return type
		// TODO remove SignatureUtil.fix83600 call when bugs are fixed
		char[] returnType = createTypeDisplayName(SignatureUtil.getUpperBound(Signature.getReturnType(SignatureUtil.fix83600(methodProposal.getSignature()))));

		if (isCompletionItemLabelDetailsSupport()) {
			setLabelDetails(item, String.valueOf(methodProposal.getName()), parameters.toString(), String.valueOf(returnType));
		} else {
			StringBuilder nameBuffer = new StringBuilder();
			nameBuffer.append(name);
			nameBuffer.append(parameters);
			nameBuffer.append(RETURN_TYPE_SEPARATOR);
			nameBuffer.append(returnType);
			item.setLabel(nameBuffer.toString());
		}
		item.setFilterText(name);

		// declaring type
		StringBuilder typeBuffer = new StringBuilder();
		String declaringType= extractDeclaringTypeFQN(methodProposal);
		declaringType= Signature.getSimpleName(declaringType);
		typeBuffer.append(String.format("Override method in '%s'", declaringType));
		item.setDetail(typeBuffer.toString());
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
		{
			return OBJECT;
		}
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
		if (fContext != null && fContext.isInJavadoc()) {
			signature= Signature.getTypeErasure(typeProposal.getSignature());
		} else {
			signature= typeProposal.getSignature();
		}
		char[] fullName= Signature.toCharArray(signature);
		createTypeProposalLabel(fullName, item);
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

		String name = new String(fullName, qIndex, fullName.length - qIndex);
		item.setFilterText(name);
		item.setInsertText(name);
		item.setDetail(new String(fullName));

		String packageName = qIndex > 0 ? new String(fullName, 0, qIndex - 1) : null;

		if (isCompletionItemLabelDetailsSupport()) {
			setLabelDetails(item, name, null, packageName);
		} else {
			StringBuilder nameBuffer = new StringBuilder();
			nameBuffer.append(name);
			if (packageName != null) {
				nameBuffer.append(PACKAGE_NAME_SEPARATOR);
				nameBuffer.append(packageName);
			}
			item.setLabel(nameBuffer.toString());
		}
	}

	private void createJavadocTypeProposalLabel(char[] fullName, CompletionItem item) {
		// only display innermost type name as type name, using any
		// enclosing types as qualification
		int qIndex= findSimpleNameStart(fullName);

		StringBuilder nameBuffer= new StringBuilder();

		String name = new String(fullName, qIndex, fullName.length - qIndex);
		nameBuffer.append("{@link "); //$NON-NLS-1$
		nameBuffer.append(name);
		nameBuffer.append('}');
		item.setLabel(nameBuffer.toString());
		item.setFilterText(name);
		String packageName = qIndex > 0 ? new String(fullName, 0, qIndex - 1) : null;
		if (packageName != null) {
			item.setDetail(packageName);
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
		char[] typeName = Signature.getSignatureSimpleName(proposal.getSignature());
		String name = String.valueOf(proposal.getCompletion());
		item.setInsertText(name);

		if (isCompletionItemLabelDetailsSupport()) {
			setLabelDetails(item, name, null, String.valueOf(typeName));
		} else {
			StringBuilder nameBuffer = new StringBuilder();
			nameBuffer.append(name);
			if (typeName.length > 0) {
				nameBuffer.append(VAR_TYPE_SEPARATOR);
				nameBuffer.append(typeName);
			}
			item.setLabel(nameBuffer.toString());
		}
	}

	/**
	 * Returns whether the given string starts with "this.".
	 *
	 * @param string string to test
	 * @return <code>true</code> if the given string starts with "this."
	 */
	private boolean isThisPrefix(char[] string) {
		if (string == null || string.length < 5) {
			return false;
		}
		return string[0] == 't' && string[1] == 'h' && string[2] == 'i' && string[3] == 's' && string[4] == '.';
	}

	private void createLabelWithTypeAndDeclaration(CompletionProposal proposal, CompletionItem item) {
		char[] name= proposal.getCompletion();
		if (!isThisPrefix(name)) {
			name= proposal.getName();
		}
		char[] typeName= Signature.getSignatureSimpleName(proposal.getSignature());

		StringBuilder buf = new StringBuilder();
		buf.append(name);
		item.setInsertText(buf.toString());
		if (typeName.length > 0) {
			buf.append(VAR_TYPE_SEPARATOR);
			buf.append(typeName);
		}
		if (isCompletionItemLabelDetailsSupport()){
			setLabelDetails(item, String.valueOf(name), null, String.valueOf(typeName));
		} else {
			item.setLabel(buf.toString());
		}

		char[] declaration= proposal.getDeclarationSignature();
		StringBuilder detailBuf = new StringBuilder();
		if (declaration != null) {
			declaration= Signature.getSignatureSimpleName(declaration);
			if (declaration.length > 0) {
				if (proposal.getRequiredProposals() != null) {
					String declaringType= extractDeclaringTypeFQN(proposal);
					String qualifier= Signature.getQualifier(declaringType);
					if (qualifier.length() > 0) {
						detailBuf.append(qualifier);
						detailBuf.append('.');
					}
				}
				detailBuf.append(declaration);
			}
		}
		if (detailBuf.length() > 0) {
			detailBuf.append('.');
		}
		detailBuf.append(buf);
		item.setDetail(detailBuf.toString());
	}

	private void createPackageProposalLabel(CompletionProposal proposal, CompletionItem item) {
		Assert.isTrue(proposal.getKind() == CompletionProposal.PACKAGE_REF || proposal.getKind() == CompletionProposal.MODULE_REF || proposal.getKind() == CompletionProposal.MODULE_DECLARATION);
		item.setLabel(String.valueOf(proposal.getDeclarationSignature()));
		StringBuilder detail = new StringBuilder();
		detail.append(proposal.getKind() == CompletionProposal.PACKAGE_REF ? "(package) " : "(module) ");
		detail.append(String.valueOf(proposal.getDeclarationSignature()));
		item.setDetail(detail.toString());
		if (isCompletionItemLabelDetailsSupport()){
			setLabelDetails(item, null, null, proposal.getKind() == CompletionProposal.PACKAGE_REF ? "(package)" : "(module)");
		}
	}

	StringBuilder createSimpleLabel(CompletionProposal proposal) {
		StringBuilder buf= new StringBuilder();
		buf.append(String.valueOf(proposal.getCompletion()));
		return buf;
	}

	private void createAnonymousTypeLabel(CompletionProposal proposal, CompletionItem item) {
		char[] declaringTypeSignature= proposal.getDeclarationSignature();
		declaringTypeSignature= Signature.getTypeErasure(declaringTypeSignature);
		String name = new String(Signature.getSignatureSimpleName(declaringTypeSignature));
		item.setInsertText(name);

		StringBuilder methodParams = new StringBuilder();
		methodParams.append('(');
		appendUnboundedParameterList(methodParams, proposal);
		methodParams.append(')');

		if (isCompletionItemLabelDetailsSupport()){
			StringBuilder returnType = new StringBuilder();
			appendReturnType(returnType, proposal);
			setLabelDetails(item, name, methodParams.toString(), "Anonymous Inner Type");
		} else {
			StringBuilder buf= new StringBuilder();
			buf.append(name);
			buf.append(methodParams);
			buf.append("  "); //$NON-NLS-1$
			buf.append("Anonymous Inner Type"); //TODO: consider externalization
			item.setLabel(buf.toString());
		}
		if (proposal.getRequiredProposals() != null) {
			char[] signatureQualifier= Signature.getSignatureQualifier(declaringTypeSignature);
			if (signatureQualifier.length > 0) {
				item.setDetail(String.valueOf(signatureQualifier) + "." + name);
			}
		}
	}

	private void createLabelWithLambdaExpression(CompletionProposal proposal, CompletionItem item) {
		StringBuilder label = new StringBuilder();
		label.append('(');
		appendUnboundedParameterList(label, proposal);
		label.append(')');
		label.append(" ->");
		char[] returnType = createTypeDisplayName(SignatureUtil.getUpperBound(Signature.getReturnType(SignatureUtil.fix83600(proposal.getSignature()))));
		if (isCompletionItemLabelDetailsSupport()) {
			setLabelDetails(item, label.toString(), null, String.valueOf(returnType));
		} else {
			label.append(RETURN_TYPE_SEPARATOR);
			label.append(returnType);
			item.setLabel(label.toString());
		}
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
				if (fContext != null && fContext.isInJavadoc()) {
					createJavadocMethodProposalLabel(proposal, item);
					break;
				}
				createMethodProposalLabel(proposal, item);
				break;
			case CompletionProposal.METHOD_DECLARATION:
				createOverrideMethodProposalLabel(proposal, item);
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
			case CompletionProposal.MODULE_DECLARATION:
			case CompletionProposal.MODULE_REF:
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
			case CompletionProposal.LAMBDA_EXPRESSION:
				createLabelWithLambdaExpression(proposal, item);
				break;
			default:
				JavaLanguageServerPlugin.logInfo(new String(proposal.getName()) + " is of type " + getProposal(proposal));
				Assert.isTrue(false);
		}
	}

	private String getProposal(CompletionProposal proposal) {
		try {
			for (Field field : CompletionProposal.class.getDeclaredFields()) {
				if (int.class.equals(field.getType()) && Integer.valueOf(proposal.getKind()).equals(field.get(null))) {
					return field.getName();
				}
			}
		} catch (Exception e) {
		}
		return "unknown";
	}

	/**
	 *
	 * Sets the Completion Item Label Details
	 *
	 * @param item the completion item
	 * @param label the label of the completion item
	 * @param detail the detail of the completion item
	 * @param description the description of the completion item showing the return type
	 *
	 */
	private void setLabelDetails(CompletionItem item, String label, String detail, String description) {
			CompletionItemLabelDetails itemLabelDetails = new CompletionItemLabelDetails();
			if (label != null) {
				item.setLabel(label);
			}
			if (detail != null) {
				itemLabelDetails.setDetail(detail);
			}
			if (description != null) {
				itemLabelDetails.setDescription(description);
			}
			item.setLabelDetails(itemLabelDetails);
	}

	private boolean isCompletionItemLabelDetailsSupport() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionItemLabelDetailsSupport();
	}
}
