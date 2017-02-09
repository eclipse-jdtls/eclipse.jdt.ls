/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.eclipse.jdt.ls.core.internal.contentassist.TypeProposalUtils.isImplicitImport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Range;
import org.eclipse.text.edits.TextEdit;

/**
 * Utility to calculate the completion replacement string based on JDT Core
 * {@link CompletionProposal}. This class is based on the implementation of JDT
 * UI <code>AbstractJavaCompletionProposal</code> and its subclasses.
 *
 * @author aboyko
 *
 * Copied from Flux project.
 *
 */
public class CompletionProposalReplacementProvider {

	final private static char SPACE = ' ';
	final private static char LPAREN = '(';
	final private static char RPAREN = ')';
	final private static char SEMICOLON = ';';
	final private static char COMMA = ',';

	private final ICompilationUnit compilationUnit;
	private final int offset;
	private final CompletionContext context;
	private ImportRewrite importRewrite;

	public CompletionProposalReplacementProvider(ICompilationUnit compilationUnit, CompletionContext context, int offset) {
		super();
		this.compilationUnit = compilationUnit;
		this.context = context;
		this.offset = offset;
	}



	/**
	 * Updates the replacement and any additional replacement for the given item.
	 *
	 * @param proposal
	 * @param item
	 * @param trigger
	 */
	public void updateReplacement(CompletionProposal proposal, CompletionItem item, char trigger) {

		// reset importRewrite
		this.importRewrite = TypeProposalUtils.createImportRewrite(compilationUnit);

		List<org.eclipse.lsp4j.TextEdit> additionalTextEdits = new ArrayList<>();

		StringBuilder completionBuffer = new StringBuilder();
		Range range = null;
		if (isSupportingRequiredProposals(proposal)) {
			CompletionProposal[] requiredProposals= proposal.getRequiredProposals();
			if (requiredProposals != null) {
				for (CompletionProposal requiredProposal : requiredProposals) {
					switch(requiredProposal.getKind()) {
					case CompletionProposal.TYPE_IMPORT:
					case CompletionProposal.METHOD_IMPORT:
					case CompletionProposal.FIELD_IMPORT:
						appendImportProposal(completionBuffer, requiredProposal, proposal.getKind());
						break;
					case CompletionProposal.TYPE_REF:
						org.eclipse.lsp4j.TextEdit edit = toRequiredTypeEdit(requiredProposal, trigger, proposal.canUseDiamond(context));
						if (proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION) {
							completionBuffer.append(edit.getNewText());
							range = edit.getRange();
						} else {
							additionalTextEdits.add(edit);
						}
						break;
					default:
						/*
						 * In 3.3 we only support the above required proposals, see
						 * CompletionProposal#getRequiredProposals()
						 */
						Assert.isTrue(false);
					}
				}
			}
		}

		appendReplacementString(completionBuffer, proposal);
		if (range == null) {
			range = toReplacementRange(proposal);
		}
		if(range != null){
			item.setTextEdit(new org.eclipse.lsp4j.TextEdit(range, completionBuffer.toString()));
		}else{
			// fallback
			item.setInsertText(completionBuffer.toString());
		}
		addImports(additionalTextEdits);
		if(!additionalTextEdits.isEmpty()){
			item.setAdditionalTextEdits(additionalTextEdits);
		}
	}

	private Range toReplacementRange(CompletionProposal proposal){
		try {
			return JDTUtils.toRange(compilationUnit, proposal.getReplaceStart(), proposal.getReplaceEnd()-proposal.getReplaceStart());
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Adds imports collected by importRewrite to item
	 * @param item
	 */
	private void addImports(List<org.eclipse.lsp4j.TextEdit> additionalEdits) {
		if(this.importRewrite != null ){
			try {
				TextEdit edit =  this.importRewrite.rewriteImports(new NullProgressMonitor());
				TextEditConverter converter = new TextEditConverter(this.compilationUnit, edit);
				additionalEdits.addAll(converter.convert());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Error adding imports",e);
			}
		}
	}

	private boolean isSupportingRequiredProposals(CompletionProposal proposal) {
		return proposal != null
				&& (proposal.getKind() == CompletionProposal.METHOD_REF
				|| proposal.getKind() == CompletionProposal.FIELD_REF
				|| proposal.getKind() == CompletionProposal.TYPE_REF
				|| proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION || proposal
				.getKind() == CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION);
	}

	protected boolean hasArgumentList(CompletionProposal proposal) {
		if (CompletionProposal.METHOD_NAME_REFERENCE == proposal.getKind())
			return false;
		char[] completion= proposal.getCompletion();
		return !isInJavadoc() && completion.length > 0 && completion[completion.length - 1] == ')';
	}

	private boolean isInJavadoc() {
		return context.isInJavadoc();
	}

	private void appendReplacementString(StringBuilder buffer, CompletionProposal proposal) {
		if (!hasArgumentList(proposal)) {
			buffer.append(proposal.getKind() == CompletionProposal.TYPE_REF ? computeJavaTypeReplacementString(proposal) : String.valueOf(proposal.getCompletion()));
			return;
		}

		// we're inserting a method plus the argument list - respect formatter preferences
		appendMethodNameReplacement(buffer, proposal);

		if (hasParameters(proposal)) {
			appendGuessingCompletion(buffer, proposal);
		}

		buffer.append(RPAREN);

		if (canAutomaticallyAppendSemicolon(proposal))
			buffer.append(SEMICOLON);
	}

	private boolean hasParameters(CompletionProposal proposal) throws IllegalArgumentException {
		return Signature.getParameterCount(proposal.getSignature()) > 0;
	}

	private void appendMethodNameReplacement(StringBuilder buffer, CompletionProposal proposal) {
		if (proposal.getKind() == CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER) {
			String coreCompletion= String.valueOf(proposal.getCompletion());
			//			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(getTextViewer().getDocument());
			//			String replacement= CodeFormatterUtil.format(CodeFormatter.K_EXPRESSION, coreCompletion, 0, lineDelimiter, fInvocationContext.getProject());
			//			buffer.append(replacement.substring(0, replacement.lastIndexOf('.') + 1));
			buffer.append(coreCompletion);
		}

		if (proposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION)
			buffer.append(proposal.getName());

		buffer.append(LPAREN);
	}

	private void appendGuessingCompletion(StringBuilder buffer, CompletionProposal proposal) {
		char[][] parameterNames= proposal.findParameterNames(null);

		int count= parameterNames.length;

		for (int i= 0; i < count; i++) {
			if (i != 0) {
				buffer.append(COMMA);
				buffer.append(SPACE);
			}

			char[] argument = parameterNames[i];

			buffer.append("{{");
			buffer.append(argument);
			buffer.append("}}");
		}
	}

	private final boolean canAutomaticallyAppendSemicolon(CompletionProposal proposal) {
		return !proposal.isConstructor() && CharOperation.equals(new char[] { Signature.C_VOID }, Signature.getReturnType(proposal.getSignature()));
	}

	private org.eclipse.lsp4j.TextEdit toRequiredTypeEdit(CompletionProposal typeProposal, char trigger, boolean canUseDiamond) {

		StringBuilder buffer = new StringBuilder();
		appendReplacementString(buffer, typeProposal);

		if (compilationUnit == null /*|| getContext() != null && getContext().isInJavadoc()*/) {
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		IJavaProject project= compilationUnit.getJavaProject();
		if (!shouldProposeGenerics(project)){
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		char[] completion= typeProposal.getCompletion();
		// don't add parameters for import-completions nor for proposals with an empty completion (e.g. inside the type argument list)
		if (completion.length > 0 && (completion[completion.length - 1] == ';' || completion[completion.length - 1] == '.')){
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		/*
		 * Add parameter types
		 */
		boolean onlyAppendArguments;
		try {
			onlyAppendArguments= typeProposal.getCompletion().length == 0 && offset > 0 && compilationUnit.getBuffer().getChar(offset - 1) == '<';
		} catch (JavaModelException e) {
			onlyAppendArguments= false;
		}
		if (onlyAppendArguments || shouldAppendArguments(typeProposal, trigger)) {
			String[] typeArguments = computeTypeArgumentProposals(typeProposal);
			if(typeArguments.length > 0){
				if (canUseDiamond){
					buffer.append("<>"); //$NON-NLS-1$
				} else
					appendParameterList(buffer,typeArguments, onlyAppendArguments);
			}
		}
		Range range = toReplacementRange(typeProposal);
		return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
	}

	private final boolean shouldProposeGenerics(IJavaProject project) {
		String sourceVersion;
		if (project != null)
			sourceVersion= project.getOption(JavaCore.COMPILER_SOURCE, true);
		else
			sourceVersion= JavaCore.getOption(JavaCore.COMPILER_SOURCE);

		return !isVersionLessThan(sourceVersion, JavaCore.VERSION_1_5);
	}

	public static boolean isVersionLessThan(String version1, String version2) {
		if (JavaCore.VERSION_CLDC_1_1.equals(version1)) {
			version1= JavaCore.VERSION_1_1 + 'a';
		}
		if (JavaCore.VERSION_CLDC_1_1.equals(version2)) {
			version2= JavaCore.VERSION_1_1 + 'a';
		}
		return version1.compareTo(version2) < 0;
	}

	private IJavaElement resolveJavaElement(IJavaProject project, CompletionProposal proposal) throws JavaModelException {
		char[] signature= proposal.getSignature();
		String typeName= SignatureUtil.stripSignatureToFQN(String.valueOf(signature));
		return project.findType(typeName);
	}

	private String[] computeTypeArgumentProposals(CompletionProposal proposal) {
		try {
			IType type = (IType) resolveJavaElement(
					compilationUnit.getJavaProject(), proposal);
			if (type == null)
				return new String[0];

			ITypeParameter[] parameters = type.getTypeParameters();
			if (parameters.length == 0)
				return new String[0];

			String[] arguments = new String[parameters.length];

			ITypeBinding expectedTypeBinding = getExpectedTypeForGenericParameters();
			if (expectedTypeBinding != null && expectedTypeBinding.isParameterizedType()) {
				// in this case, the type arguments we propose need to be compatible
				// with the corresponding type parameters to declared type

				IType expectedType= (IType) expectedTypeBinding.getJavaElement();

				IType[] path= TypeProposalUtils.computeInheritancePath(type, expectedType);
				if (path == null)
					// proposed type does not inherit from expected type
					// the user might be looking for an inner type of proposed type
					// to instantiate -> do not add any type arguments
					return new String[0];

				int[] indices= new int[parameters.length];
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					indices[paramIdx]= TypeProposalUtils.mapTypeParameterIndex(path, path.length - 1, paramIdx);
				}

				// for type arguments that are mapped through to the expected type's
				// parameters, take the arguments of the expected type
				ITypeBinding[] typeArguments= expectedTypeBinding.getTypeArguments();
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					if (indices[paramIdx] != -1) {
						// type argument is mapped through
						ITypeBinding binding= typeArguments[indices[paramIdx]];
						arguments[paramIdx]= computeTypeProposal(binding, parameters[paramIdx]);
					}
				}
			}

			// for type arguments that are not mapped through to the expected type,
			// take the lower bound of the type parameter
			for (int i = 0; i < arguments.length; i++) {
				if (arguments[i] == null) {
					arguments[i] = computeTypeProposal(parameters[i]);
				}
			}
			return arguments;
		} catch (JavaModelException e) {
			return new String[0];
		}
	}

	private String computeTypeProposal(ITypeParameter parameter) throws JavaModelException {
		String[] bounds= parameter.getBounds();
		String elementName= parameter.getElementName();
		if (bounds.length == 1 && !"java.lang.Object".equals(bounds[0])) //$NON-NLS-1$
			return Signature.getSimpleName(bounds[0]);
		else
			return elementName;
	}

	private String computeTypeProposal(ITypeBinding binding, ITypeParameter parameter) throws JavaModelException {
		final String name = TypeProposalUtils.getTypeQualifiedName(binding);
		if (binding.isWildcardType()) {

			if (binding.isUpperbound()) {
				// replace the wildcard ? with the type parameter name to get "E extends Bound" instead of "? extends Bound"
				//				String contextName= name.replaceFirst("\\?", parameter.getElementName()); //$NON-NLS-1$
				// upper bound - the upper bound is the bound itself
				return binding.getBound().getName();
			}

			// no or upper bound - use the type parameter of the inserted type, as it may be more
			// restrictive (eg. List<?> list= new SerializableList<Serializable>())
			return computeTypeProposal(parameter);
		}

		// not a wildcard but a type or type variable - this is unambigously the right thing to insert
		return name;
	}

	private StringBuilder appendParameterList(StringBuilder buffer, String[] typeArguments, boolean onlyAppendArguments) {
		if (typeArguments != null && typeArguments.length > 0) {
			final char LESS= '<';
			final char GREATER= '>';
			if (!onlyAppendArguments) {
				buffer.append(LESS);
			}
			StringBuilder separator= new StringBuilder(3);
			separator.append(COMMA);

			for (int i= 0; i != typeArguments.length; i++) {
				if (i != 0)
					buffer.append(separator);

				buffer.append(typeArguments[i]);
			}

			if (!onlyAppendArguments)
				buffer.append(GREATER);
		}
		return buffer;
	}


	private boolean shouldAppendArguments(CompletionProposal proposal,
			char trigger) {
		/*
		 * No argument list if there were any special triggers (for example a
		 * period to qualify an inner type).
		 */
		if (trigger != '\0' && trigger != '<' && trigger != '(')
			return false;

		/*
		 * No argument list if the completion is empty (already within the
		 * argument list).
		 */
		char[] completion = proposal.getCompletion();
		if (completion.length == 0)
			return false;

		/*
		 * No argument list if there already is a generic signature behind the
		 * name.
		 */
		try {
			IDocument document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			IRegion region= document.getLineInformationOfOffset(proposal.getReplaceEnd());
			String line= document.get(region.getOffset(),region.getLength());

			int index= proposal.getReplaceEnd() - region.getOffset();
			while (index != line.length() && Character.isUnicodeIdentifierPart(line.charAt(index)))
				++index;

			if (index == line.length())
				return true;

			char ch= line.charAt(index);
			return ch != '<';

		} catch (BadLocationException | JavaModelException e) {
			return true;
		}

	}

	private StringBuilder appendImportProposal(StringBuilder buffer, CompletionProposal proposal, int coreKind) {
		int proposalKind= proposal.getKind();
		String qualifiedTypeName= null;
		char[] qualifiedType= null;
		if (proposalKind == CompletionProposal.TYPE_IMPORT) {
			qualifiedType= proposal.getSignature();
			qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
		} else if (proposalKind == CompletionProposal.METHOD_IMPORT || proposalKind == CompletionProposal.FIELD_IMPORT) {
			qualifiedType= Signature.getTypeErasure(proposal.getDeclarationSignature());
			qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
		} else {
			/*
			 * In 3.3 we only support the above import proposals, see
			 * CompletionProposal#getRequiredProposals()
			 */
			Assert.isTrue(false);
		}

		/* Add imports if the preference is on. */
		if (importRewrite != null) {
			if (proposalKind == CompletionProposal.TYPE_IMPORT) {
				String simpleType= importRewrite.addImport(qualifiedTypeName, null);
				if (coreKind == CompletionProposal.METHOD_REF) {
					buffer.append(simpleType);
					buffer.append(',');
					return buffer;
				}
			} else {
				String res= importRewrite.addStaticImport(qualifiedTypeName, String.valueOf(proposal.getName()), proposalKind == CompletionProposal.FIELD_IMPORT, null);
				int dot= res.lastIndexOf('.');
				if (dot != -1) {
					buffer.append(importRewrite.addImport(res.substring(0, dot), null));
					buffer.append('.');
					return buffer;
				}
			}
			return buffer;
		}

		// Case where we don't have an import rewrite (see allowAddingImports)

		if (compilationUnit != null && isImplicitImport(Signature.getQualifier(qualifiedTypeName), compilationUnit)) {
			/* No imports for implicit imports. */

			if (proposal.getKind() == CompletionProposal.TYPE_IMPORT && coreKind == CompletionProposal.FIELD_REF)
				return buffer;
			qualifiedTypeName= String.valueOf(Signature.getSignatureSimpleName(qualifiedType));
		}
		buffer.append(qualifiedTypeName);
		buffer.append('.');
		return buffer;
	}

	private ITypeBinding getExpectedTypeForGenericParameters() {
		char[][] chKeys= context.getExpectedTypesKeys();
		if (chKeys == null || chKeys.length == 0)
			return null;

		String[] keys= new String[chKeys.length];
		for (int i= 0; i < keys.length; i++) {
			keys[i]= String.valueOf(chKeys[0]);
		}

		final ASTParser parser= ASTParser.newParser(AST.JLS8);
		parser.setProject(compilationUnit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);

		final Map<String, IBinding> bindings= new HashMap<>();
		ASTRequestor requestor= new ASTRequestor() {
			@Override
			public void acceptBinding(String bindingKey, IBinding binding) {
				bindings.put(bindingKey, binding);
			}
		};
		parser.createASTs(new ICompilationUnit[0], keys, requestor, null);

		if (bindings.size() > 0)
			return (ITypeBinding) bindings.get(keys[0]);

		return null;
	}

	private String computeJavaTypeReplacementString(CompletionProposal proposal) {
		String replacement = String.valueOf(proposal.getCompletion());

		/* No import rewriting ever from within the import section. */
		if (isImportCompletion(proposal))
			return replacement;

		/*
		 * Always use the simple name for non-formal javadoc references to
		 * types.
		 */
		// TODO fix
		if (proposal.getKind() == CompletionProposal.TYPE_REF
				&& context.isInJavadocText())
			return SignatureUtil.getSimpleTypeName(proposal);

		String qualifiedTypeName = SignatureUtil.getQualifiedTypeName(proposal);

		// Type in package info must be fully qualified.
		if (compilationUnit != null
				&& TypeProposalUtils.isPackageInfo(compilationUnit))
			return qualifiedTypeName;

		if (qualifiedTypeName.indexOf('.') == -1 && replacement.length() > 0)
			// default package - no imports needed
			return qualifiedTypeName;

		/*
		 * If the user types in the qualification, don't force import rewriting
		 * on him - insert the qualified name.
		 */
		String prefix="";
		try{
			IDocument document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			IRegion region= document.getLineInformationOfOffset(proposal.getReplaceEnd());
			prefix =  document.get(region.getOffset(), proposal.getReplaceEnd() -region.getOffset()).trim();
		}catch(BadLocationException | JavaModelException e){

		}
		int dotIndex = prefix.lastIndexOf('.');
		// match up to the last dot in order to make higher level matching still
		// work (camel case...)
		if (dotIndex != -1
				&& qualifiedTypeName.toLowerCase().startsWith(
						prefix.substring(0, dotIndex + 1).toLowerCase())) {
			return qualifiedTypeName;
		}

		/*
		 * The replacement does not contain a qualification (e.g. an inner type
		 * qualified by its parent) - use the replacement directly.
		 */
		if (replacement.indexOf('.') == -1) {
			if (isInJavadoc())
				return SignatureUtil.getSimpleTypeName(proposal); // don't use
			// the
			// braces
			// added for
			// javadoc
			// link
			// proposals
			return replacement;
		}

		/* Add imports if the preference is on. */
		if (importRewrite != null) {
			return importRewrite.addImport(qualifiedTypeName, null);
		}

		// fall back for the case we don't have an import rewrite (see
		// allowAddingImports)

		/* No imports for implicit imports. */
		if (compilationUnit != null
				&& TypeProposalUtils.isImplicitImport(
						Signature.getQualifier(qualifiedTypeName),
						compilationUnit)) {
			return Signature.getSimpleName(qualifiedTypeName);
		}


		/* Default: use the fully qualified type name. */
		return qualifiedTypeName;
	}

	private boolean isImportCompletion(CompletionProposal proposal) {
		char[] completion = proposal.getCompletion();
		if (completion.length == 0)
			return false;

		char last = completion[completion.length - 1];
		/*
		 * Proposals end in a semicolon when completing types in normal imports
		 * or when completing static members, in a period when completing types
		 * in static imports.
		 */
		return last == ';' || last == '.';
	}

}
