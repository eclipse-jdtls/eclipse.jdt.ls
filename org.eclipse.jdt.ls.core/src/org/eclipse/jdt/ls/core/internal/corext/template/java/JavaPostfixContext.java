/*******************************************************************************
 * Copyright (c) 2019, 2020 Nicolaj Hoess and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Nicolaj Hoess - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.jface.text.templates.TemplateVariable;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Copied from org.eclipse.jdt.internal.corext.template.java.JavaPostfixContext
 */
public class JavaPostfixContext extends JavaContext {

	private static final Object CONTEXT_TYPE_ID= "postfix"; //$NON-NLS-1$

	private static final String OBJECT_SIGNATURE= "java.lang.Object"; //$NON-NLS-1$

	private static final String ID_SEPARATOR= "��"; //$NON-NLS-1$

	private static final Pattern INNER_EXPRESSION_PATTERN= Pattern.compile("\\$\\{([a-zA-Z]*):" + InnerExpressionResolver.INNER_EXPRESSION_VAR + "\\(([^\\$|\\{|\\}]*)\\)\\}"); //$NON-NLS-1$ //$NON-NLS-2$

	private static final Pattern CLASS_NAME_PATTERN= Pattern.compile("[a-zA-Z0-9$_\\.]+"); //$NON-NLS-1$

	private ASTNode selectedNode;

	private Map<ASTNode, Region> nodeRegions;

	private Map<TemplateVariable, int[]> variableOutOfRangeOffsets;

	private boolean domInitialized;

	private BodyDeclaration bodyDeclaration;

	private ASTNode parentDeclaration;

	private CompletionContext completionCtx;

	// Map to store the additional text edits of the templates.
	private Map<String, List<TextEdit>> additionalTextEdits;

	private String activeTemplateName;

	public JavaPostfixContext(JavaPostfixContextType type, IDocument document, int offset, int length, ICompilationUnit compilationUnit, ASTNode currentNode, ASTNode parentNode, CompletionContext context) {
		super(type, document, offset, length, compilationUnit);

		completionCtx= context;
		nodeRegions= new HashMap<>();
		variableOutOfRangeOffsets= new HashMap<>();
		nodeRegions.put(currentNode, calculateNodeRegion(currentNode));
		nodeRegions.put(parentNode, calculateNodeRegion(parentNode));
		selectedNode= findBestASTNodeSelection(currentNode);
		additionalTextEdits= new HashMap<>();
	}

	/**
	 * Determines and returns the <i>best</i> {@link ASTNode} to apply a postfix template in the
	 * current context.<br/>
	 * Examples for <i>best</i> {@link ASTNode}s:
	 * <ul>
	 * <li><code>new Integer(0).$</code> will return the {@link ASTNode} for
	 * <code>new Integer(0)</code></li>
	 * </ul>
	 *
	 * @param currentNode The {@link ASTNode} of the completion.
	 * @return An {@link ASTNode} of the key set of {@link #nodeRegions}.
	 */
	private ASTNode findBestASTNodeSelection(ASTNode currentNode) {
		// This implementation takes the longest ASTNode by means of string length
		// which doesn't exceed the completion position.
		// If future extensions of the postfix implementation inject more than two ASTNodes
		// the selection should be more intelligent.
		// Some examples which should be considered for future implementations:
		// `1 + 1 + 1.$` should select `1 + 1 + 1` (note that the expression is not parenthesized).
		// `foo("asdf" + true.$` should select `true` since `"asdf" + true` makes no sense for any template in this context.
		// `"test" + true.$` should select `true` for "sif" template and `"test" + true` for "var" template.
		ASTNode result= currentNode;
		int currMax= getNodeBegin(currentNode) + getNodeLength(currentNode);
		Set<ASTNode> nodes= nodeRegions.keySet();
		int tokenLength= (completionCtx != null && completionCtx.getToken() != null) ? completionCtx.getToken().length : 0;
		int invOffset= completionCtx != null ? completionCtx.getOffset() - tokenLength - 1 : getCompletionOffset();
		for (ASTNode n : nodes) {
			int end= getNodeBegin(n) + getNodeLength(n);
			if (end > currMax && end <= invOffset) {
				currMax= end;
				result= n;
			}
		}
		return result;
	}

	/**
	 * Adds a new field to the {@link AST} using the given type and variable name. The method
	 * returns a {@link TextEdit} which can then be applied using the
	 * {@link #applyTextEdit(TextEdit)} method.
	 *
	 * @param type fully qualified type name of the new field
	 * @param varName suggested field name
	 * @param publicField <code>true</code> if the new field should be public
	 * @param staticField <code>true</code> if the new field should be static
	 * @param finalField <code>true</code> if the new field should be final
	 * @param value the initialization value of the new field. If this parameter is
	 *            <code>null</code> or empty the field is not initialized.
	 * @return a {@link TextEdit} which represents the changes which would be made, or
	 *         <code>null</code> if the field can not be created.
	 */
	public TextEdit addField(String type, String varName, boolean publicField, boolean staticField, boolean finalField, String value) {
		if (isReadOnly())
			return null;

		if (!domInitialized)
			initDomAST();

		boolean isStatic= isBodyStatic();
		int modifiers= (!publicField) ? Modifier.PRIVATE : Modifier.PUBLIC;
		if (isStatic || staticField) {
			modifiers|= Modifier.STATIC;
		}
		if (finalField) {
			modifiers|= Modifier.FINAL;
		}

		ASTRewrite rewrite= ASTRewrite.create(parentDeclaration.getAST());
		addFieldDeclaration(rewrite, parentDeclaration, modifiers, varName, type, value);
		TextEdit te= rewrite.rewriteAST(getDocument(), null);
		return te;
	}

	private VariableDeclarationFragment addFieldDeclaration(ASTRewrite rewrite, ASTNode newTypeDecl, int modifiers, String varName, String qualifiedName, String value) {
		ChildListPropertyDescriptor property= ASTNodes.getBodyDeclarationsProperty(newTypeDecl);
		List<BodyDeclaration> decls= ASTNodes.getBodyDeclarations(newTypeDecl);
		AST ast= newTypeDecl.getAST();

		VariableDeclarationFragment newDeclFrag= ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varName));

		Type type= createType(Signature.createTypeSignature(qualifiedName, true), ast);

		if (value != null && value.trim().length() > 0) {
			Expression e= createExpression(value);
			Expression ne= (Expression) ASTNode.copySubtree(ast, e);
			newDeclFrag.setInitializer(ne);
		} else {
			if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
				newDeclFrag.setInitializer(ASTNodeFactory.newDefaultExpression(ast, type, 0));
			}
		}

		FieldDeclaration newDecl= ast.newFieldDeclaration(newDeclFrag);
		newDecl.setType(type);
		newDecl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers));

		int insertIndex= findFieldInsertIndex(decls, getCompletionOffset(), modifiers);
		rewrite.getListRewrite(newTypeDecl, property).insertAt(newDecl, insertIndex, null);

		return newDeclFrag;
	}

	/**
	 * Adds all imports for a fully qualified generic type name, i.e. this method calls
	 * {@link #addImport(String)} for all "parts" of the given qualified name.
	 *
	 * @param className fully qualified name of a generic type name
	 * @return returns unqualified name of the generic type
	 */
	public String addImportGenericClass(String className) {
		Matcher m= CLASS_NAME_PATTERN.matcher(className);
		List<String> classNames= new ArrayList<>();
		Map<String, String> classNameMapping= new HashMap<>();
		while (m.find()) {
			classNames.add(className.substring(m.start(), m.end()));
		}
		/*
		 * In case the import class looks like this:
		 * a.b.c.Foo<b.c.Foo>
		 * we have to consider that - if we do not care about ordering, the following could happen:
		 * 1. trying to import b.c.Foo - import is resolved to Foo
		 * 2. replacing b.c.Foo with Foo - a.Foo<Foo> --> not correct (should be a.b.c.Foo<Foo>)
		 * 3. ...
		 *
		 * The solution to this is as follows:
		 * 1. sorting the fully qualified class names by length
		 * 2. replacing all occurring class names with unique identifiers ($$id$$)
		 * 3. importing all class names and map the fully qualified identifier with the resolved identifier of the class
		 * 4. replace the unique identifiers with the mapped values
		 */
		Collections.sort(classNames, (arg0, arg1) -> arg1.length() - arg0.length());
		for (int i= 0; i < classNames.size(); i++) {
			className= className.replace(classNames.get(i), ID_SEPARATOR + i + ID_SEPARATOR);
			classNameMapping.put(classNames.get(i), addImport(classNames.get(i)));
		}
		for (int i= 0; i < classNames.size(); i++) {
			className= className.replace(ID_SEPARATOR + i + ID_SEPARATOR, classNameMapping.get(classNames.get(i)));
		}
		return className;
	}

	/**
	 * Applies a {@link TextEdit} to the {@link IDocument} of this context and updates the
	 * completion offset variable.
	 *
	 * @param te {@link TextEdit} to apply
	 * @return <code>true</code> if the method was successful, <code>false</code> otherwise
	 */
	public boolean applyTextEdit(TextEdit te) {
		try {
			te.apply(getDocument());
			setCompletionOffset(getCompletionOffset() + ((te.getOffset() < getCompletionOffset()) ? te.getLength() : 0));
			return true;
		} catch (MalformedTreeException | BadLocationException e) {
			// fall through returning false
		}
		return false;
	}

	private Region calculateNodeRegion(ASTNode node) {
		if (node == null) {
			return new Region(0, 0);
		}
		int start= getNodeBegin(node);
		int end= getCompletionOffset() - getPrefixKey().length() - start - 1; // TODO Not entirely correct but good enough for our needs (calculation should be similar to getNodeBegin(..))
		return new Region(start, end);
	}

	/*
	 * @see TemplateContext#canEvaluate(Template templates)
	 */
	@Override
	public boolean canEvaluate(Template template) {
		if (!template.getContextTypeId().equals(JavaPostfixContext.CONTEXT_TYPE_ID))
			return false;

		if (isForceEvaluation())
			return true;

		// We can evaluate to true only if we have a valid inner expression
		// Do not evalute within Javadoc elements
		if (selectedNode == null || selectedNode instanceof Javadoc)
			return false;

		if (template.getName().toLowerCase().startsWith(getPrefixKey().toLowerCase()) == false) {
			return false;
		}

		if (selectedNode instanceof SimpleName) {
			IBinding binding = ((SimpleName) selectedNode).resolveBinding();
			// return false when the binding of the simple name is not a variable,
			// and it's not a recovered AST. This is to make sure postfix will be
			// skipped for cases like 'System.|'
			if (!(binding instanceof IVariableBinding) && binding != null && !binding.isRecovered()) {
				return false;
			}
		}

		// We check if the template makes "sense" by checking the requirements/conditions for the template
		// For this purpose we have to resolve the inner_expression variable of the template
		// This approach is much faster then delegating this to the existing TemplateTranslator class
		// (Maybe this hard-coded dependency to the inner expression variable is a little bit weird)
		Matcher matcher= INNER_EXPRESSION_PATTERN.matcher(template.getPattern());
		boolean result= true;

		while (matcher.find()) {
			String[] types= matcher.group(2).split(","); //$NON-NLS-1$
			for (String s : types) {
				if (!Arrays.asList(InnerExpressionResolver.FLAGS).contains(s)) {
					result= false;
					if (this.isNodeResolvingTo(selectedNode, s.trim()) == true) {
						return true;
					}
				}
			}
		}
		return result;
	}

	private boolean containsNestedCapture(String signature) {
		return signature.length() > 1 && signature.indexOf(Signature.C_CAPTURE, 1) != -1;
	}

	private Expression createExpression(String expr) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setKind(ASTParser.K_EXPRESSION);
		parser.setResolveBindings(true);
		parser.setSource(expr.toCharArray());
		ASTNode astNode= parser.createAST(new NullProgressMonitor());
		return (Expression) astNode;
	}

	private Type createType(String typeSig, AST ast) {
		int sigKind= Signature.getTypeSignatureKind(typeSig);
		switch (sigKind) {
			case Signature.BASE_TYPE_SIGNATURE:
				return ast.newPrimitiveType(PrimitiveType.toCode(Signature.toString(typeSig)));
			case Signature.ARRAY_TYPE_SIGNATURE:
				Type elementType= createType(Signature.getElementType(typeSig), ast);
				return ast.newArrayType(elementType, Signature.getArrayCount(typeSig));
			case Signature.CLASS_TYPE_SIGNATURE:
				String erasureSig= Signature.getTypeErasure(typeSig);

				String erasureName= Signature.toString(erasureSig);
				if (erasureSig.charAt(0) == Signature.C_RESOLVED) {
					erasureName= addImport(erasureName);
				}

				Type baseType= ast.newSimpleType(ast.newName(erasureName));
				String[] typeArguments= Signature.getTypeArguments(typeSig);
				if (typeArguments.length > 0) {
					ParameterizedType type= ast.newParameterizedType(baseType);
					List<Type> argNodes= type.typeArguments();
					for (String curr : typeArguments) {
						if (containsNestedCapture(curr)) {
							argNodes.add(ast.newWildcardType());
						} else {
							argNodes.add(createType(curr, ast));
						}
					}
					return type;
				}
				return baseType;
			case Signature.TYPE_VARIABLE_SIGNATURE:
				return ast.newSimpleType(ast.newSimpleName(Signature.toString(typeSig)));
			case Signature.WILDCARD_TYPE_SIGNATURE:
				WildcardType wildcardType= ast.newWildcardType();
				char ch= typeSig.charAt(0);
				if (ch != Signature.C_STAR) {
					Type bound= createType(typeSig.substring(1), ast);
					wildcardType.setBound(bound, ch == Signature.C_EXTENDS);
				}
				return wildcardType;
			case Signature.CAPTURE_TYPE_SIGNATURE:
				return createType(typeSig.substring(1), ast);
		}
		return ast.newSimpleType(ast.newName(OBJECT_SIGNATURE));
	}

	@Override
	public TemplateBuffer evaluate(Template template)
			throws BadLocationException, TemplateException {
		TemplateBuffer result= super.evaluate(template);

		// After the template buffer has been created we are able to add out of range offsets
		// This is not possible beforehand as it will result in an exception!
		for (TemplateVariable tv : result.getVariables()) {
			int[] outOfRangeOffsets= variableOutOfRangeOffsets.get(tv);
			if (outOfRangeOffsets != null && outOfRangeOffsets.length > 0) {
				int[] offsets= tv.getOffsets();
				int[] newOffsets= Arrays.copyOf(offsets, offsets.length + outOfRangeOffsets.length);
				System.arraycopy(outOfRangeOffsets, 0, newOffsets, offsets.length, outOfRangeOffsets.length);
				tv.setOffsets(newOffsets);
			}
		}
		return result;
	}

	private int findFieldInsertIndex(List<BodyDeclaration> decls, int currPos, int modifiers) {
		for (int i= decls.size() - 1; i >= 0; i--) {
			ASTNode curr= decls.get(i);
			if (curr instanceof FieldDeclaration && currPos > curr.getStartPosition() + curr.getLength()
					&& ((FieldDeclaration) curr).getModifiers() == modifiers) {
				return i + 1;
			}
		}
		return 0;
	}

	/**
	 * Returns the {@link Region} which represents the source region of the affected statement.
	 *
	 * @return the source region of the affected statement
	 */
	public Region getAffectedSourceRegion() {
		return new Region(getCompletionOffset() - getPrefixKey().length() - nodeRegions.get(selectedNode).getLength() - 1, nodeRegions.get(selectedNode).getLength());
	}

	public String getAffectedStatement() {
		Region r= getAffectedSourceRegion();
		try {
			return getDocument().get(r.getOffset(), r.getLength());
		} catch (BadLocationException e) {
			// fall through returning empty string
		}
		return ""; //$NON-NLS-1$
	}

	@Override
	public int getEnd() {
		return getCompletionOffset();
	}

	/**
	 * Returns the fully qualified name the node of the current code completion invocation resolves
	 * to.
	 *
	 * @return a fully qualified type signature or the name of the base type.
	 */
	public String getInnerExpressionTypeSignature() {
		return resolveNodeToTypeString(selectedNode);
	}

	/**
	 * Calculates the beginning position of a given {@link ASTNode}
	 *
	 * @param node the {@link ASTNode}
	 * @return source position of the node or -1 if the given node is <code>null</code>
	 */
	protected int getNodeBegin(ASTNode node) {
		if (node == null) {
			return -1;
		}
		if (node.getParent() instanceof MethodInvocation) {
			return ((MethodInvocation) node.getParent()).getStartPosition();
		} else if (node.getParent() instanceof FieldAccess || node.getParent() instanceof SuperFieldAccess) {
			return node.getParent().getStartPosition();
		} else if (node instanceof Name) {
			ASTNode n= node;
			while (n.getParent() instanceof QualifiedName) {
				n= n.getParent();
			}
			return ((Name) n).getStartPosition();
		}
		return node.getStartPosition();
	}

	/**
	 * Calculates the length of a given {@link ASTNode}
	 *
	 * @param node the {@link ASTNode}
	 * @return length of the node or -1 if the given node is <code>null</code>
	 */
	protected int getNodeLength(ASTNode node) {
		if (node == null) {
			return -1;
		}
		return node.getLength();
	}

	/**
	 * Returns the current prefix of the key which was typed in. <br/>
	 * Examples: <code>
	 * <br/>
	 * new Object().		=> getPrefixKey() returns ""<br/>
	 * new Object().a		=> getPrefixKey() returns "a"<br/>
	 * new object().asdf	=> getPrefixKey() returns "asdf"<br/>
	 * </code>
	 *
	 * @return an empty string or a string which represents the prefix of the key which was typed in
	 */
	private String getPrefixKey() {
		if (completionCtx != null) {
			IDocument document= getDocument();
			int start= completionCtx.getTokenStart();
			int end= completionCtx.getTokenEnd();
			try {
				return document.get(start, end - start + 1);
			} catch (BadLocationException e) {
				// fall through returning empty string
			}
		}
		return ""; //$NON-NLS-1$
	}

	@Override
	public int getStart() {
		int result= super.getStart();
		result-= getAffectedSourceRegion().getLength() + 1;
		return result;
	}

	private void initDomAST() {
		if (isReadOnly())
			return;

		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(getCompilationUnit());
		parser.setResolveBindings(true);
		ASTNode domAst= parser.createAST(new NullProgressMonitor());

		NodeFinder nf= new NodeFinder(domAst, getCompletionOffset(), 1);
		ASTNode cv= nf.getCoveringNode();

		bodyDeclaration= ASTResolving.findParentBodyDeclaration(cv);
		parentDeclaration= ASTResolving.findParentType(cv);
		domInitialized= true;
	}

	/**
	 * Returns <code>true</code> if the type or one of its supertypes of a given {@link ASTNode}
	 * resolves to a given type signature. <br/>
	 * Examples: <br/>
	 * <code>
	 * <br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Object") returns true<br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Iterable") returns false<br/>
	 * </code>
	 *
	 * TODO Implement this method without using the recursive helper method if there are any
	 * performance/stackoverflow issues
	 *
	 * @param node an ASTNode
	 * @param signature a fully qualified type
	 * @return true if the type of the given ASTNode itself or one of its superclass/superinterfaces
	 *         resolves to the given signature. false otherwise.
	 */
	private boolean isNodeResolvingTo(ASTNode node, String signature) {
		if (signature == null || signature.trim().length() == 0) {
			return true;
		}
		ITypeBinding tb= resolveNodeToBinding(node);
		if (tb != null && tb.isPrimitive()) {
			return (new String(tb.getQualifiedName()).equals(signature));
		} else {
			return resolvesReferenceBindingTo(tb, signature);
		}
	}

	private boolean isBodyStatic() {
		boolean isAnonymous= parentDeclaration.getNodeType() == ASTNode.ANONYMOUS_CLASS_DECLARATION;
		boolean isStatic= Modifier.isStatic(bodyDeclaration.getModifiers()) && !isAnonymous;
		return isStatic;
	}

	public void registerOutOfRangeOffset(TemplateVariable var, int absoluteOffset) {
		if (variableOutOfRangeOffsets.get(var) == null) {
			variableOutOfRangeOffsets.put(var, new int[] { absoluteOffset });
		} else {
			int[] temp= variableOutOfRangeOffsets.get(var);
			int[] newArr= new int[temp.length + 1];
			System.arraycopy(temp, 0, newArr, 0, temp.length);
			newArr[temp.length]= absoluteOffset;
			variableOutOfRangeOffsets.put(var, newArr);
		}
	}

	private ITypeBinding resolveNodeToBinding(ASTNode node) {
		if (node instanceof StringLiteral) {
			return ((StringLiteral) node).resolveTypeBinding();
		}

		ITypeBinding[] res= new ITypeBinding[1];
		node.accept(new ASTVisitor() {

			@Override
			public boolean visit(MethodInvocation n) {
				res[0]= n.resolveTypeBinding();
				return false;
			}

			@Override
			public boolean visit(SimpleName n) {
				IBinding b= n.resolveBinding();
				if (b instanceof IVariableBinding) {
					IVariableBinding vb= (IVariableBinding) b;
					res[0]= vb.getType();
				} else if (b instanceof IMethodBinding) {
					IMethodBinding mb= (IMethodBinding) b;
					res[0]= mb.getReturnType();
				}
				return false;
			}

			@Override
			public boolean visit(QualifiedName n) {
				IBinding b= n.resolveBinding();
				if (b instanceof IVariableBinding) {
					IVariableBinding vb= (IVariableBinding) b;
					res[0]= vb.getType();
				}
				return false;
			}

			@Override
			public boolean visit(FieldAccess n) {
				ITypeBinding tmp= n.getName().resolveTypeBinding();
				if (tmp != null) {
					res[0]= tmp;
					return false;
				}
				res[0]= n.getExpression().resolveTypeBinding();
				return false;
			}

			@Override
			public boolean visit(Assignment n) {
				ITypeBinding tmp= n.getLeftHandSide().resolveTypeBinding();
				if (tmp != null) {
					res[0]= tmp;
					return false;
				}
				return true;
			}

			@Override
			public boolean visit(BooleanLiteral n) {
				res[0]= n.resolveTypeBinding();
				return false;
			}

			@Override
			public boolean visit(InfixExpression n) {
				res[0]= n.resolveTypeBinding();
				return false;
			}

			@Override
			public boolean visit(ClassInstanceCreation n) {
				res[0]= n.resolveTypeBinding();
				return false;
			}

			@Override
			public boolean visit(ArrayAccess n) {
				res[0]= n.resolveTypeBinding();
				return false;
			}
		});

		return res[0] != null ? res[0] : null;
	}

	private String resolveNodeToTypeString(ASTNode node) {
		ITypeBinding b= resolveNodeToBinding(node);
		if (b == null) {
			return OBJECT_SIGNATURE;
		}
		String result= b.getQualifiedName();
		if (result.isEmpty() && b.isCapture()) {
			for (ITypeBinding tb : b.getTypeBounds()) {
				result= tb.getQualifiedName();
				if (!result.isEmpty()) {
					return result;
				}
			}
		}
		return result;
	}

	/**
	 * This is a recursive method which performs a depth first search in the inheritance graph of
	 * the given {@link ITypeBinding}.
	 *
	 * @param sb a TypeBinding
	 * @param signature a fully qualified type
	 * @return <code>true</code> if the given TypeBinding itself or one of its
	 *         superclass/superinterfaces resolves to the given signature, <code>false</code>
	 *         otherwise.
	 */
	private boolean resolvesReferenceBindingTo(ITypeBinding sb, String signature) {
		if (sb == null) {
			return false;
		}
		if (new String(sb.getQualifiedName()).startsWith(signature) || (sb.isArray() && "array".equals(signature))) { //$NON-NLS-1$
			return true;
		}
		if (Object.class.getName().equals(signature)) {
			return true;
		}

		List<ITypeBinding> bindings= new ArrayList<>();
		Collections.addAll(bindings, sb.getInterfaces());
		bindings.add(sb.getSuperclass());
		boolean result= false;
		Iterator<ITypeBinding> it= bindings.iterator();
		while (it.hasNext() && result == false) {
			result= resolvesReferenceBindingTo(it.next(), signature);
		}
		return result;
	}

	public String[] suggestFieldName(String type, String[] excludes, boolean staticField, boolean finalField) throws IllegalArgumentException {
		int dim= 0;
		while (type.endsWith("[]")) { //$NON-NLS-1$
			dim++;
			type= type.substring(0, type.length() - 2);
		}

		IJavaProject project= getJavaProject();
		int namingConventions= 0;
		if (staticField && finalField) {
			namingConventions= NamingConventions.VK_STATIC_FINAL_FIELD;
		} else if (staticField && !finalField) {
			namingConventions= NamingConventions.VK_STATIC_FIELD;
		} else {
			namingConventions= NamingConventions.VK_INSTANCE_FIELD;
		}
		if (project != null)
			return StubUtility.getVariableNameSuggestions(namingConventions, project, type, dim, Arrays.asList(excludes), true);

		return new String[] { Signature.getSimpleName(type).toLowerCase() };
	}

	public String[] suggestFieldName(String type, boolean finalField, boolean forceStatic) {
		if (!domInitialized) {
			initDomAST();
		}
		if (domInitialized) {
			return suggestFieldName(type, ASTResolving.getUsedVariableNames(bodyDeclaration), (forceStatic) ? forceStatic : isBodyStatic(), finalField);
		}
		// If the dom is not initialized yet (template preview) we return a dummy name
		return new String[] { "newField" }; //$NON-NLS-1$
	}

	@Override
	public String[] suggestVariableNames(String type) throws IllegalArgumentException {
		List<String> res= new ArrayList<>();
		if (selectedNode instanceof Expression) {
			ITypeBinding tb= resolveNodeToBinding(selectedNode);
			List<String> excludes= Arrays.asList(computeExcludes());
			String[] names= StubUtility.getVariableNameSuggestions(NamingConventions.VK_LOCAL, getJavaProject(), tb, (Expression)selectedNode, excludes);
			res.addAll(Arrays.asList(names));
		}
		res.addAll(Arrays.asList(super.suggestVariableNames(type)));
		return res.toArray(new String [0]);
	}

	/**
	 * Get additional text edits for a template. Usually this is used to add import statements.
	 * @param name name of the template.
	 */
	public List<TextEdit> getAdditionalTextEdits(String name) {
		return additionalTextEdits.get(name);
	}

	public void setActiveTemplateName(String activeTemplateName) {
		this.activeTemplateName = activeTemplateName;
	}

	@Override
	public String addImport(String type) {
		/**
		 * Resolve the add import calculation and cache it. The cached edit will be used
		 * as the additional text edit of the completion items.
		 */
		ICompilationUnit cu = getCompilationUnit();
		if (cu == null)
			return type;

		try {
			boolean qualified = type.indexOf('.') != -1;
			if (!qualified) {
				// the search engine is not used to make sure the completion perf is not impacted.
				return type;
			}

			CompilationUnit root = getASTRoot(cu);
			ImportRewrite importRewrite;
			if (root == null) {
				importRewrite = StubUtility.createImportRewrite(cu, true);
			} else {
				importRewrite = StubUtility.createImportRewrite(root, true);
			}

			ImportRewriteContext context;
			if (root == null) {
				context = null;
			} else {
				context = new ContextSensitiveImportRewriteContext(root, getCompletionOffset(), importRewrite);
			}

			String typeName = importRewrite.addImport(type, context);
			if (StringUtils.isNotBlank(this.activeTemplateName)) {
				List<TextEdit> edits = this.additionalTextEdits.getOrDefault(this.activeTemplateName, new ArrayList<>());
				try {
					edits.add(importRewrite.rewriteImports(new NullProgressMonitor()));
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
				this.additionalTextEdits.put(this.activeTemplateName, edits);
			}
			return typeName;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
			return type;
		}
	}

	private CompilationUnit getASTRoot(ICompilationUnit compilationUnit) {
		return SharedASTProviderCore.getAST(compilationUnit, SharedASTProviderCore.WAIT_NO, new NullProgressMonitor());
	}
}
