/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Referenced the origin implementation:
 * org.eclipse.jdt.internal.corext.refactoring.code.ExtractTempRefactoring
 * org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Microsoft Corporation - create the new refactoring implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.ls.core.internal.corext.dom.fragments.ASTFragmentFactory;
import org.eclipse.jdt.ls.core.internal.corext.dom.fragments.IASTFragment;
import org.eclipse.jdt.ls.core.internal.corext.dom.fragments.IExpressionFragment;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.util.NoCommentSourceRangeComputer;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.ls.core.internal.corrections.ASTResolving;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

public class ExtractFieldRefactoring extends Refactoring {
	public static final int INITIALIZE_IN_FIELD = 0;
	public static final int INITIALIZE_IN_METHOD = 1;
	public static final int INITIALIZE_IN_CONSTRUCTOR = 2;

	private int fSelectionStart;
	private int fSelectionLength;
	private ICompilationUnit fCu;
	private CompilationUnit fCompilationUnitNode;
	private String[] fGuessedFieldNames;

	private LinkedProposalModelCore fLinkedProposalModel;
	private IExpressionFragment fSelectedExpression;
	private String[] fExcludedVariableNames;
	private String[] fExcludedFieldNames;
	private CompilationUnitRewrite fCURewrite;
	private boolean fDeclareFinal;
	private String fFieldName;
	private int fVisibility;
	private int fInitializeIn;
	private Map fFormatterOptions;
	private boolean fInitializerUsesLocalTypes;
	private boolean fDeclareStatic;

	private static final String KEY_NAME = "name"; //$NON-NLS-1$
	private static final String KEY_TYPE = "type"; //$NON-NLS-1$

	/**
	 * Creates a new extract field refactoring
	 *
	 * @param unit
	 *            the compilation unit, or <code>null</code> if invoked by scripting
	 * @param selectionStart
	 *            start of selection
	 * @param selectionLength
	 *            length of selection
	 */
	public ExtractFieldRefactoring(ICompilationUnit unit, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		fSelectionStart = selectionStart;
		fSelectionLength = selectionLength;
		fCu = unit;
		fCompilationUnitNode = null;

		fFieldName = ""; //$NON-NLS-1$

		fLinkedProposalModel = null;
		fVisibility = Modifier.PRIVATE;
		fDeclareFinal = false;
		fDeclareStatic = false;
		fInitializeIn = INITIALIZE_IN_METHOD;
	}

	public ExtractFieldRefactoring(CompilationUnit astRoot, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		Assert.isTrue(astRoot.getTypeRoot() instanceof ICompilationUnit);

		fSelectionStart = selectionStart;
		fSelectionLength = selectionLength;
		fCu = (ICompilationUnit) astRoot.getTypeRoot();
		fCompilationUnitNode = astRoot;

		fDeclareFinal = false;
		fDeclareStatic = false;
		fFieldName = ""; //$NON-NLS-1$

		fLinkedProposalModel = null;
		fVisibility = Modifier.PRIVATE;
		fInitializeIn = INITIALIZE_IN_METHOD;
	}

	@Override
	public String getName() {
		return RefactoringCoreMessages.ExtractFieldRefactoring_name;
	}

	public int getVisibility() {
		return fVisibility;
	}

	public boolean getDeclareFinal() {
		return fDeclareFinal;
	}

	public int getInitializeIn() {
		return fInitializeIn;
	}

	public Map<String, String> getFormatterOptions() {
		return fFormatterOptions;
	}

	public void setInitializeIn(int initializeIn) {
		Assert.isTrue(initializeIn == INITIALIZE_IN_CONSTRUCTOR || initializeIn == INITIALIZE_IN_FIELD || initializeIn == INITIALIZE_IN_METHOD);
		fInitializeIn = initializeIn;
	}

	/**
	 * Set the formatter options to format the refactored code.
	 *
	 * @param formatterOptions
	 *            the formatter options to format the refactored code
	 */
	public void setFormatterOptions(Map<String, String> formatterOptions) {
		fFormatterOptions = formatterOptions;
	}

	public boolean canEnableSettingDeclareInConstructors() throws JavaModelException {
		return !fDeclareStatic && !fInitializerUsesLocalTypes && !getMethodDeclaration().isConstructor() && !isDeclaredInAnonymousClass() && !isDeclaredInStaticMethod();
	}

	public boolean canEnableSettingDeclareInMethod() {
		return !fDeclareFinal;
	}

	public boolean canEnableSettingDeclareInFieldDeclaration() {
		return !fInitializerUsesLocalTypes;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask("", 16); //$NON-NLS-1$

			RefactoringStatus result = Checks.validateModifiesFiles(ResourceUtil.getFiles(new ICompilationUnit[] { fCu }), getValidationContext(), pm);
			if (result.hasFatalError()) {
				return result;
			}

			if (fCompilationUnitNode == null) {
				fCompilationUnitNode = RefactoringASTParser.parseWithASTProvider(fCu, true, new SubProgressMonitor(pm, 3));
			}
			pm.worked(1);

			if (fCURewrite == null) {
				fCURewrite = new CompilationUnitRewrite(fCu, fCompilationUnitNode);
				fCURewrite.setFormattingOptions(fFormatterOptions);
				fCURewrite.getASTRewrite().setTargetSourceRangeComputer(new NoCommentSourceRangeComputer());
			}
			pm.worked(1);

			// Check the conditions for extracting an expression to a variable.
			IExpressionFragment selectedExpression = getSelectedExpression();
			if (selectedExpression == null) {
				String message = RefactoringCoreMessages.ExtractTempRefactoring_select_expression;
				return CodeRefactoringUtil.checkMethodSyntaxErrors(fSelectionStart, fSelectionLength, fCompilationUnitNode, message);
			}
			pm.worked(1);

			if (isUsedInExplicitConstructorCall()) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_explicit_constructor);
			}
			pm.worked(1);

			ASTNode associatedNode = selectedExpression.getAssociatedNode();
			if (getEnclosingBodyNode() == null || ASTNodes.getParent(associatedNode, Annotation.class) != null) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_expr_in_method_or_initializer);
			}
			pm.worked(1);

			if (associatedNode instanceof Name && associatedNode.getParent() instanceof ClassInstanceCreation && associatedNode.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_name_in_new);
			}
			pm.worked(1);

			result.merge(checkExpression());
			if (result.hasFatalError()) {
				return result;
			}
			pm.worked(1);

			result.merge(checkExpressionFragmentIsRValue());
			if (result.hasFatalError()) {
				return result;
			}
			pm.worked(1);

			Expression associatedExpression = selectedExpression.getAssociatedExpression();
			if (isUsedInForInitializerOrUpdater(associatedExpression)) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_for_initializer_updater);
			}
			pm.worked(1);

			if (isReferringToLocalVariableFromFor(associatedExpression)) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_refers_to_for_variable);
			}
			pm.worked(1);

			// Check the conditions for extracting an expression to field.
			ASTNode declaringType = getEnclosingTypeDeclaration();
			if (declaringType instanceof TypeDeclaration && ((TypeDeclaration) declaringType).isInterface()) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractFieldRefactoring_interface_methods);
			}
			pm.worked(1);

			result.merge(checkTempTypeForLocalTypeUsage());
			if (result.hasFatalError()) {
				return result;
			}
			pm.worked(1);

			checkTempInitializerForLocalTypeUsage();
			initializeDefaults();
			pm.worked(1);

			return result;
		} finally {
			pm.done();
		}
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask(RefactoringCoreMessages.ExtractTempRefactoring_checking_preconditions, 4);
			RefactoringStatus result = new RefactoringStatus();
			result.merge(checkMatchingFragments());
			return result;
		} finally {
			pm.done();
		}
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask(RefactoringCoreMessages.ExtractFieldRefactoring_creating_change, 1);

			try {
				if (fInitializeIn == INITIALIZE_IN_METHOD) {
					addInitializerToMethod();
				} else if (fInitializeIn == INITIALIZE_IN_CONSTRUCTOR) {
					addInitializersToConstructors(fCURewrite.getASTRewrite());
				}

				addFieldDeclaration();
				addReplaceExpressionWithField();
			} catch (CoreException exception) {
				JavaLanguageServerPlugin.logException("Problem with extract temp filed ", exception);
			}

			return fCURewrite.createChange(RefactoringCoreMessages.ExtractFieldRefactoring_name, true, new SubProgressMonitor(pm, 1));
		} finally {
			pm.done();
		}
	}

	private void initializeDefaults() throws JavaModelException {
		fVisibility = Modifier.PRIVATE;
		fDeclareStatic = isDeclaredInStaticMethod();
		fDeclareFinal = false;
		if (canEnableSettingDeclareInMethod()) {
			fInitializeIn = INITIALIZE_IN_METHOD;
		} else if (canEnableSettingDeclareInFieldDeclaration()) {
			fInitializeIn = INITIALIZE_IN_FIELD;
		} else if (canEnableSettingDeclareInConstructors()) {
			fInitializeIn = INITIALIZE_IN_CONSTRUCTOR;
		}
	}

	private IExpressionFragment getSelectedExpression() throws JavaModelException {
		if (fSelectedExpression != null) {
			return fSelectedExpression;
		}
		IASTFragment selectedFragment = ASTFragmentFactory.createFragmentForSourceRange(new SourceRange(fSelectionStart, fSelectionLength), fCompilationUnitNode, fCu);

		if (selectedFragment instanceof IExpressionFragment && !Checks.isInsideJavadoc(selectedFragment.getAssociatedNode())) {
			fSelectedExpression = (IExpressionFragment) selectedFragment;
		} else if (selectedFragment != null) {
			if (selectedFragment.getAssociatedNode() instanceof ExpressionStatement) {
				ExpressionStatement exprStatement = (ExpressionStatement) selectedFragment.getAssociatedNode();
				Expression expression = exprStatement.getExpression();
				fSelectedExpression = (IExpressionFragment) ASTFragmentFactory.createFragmentForFullSubtree(expression);
			} else if (selectedFragment.getAssociatedNode() instanceof Assignment) {
				Assignment assignment = (Assignment) selectedFragment.getAssociatedNode();
				fSelectedExpression = (IExpressionFragment) ASTFragmentFactory.createFragmentForFullSubtree(assignment);
			}
		}

		if (fSelectedExpression != null && Checks.isEnumCase(fSelectedExpression.getAssociatedExpression().getParent())) {
			fSelectedExpression = null;
		}

		return fSelectedExpression;
	}

	private boolean isUsedInExplicitConstructorCall() throws JavaModelException {
		Expression selectedExpression = getSelectedExpression().getAssociatedExpression();
		if (ASTNodes.getParent(selectedExpression, ConstructorInvocation.class) != null) {
			return true;
		}
		if (ASTNodes.getParent(selectedExpression, SuperConstructorInvocation.class) != null) {
			return true;
		}
		return false;
	}

	private ASTNode getEnclosingBodyNode() throws JavaModelException {
		ASTNode node = getSelectedExpression().getAssociatedNode();

		// expression must be in a method, lambda or initializer body.
		// make sure it is not in method or parameter annotation
		StructuralPropertyDescriptor location = null;
		while (node != null && !(node instanceof BodyDeclaration)) {
			location = node.getLocationInParent();
			node = node.getParent();
			if (node instanceof LambdaExpression) {
				break;
			}
		}
		if (location == MethodDeclaration.BODY_PROPERTY || location == Initializer.BODY_PROPERTY || (location == LambdaExpression.BODY_PROPERTY && ((LambdaExpression) node).resolveMethodBinding() != null)) {
			return (ASTNode) node.getStructuralProperty(location);
		}
		return null;
	}

	private RefactoringStatus checkExpression() throws JavaModelException {
		Expression selectedExpression = getSelectedExpression().getAssociatedExpression();
		if (selectedExpression != null) {
			final ASTNode parent = selectedExpression.getParent();
			if (selectedExpression instanceof NullLiteral) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_null_literals);
			} else if (selectedExpression instanceof ArrayInitializer) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_array_initializer);
			} else if (selectedExpression instanceof Assignment) {
				if (parent instanceof Expression && !(parent instanceof ParenthesizedExpression)) {
					return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_assignment);
				} else {
					return null;
				}
			} else if (selectedExpression instanceof SimpleName) {
				if ((((SimpleName) selectedExpression)).isDeclaration()) {
					return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_names_in_declarations);
				}
				if (parent instanceof QualifiedName && selectedExpression.getLocationInParent() == QualifiedName.NAME_PROPERTY || parent instanceof FieldAccess && selectedExpression.getLocationInParent() == FieldAccess.NAME_PROPERTY) {
					return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_select_expression);
				}
			} else if (selectedExpression instanceof VariableDeclarationExpression && parent instanceof TryStatement) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_resource_in_try_with_resources);
			}
		}

		return null;
	}

	// !! Same as in ExtractConstantRefactoring
	private RefactoringStatus checkExpressionFragmentIsRValue() throws JavaModelException {
		switch (Checks.checkExpressionIsRValue(getSelectedExpression().getAssociatedExpression())) {
			case Checks.NOT_RVALUE_MISC:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.ExtractTempRefactoring_select_expression, null, IConstants.PLUGIN_ID, RefactoringStatusCodes.EXPRESSION_NOT_RVALUE, null);
			case Checks.NOT_RVALUE_VOID:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.ExtractTempRefactoring_no_void, null, IConstants.PLUGIN_ID, RefactoringStatusCodes.EXPRESSION_NOT_RVALUE_VOID, null);
			case Checks.IS_RVALUE_GUESSED:
			case Checks.IS_RVALUE:
				return new RefactoringStatus();
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	private static boolean isUsedInForInitializerOrUpdater(Expression expression) {
		ASTNode parent = expression.getParent();
		if (parent instanceof ForStatement) {
			ForStatement forStmt = (ForStatement) parent;
			return forStmt.initializers().contains(expression) || forStmt.updaters().contains(expression);
		}
		return false;
	}

	private static boolean isReferringToLocalVariableFromFor(Expression expression) {
		ASTNode current = expression;
		ASTNode parent = current.getParent();
		while (parent != null && !(parent instanceof BodyDeclaration)) {
			if (parent instanceof ForStatement) {
				ForStatement forStmt = (ForStatement) parent;
				if (forStmt.initializers().contains(current) || forStmt.updaters().contains(current) || forStmt.getExpression() == current) {
					List<Expression> initializers = forStmt.initializers();
					if (initializers.size() == 1 && initializers.get(0) instanceof VariableDeclarationExpression) {
						List<IVariableBinding> forInitializerVariables = getForInitializedVariables((VariableDeclarationExpression) initializers.get(0));
						ForStatementChecker checker = new ForStatementChecker(forInitializerVariables);
						expression.accept(checker);
						if (checker.isReferringToForVariable()) {
							return true;
						}
					}
				}
			}
			current = parent;
			parent = current.getParent();
		}
		return false;
	}

	private static List<IVariableBinding> getForInitializedVariables(VariableDeclarationExpression variableDeclarations) {
		List<IVariableBinding> forInitializerVariables = new ArrayList<>(1);
		for (Iterator<VariableDeclarationFragment> iter = variableDeclarations.fragments().iterator(); iter.hasNext();) {
			VariableDeclarationFragment fragment = iter.next();
			IVariableBinding binding = fragment.resolveBinding();
			if (binding != null) {
				forInitializerVariables.add(binding);
			}
		}
		return forInitializerVariables;
	}

	private RefactoringStatus checkTempTypeForLocalTypeUsage() throws JavaModelException {
		Expression expression = getSelectedExpression().getAssociatedExpression();
		Type resultingType = null;
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		AST ast = fCURewrite.getAST();

		if (expression instanceof ClassInstanceCreation && (typeBinding == null || typeBinding.getTypeArguments().length == 0)) {
			resultingType = ((ClassInstanceCreation) expression).getType();
		} else if (expression instanceof CastExpression) {
			resultingType = ((CastExpression) expression).getType();
		} else {
			if (typeBinding == null) {
				typeBinding = ASTResolving.guessBindingForReference(expression);
			}
			if (typeBinding != null) {
				typeBinding = Bindings.normalizeForDeclarationUse(typeBinding, ast);
				ImportRewrite importRewrite = fCURewrite.getImportRewrite();
				ImportRewriteContext context = new ContextSensitiveImportRewriteContext(expression, importRewrite);
				resultingType = importRewrite.addImport(typeBinding, ast, context, TypeLocation.LOCAL_VARIABLE);
			} else {
				resultingType = ast.newSimpleType(ast.newSimpleName("Object")); //$NON-NLS-1$
			}
		}

		IMethodBinding declaringMethodBinding = getMethodDeclaration().resolveBinding();
		ITypeBinding[] methodTypeParameters = declaringMethodBinding == null ? new ITypeBinding[0] : declaringMethodBinding.getTypeParameters();
		LocalTypeAndVariableUsageAnalyzer analyzer = new LocalTypeAndVariableUsageAnalyzer(methodTypeParameters);
		resultingType.accept(analyzer);
		boolean usesLocalTypes = !analyzer.getUsageOfEnclosingNodes().isEmpty();
		if (usesLocalTypes) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractFieldRefactoring_uses_type_declared_locally);
		}
		return null;
	}

	private void checkTempInitializerForLocalTypeUsage() throws JavaModelException {
		Expression initializer = getSelectedExpression().getAssociatedExpression();
		IMethodBinding declaringMethodBinding = getMethodDeclaration().resolveBinding();
		ITypeBinding[] methodTypeParameters = declaringMethodBinding == null ? new ITypeBinding[0] : declaringMethodBinding.getTypeParameters();
		LocalTypeAndVariableUsageAnalyzer localTypeAnalyer = new LocalTypeAndVariableUsageAnalyzer(methodTypeParameters);
		initializer.accept(localTypeAnalyer);
		fInitializerUsesLocalTypes = !localTypeAnalyer.getUsageOfEnclosingNodes().isEmpty();
	}

	public void setLinkedProposalModel(LinkedProposalModelCore linkedProposalModel) {
		fLinkedProposalModel = linkedProposalModel;
	}

	public String guessFieldName() {
		String[] proposals = guessFieldNames();
		if (proposals.length == 0) {
			return fFieldName;
		} else {
			return proposals[0];
		}
	}

	/**
	 * @return proposed field names (may be empty, but not null). The first proposal
	 *         should be used as "best guess" (if it exists).
	 */
	public String[] guessFieldNames() {
		if (fGuessedFieldNames == null) {
			try {
				Expression expression = getSelectedExpression().getAssociatedExpression();
				if (expression != null) {
					ITypeBinding binding = guessBindingForReference(expression);
					int modifiers = getModifiers();
					int variableKind;
					if (Flags.isFinal(modifiers) && Flags.isStatic(modifiers)) {
						variableKind = NamingConventions.VK_STATIC_FINAL_FIELD;
					} else if (Flags.isStatic(modifiers)) {
						variableKind = NamingConventions.VK_STATIC_FIELD;
					} else {
						variableKind = NamingConventions.VK_INSTANCE_FIELD;
					}

					fGuessedFieldNames = StubUtility.getVariableNameSuggestions(variableKind, fCu.getJavaProject(), binding, expression, Arrays.asList(getExcludedFieldNames()));
				}
			} catch (JavaModelException e) {
			}
			if (fGuessedFieldNames == null) {
				fGuessedFieldNames = new String[0];
			}
		}
		return fGuessedFieldNames;
	}

	private ITypeBinding guessBindingForReference(Expression expression) {
		ITypeBinding binding = expression.resolveTypeBinding();
		if (binding == null) {
			binding = ASTResolving.guessBindingForReference(expression);
		}
		return binding;
	}

	private String[] getExcludedVariableNames() {
		if (fExcludedVariableNames == null) {
			List<String> excludedNames = new ArrayList<>();
			try {
				IBinding[] bindings = new ScopeAnalyzer(fCompilationUnitNode).getDeclarationsInScope(getSelectedExpression().getStartPosition(), ScopeAnalyzer.VARIABLES | ScopeAnalyzer.CHECK_VISIBILITY);
				for (int i = 0; i < bindings.length; i++) {
					excludedNames.add(bindings[i].getName());
				}
			} catch (JavaModelException e) {
				// do nothing.
			}

			fExcludedVariableNames = excludedNames.toArray(new String[0]);
		}
		return fExcludedVariableNames;
	}

	private String[] getExcludedFieldNames() {
		if (fExcludedFieldNames == null) {
			List<String> result = new ArrayList<>();
			try {
				final ASTNode type = getEnclosingTypeDeclaration();
				if (type instanceof TypeDeclaration) {
					FieldDeclaration[] fields = ((TypeDeclaration) type).getFields();
					for (int i = 0; i < fields.length; i++) {
						for (Iterator<VariableDeclarationFragment> iter = fields[i].fragments().iterator(); iter.hasNext();) {
							VariableDeclarationFragment field = iter.next();
							result.add(field.getName().getIdentifier());
						}
					}
				}
			} catch (JavaModelException e) {
				// do nothing.
			}

			fExcludedFieldNames = result.toArray(new String[result.size()]);
		}

		return fExcludedFieldNames;
	}

	public void setFieldName(String guessFieldName) {
		fFieldName = guessFieldName;
	}

	private boolean isStandaloneExpression() throws JavaModelException {
		IExpressionFragment selectedFragment = getSelectedExpression();
		ASTNode target = selectedFragment.getAssociatedNode();
		ASTNode parent = target.getParent();
		return (parent instanceof ExpressionStatement || parent instanceof LambdaExpression) && selectedFragment.matches(ASTFragmentFactory.createFragmentForFullSubtree(target));
	}

	private void addInitializerToMethod() throws CoreException {
		Statement vds = createNewAssignmentStatement();
		IExpressionFragment selectedFragment = getSelectedExpression();
		Expression selectedExpression = selectedFragment.getAssociatedExpression();
		ASTNode target = selectedFragment.getAssociatedNode();
		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		AST ast = fCURewrite.getAST();
		TextEditGroup groupDescription = fCURewrite.createGroupDescription(RefactoringCoreMessages.ExtractFieldRefactoring_initialize_field);
		ASTNode parent = target.getParent();
		StructuralPropertyDescriptor locationInParent = target.getLocationInParent();
		if (isStandaloneExpression()) {
			ASTNode replacement;
			if (parent instanceof LambdaExpression) {
				Block blockBody = ast.newBlock();
				blockBody.statements().add(vds);
				if (!Bindings.isVoidType(((LambdaExpression) parent).resolveMethodBinding().getReturnType())) {
					ReturnStatement returnStatement = ast.newReturnStatement();
					returnStatement.setExpression(ast.newSimpleName(fFieldName));
					blockBody.statements().add(returnStatement);
				}
				replacement = blockBody;
			} else if (ASTNodes.isControlStatementBody(parent.getLocationInParent())) {
				Block block = ast.newBlock();
				block.statements().add(vds);
				replacement = block;
			} else {
				replacement = vds;
			}
			ASTNode replacee = parent instanceof LambdaExpression || !ASTNodes.hasSemicolon((ExpressionStatement) parent, fCu) ? selectedExpression : parent;
			rewrite.replace(replacee, replacement, groupDescription);
			return;
		}

		while (locationInParent != Block.STATEMENTS_PROPERTY && locationInParent != SwitchStatement.STATEMENTS_PROPERTY) {
			if (ASTNodes.isControlStatementBody(locationInParent)) {
				// create intermediate block if target was the body property of a control statement:
				Block replacement = rewrite.getAST().newBlock();
				ListRewrite replacementRewrite = rewrite.getListRewrite(replacement, Block.STATEMENTS_PROPERTY);
				replacementRewrite.insertFirst(vds, null);
				replacementRewrite.insertLast(rewrite.createMoveTarget(target), null);
				rewrite.replace(target, replacement, groupDescription);
				return;
			} else if (locationInParent == LambdaExpression.BODY_PROPERTY && ((LambdaExpression) parent).getBody() instanceof Expression) {
				Block replacement = rewrite.getAST().newBlock();
				ListRewrite replacementRewrite = rewrite.getListRewrite(replacement, Block.STATEMENTS_PROPERTY);
				replacementRewrite.insertFirst(vds, null);
				ASTNode moveTarget = rewrite.createMoveTarget(target);
				if (Bindings.isVoidType(((LambdaExpression) parent).resolveMethodBinding().getReturnType())) {
					ExpressionStatement expressionStatement = ast.newExpressionStatement((Expression) moveTarget);
					moveTarget = expressionStatement;
				} else {
					ReturnStatement returnStatement = ast.newReturnStatement();
					returnStatement.setExpression((Expression) moveTarget);
					moveTarget = returnStatement;
				}
				replacementRewrite.insertLast(moveTarget, null);
				rewrite.replace(target, replacement, groupDescription);
				return;
			}
			target = parent;
			parent = parent.getParent();
			locationInParent = target.getLocationInParent();
		}
		ListRewrite listRewrite = rewrite.getListRewrite(parent, (ChildListPropertyDescriptor) locationInParent);
		listRewrite.insertBefore(vds, target, groupDescription);
	}

	private void addInitializersToConstructors(ASTRewrite rewrite) throws CoreException {
		Assert.isTrue(!isDeclaredInAnonymousClass());
		final AbstractTypeDeclaration declaration = (AbstractTypeDeclaration) getMethodDeclaration().getParent();
		final MethodDeclaration[] constructors = getAllConstructors(declaration);
		if (constructors.length == 0) {
			AST ast = rewrite.getAST();
			MethodDeclaration newConstructor = ast.newMethodDeclaration();
			newConstructor.setConstructor(true);
			newConstructor.modifiers().addAll(ast.newModifiers(declaration.getModifiers() & ModifierRewrite.VISIBILITY_MODIFIERS));
			newConstructor.setName(ast.newSimpleName(declaration.getName().getIdentifier()));
			newConstructor.setBody(ast.newBlock());

			addFieldInitializationToConstructor(rewrite, newConstructor);

			int insertionIndex = computeInsertIndexForNewConstructor(declaration);
			rewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty()).insertAt(newConstructor, insertionIndex, null);
		} else {
			for (int index = 0; index < constructors.length; index++) {
				if (shouldInsertTempInitialization(constructors[index])) {
					addFieldInitializationToConstructor(rewrite, constructors[index]);
				}
			}
		}
	}

	private static MethodDeclaration[] getAllConstructors(AbstractTypeDeclaration typeDeclaration) {
		if (typeDeclaration instanceof TypeDeclaration) {
			MethodDeclaration[] allMethods = ((TypeDeclaration) typeDeclaration).getMethods();
			List<MethodDeclaration> result = new ArrayList<>(Math.min(allMethods.length, 1));
			for (int i = 0; i < allMethods.length; i++) {
				MethodDeclaration declaration = allMethods[i];
				if (declaration.isConstructor()) {
					result.add(declaration);
				}
			}
			return result.toArray(new MethodDeclaration[result.size()]);
		}
		return new MethodDeclaration[] {};
	}

	private void addFieldInitializationToConstructor(ASTRewrite rewrite, MethodDeclaration constructor) throws JavaModelException {
		if (constructor.getBody() == null) {
			constructor.setBody(fCURewrite.getAST().newBlock());
		}

		Statement newStatement = createNewAssignmentStatement();
		rewrite.getListRewrite(constructor.getBody(), Block.STATEMENTS_PROPERTY).insertLast(newStatement, null);
	}

	private int computeInsertIndexForNewConstructor(AbstractTypeDeclaration declaration) {
		List<BodyDeclaration> declarations = declaration.bodyDeclarations();
		if (declarations.isEmpty()) {
			return 0;
		}
		int index = findFirstMethodIndex(declaration);
		if (index == -1) {
			return declarations.size();
		} else {
			return index;
		}
	}

	private int findFirstMethodIndex(AbstractTypeDeclaration typeDeclaration) {
		for (int i = 0, n = typeDeclaration.bodyDeclarations().size(); i < n; i++) {
			if (typeDeclaration.bodyDeclarations().get(i) instanceof MethodDeclaration) {
				return i;
			}
		}
		return -1;
	}

	private static boolean shouldInsertTempInitialization(MethodDeclaration constructor) {
		Assert.isTrue(constructor.isConstructor());
		if (constructor.getBody() == null) {
			return false;
		}
		List<Statement> statements = constructor.getBody().statements();
		if (statements == null) {
			return false;
		}
		if (statements.size() > 0 && statements.get(0) instanceof ConstructorInvocation) {
			return false;
		}
		return true;
	}

	private void addFieldDeclaration() throws CoreException {
		FieldDeclaration[] fields = getFieldDeclarations();
		ASTNode parent = getEnclosingTypeDeclaration();
		ChildListPropertyDescriptor descriptor = ASTNodes.getBodyDeclarationsProperty(parent);
		int insertIndex;
		if (fields.length == 0) {
			insertIndex = 0;
		} else {
			insertIndex = ASTNodes.getBodyDeclarations(parent).indexOf(fields[fields.length - 1]) + 1;
		}

		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		final FieldDeclaration declaration = createNewFieldDeclaration(rewrite);
		rewrite.getListRewrite(parent, descriptor).insertAt(declaration, insertIndex, null);
	}

	private FieldDeclaration createNewFieldDeclaration(ASTRewrite rewrite) throws CoreException {
		AST ast = fCURewrite.getAST();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		SimpleName variableName = ast.newSimpleName(fFieldName);
		fragment.setName(variableName);
		if (fLinkedProposalModel != null) {
			fLinkedProposalModel.getPositionGroup(KEY_NAME, true).addPosition(rewrite.track(variableName), false);
		}

		if (fInitializeIn == INITIALIZE_IN_FIELD) {
			Expression initializer = getSelectedExpression().createCopyTarget(fCURewrite.getASTRewrite(), true);
			fragment.setInitializer(initializer);
		}
		FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(createFieldType());
		fieldDeclaration.modifiers().addAll(ASTNodeFactory.newModifiers(ast, getModifiers()));
		return fieldDeclaration;
	}

	private FieldDeclaration[] getFieldDeclarations() throws JavaModelException {
		List<BodyDeclaration> bodyDeclarations = ASTNodes.getBodyDeclarations(getEnclosingTypeDeclaration());
		List<FieldDeclaration> fields = new ArrayList<>(1);
		for (Iterator<BodyDeclaration> iter = bodyDeclarations.iterator(); iter.hasNext();) {
			Object each = iter.next();
			if (each instanceof FieldDeclaration) {
				fields.add((FieldDeclaration) each);
			}
		}
		return fields.toArray(new FieldDeclaration[fields.size()]);
	}

	private MethodDeclaration getMethodDeclaration() throws JavaModelException {
		return ASTNodes.getParent(getSelectedExpression().getAssociatedNode(), MethodDeclaration.class);
	}

	private ASTNode getEnclosingTypeDeclaration() throws JavaModelException {
		if (isDeclaredInLambdaExpression()) {
			return ASTNodes.getParent(getSelectedExpression().getAssociatedNode(), AbstractTypeDeclaration.class);
		}
		return getMethodDeclaration().getParent();
	}

	private String getEnclosingTypeName() throws JavaModelException {
		ASTNode node = getEnclosingTypeDeclaration();
		ITypeBinding typeBinding = ASTNodes.getEnclosingType(node);
		return typeBinding == null ? "" : typeBinding.getName();
	}

	private boolean isDeclaredInLambdaExpression() throws JavaModelException {
		ASTNode node = getSelectedExpression().getAssociatedNode();
		while (node != null && !(node instanceof BodyDeclaration)) {
			node = node.getParent();
			if (node instanceof LambdaExpression) {
				return true;
			}
		}

		return false;
	}

	private boolean isDeclaredInAnonymousClass() throws JavaModelException {
		return null != ASTNodes.getParent(getSelectedExpression().getAssociatedNode(), AnonymousClassDeclaration.class);
	}

	private Statement createNewAssignmentStatement() throws JavaModelException {
		AST ast = fCURewrite.getAST();
		Assignment assignment = ast.newAssignment();
		SimpleName fieldName = ast.newSimpleName(fFieldName);
		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		if (fLinkedProposalModel != null) {
			fLinkedProposalModel.getPositionGroup(KEY_NAME, true).addPosition(rewrite.track(fieldName), true);
		}

		assignment.setLeftHandSide(wrapAsFieldAccessExpression(fieldName));
		assignment.setRightHandSide(getSelectedExpression().createCopyTarget(rewrite, true));
		return ast.newExpressionStatement(assignment);
	}

	private Expression wrapAsFieldAccessExpression(SimpleName fieldName) {
		AST ast = fCURewrite.getAST();
		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		List<String> variableNames = Arrays.asList(getExcludedVariableNames());
		if (variableNames.contains(fFieldName)) {
			int modifiers = getModifiers();
			if (Flags.isStatic(modifiers)) {
				try {
					String enclosingTypeName = getEnclosingTypeName();
					SimpleName typeName = ast.newSimpleName(enclosingTypeName);
					if (fLinkedProposalModel != null) {
						fLinkedProposalModel.getPositionGroup(KEY_NAME, true).addPosition(rewrite.track(typeName), false);
					}
					QualifiedName qualifiedName = ast.newQualifiedName(typeName, fieldName);
					return qualifiedName;
				} catch (JavaModelException e) {
					return wrapAsFieldAccess(fieldName, ast);
				}
			} else {
				return wrapAsFieldAccess(fieldName, ast);
			}
		}

		return fieldName;
	}

	private FieldAccess wrapAsFieldAccess(SimpleName fieldName, AST ast) {
		Name qualifierName = null;
		try {
			if (isDeclaredInLambdaExpression()) {
				String enclosingTypeName = getEnclosingTypeName();
				qualifierName = ast.newSimpleName(enclosingTypeName);
			}
		} catch (JavaModelException e) {
			// do nothing.
		}

		FieldAccess fieldAccess = ast.newFieldAccess();
		ThisExpression thisExpression = ast.newThisExpression();
		if (qualifierName != null) {
			thisExpression.setQualifier(qualifierName);
		}
		fieldAccess.setExpression(thisExpression);
		fieldAccess.setName(fieldName);
		return fieldAccess;
	}

	private Type createFieldType() throws CoreException {
		Expression expression = getSelectedExpression().getAssociatedExpression();

		Type resultingType = null;
		ITypeBinding typeBinding = expression.resolveTypeBinding();

		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		AST ast = rewrite.getAST();

		if (expression instanceof ClassInstanceCreation && (typeBinding == null || typeBinding.getTypeArguments().length == 0)) {
			resultingType = (Type) rewrite.createCopyTarget(((ClassInstanceCreation) expression).getType());
		} else if (expression instanceof CastExpression) {
			resultingType = (Type) rewrite.createCopyTarget(((CastExpression) expression).getType());
		} else {
			if (typeBinding == null) {
				typeBinding = ASTResolving.guessBindingForReference(expression);
			}
			if (typeBinding != null) {
				typeBinding = Bindings.normalizeForDeclarationUse(typeBinding, ast);
				ImportRewrite importRewrite = fCURewrite.getImportRewrite();
				ImportRewriteContext context = new ContextSensitiveImportRewriteContext(expression, importRewrite);
				resultingType = importRewrite.addImport(typeBinding, ast, context, TypeLocation.LOCAL_VARIABLE);
			} else {
				resultingType = ast.newSimpleType(ast.newSimpleName("Object")); //$NON-NLS-1$
			}
		}
		if (fLinkedProposalModel != null) {
			LinkedProposalPositionGroupCore typeGroup = fLinkedProposalModel.getPositionGroup(KEY_TYPE, true);
			typeGroup.addPosition(rewrite.track(resultingType), false);
			if (typeBinding != null) {
				ITypeBinding[] relaxingTypes = ASTResolving.getNarrowingTypes(ast, typeBinding);
				for (int i = 0; i < relaxingTypes.length; i++) {
					typeGroup.addProposal(relaxingTypes[i], fCURewrite.getCu(), relaxingTypes.length - i);
				}
			}
		}
		return resultingType;
	}

	private void addReplaceExpressionWithField() throws JavaModelException {
		ASTRewrite rewrite = fCURewrite.getASTRewrite();
		AST ast = fCURewrite.getAST();
		TextEditGroup groupDescription = fCURewrite.createGroupDescription(RefactoringCoreMessages.ExtractFieldRefactoring_initialize_field);

		IExpressionFragment selectedFragment = getSelectedExpression();
		Expression selectedExpression = selectedFragment.getAssociatedExpression();
		ASTNode target = getSelectedExpression().getAssociatedNode();
		ASTNode parent = target.getParent();
		if (isStandaloneExpression() && (fInitializeIn == INITIALIZE_IN_FIELD || fInitializeIn == INITIALIZE_IN_CONSTRUCTOR)) {
			ASTNode replacee = parent instanceof LambdaExpression || !ASTNodes.hasSemicolon((ExpressionStatement) parent, fCu) ? selectedExpression : parent;
			if (parent instanceof LambdaExpression || ASTNodes.isControlStatementBody(parent.getLocationInParent())) {
				SimpleName fieldName = ast.newSimpleName(fFieldName);
				ASTNode replacement = wrapAsFieldAccessExpression(fieldName);
				if (fLinkedProposalModel != null) {
					fLinkedProposalModel.getPositionGroup(KEY_NAME, true).addPosition(rewrite.track(replacement), false);
				}
				rewrite.replace(replacee, replacement, groupDescription);
			} else {
				rewrite.remove(replacee, groupDescription);
			}

			return;
		}

		IASTFragment[] fragmentsToReplace = retainOnlyReplacableMatches(getMatchingFragments());
		ASTNode replacer;
		HashSet<IASTFragment> seen = new HashSet<>();
		for (int i = 0; i < fragmentsToReplace.length; i++) {
			IASTFragment fragment = fragmentsToReplace[i];
			if (!seen.add(fragment)) {
				continue;
			}

			SimpleName fieldName = ast.newSimpleName(fFieldName);
			replacer = wrapAsFieldAccessExpression(fieldName);
			TextEditGroup description = fCURewrite.createGroupDescription(RefactoringCoreMessages.ExtractTempRefactoring_replace);

			fragment.replace(rewrite, replacer, description);
			if (fLinkedProposalModel != null) {
				fLinkedProposalModel.getPositionGroup(KEY_NAME, true).addPosition(rewrite.track(replacer), false);
			}
		}
	}

	private static IASTFragment[] retainOnlyReplacableMatches(IASTFragment[] allMatches) {
		List<IASTFragment> result = new ArrayList<>(allMatches.length);
		for (int i = 0; i < allMatches.length; i++) {
			if (canReplace(allMatches[i])) {
				result.add(allMatches[i]);
			}
		}
		return result.toArray(new IASTFragment[result.size()]);
	}

	private static boolean canReplace(IASTFragment fragment) {
		ASTNode node = fragment.getAssociatedNode();
		ASTNode parent = node.getParent();
		if (parent instanceof VariableDeclarationFragment) {
			VariableDeclarationFragment vdf = (VariableDeclarationFragment) parent;
			if (node.equals(vdf.getName())) {
				return false;
			}
		}
		if (isMethodParameter(node)) {
			return false;
		}
		if (isThrowableInCatchBlock(node)) {
			return false;
		}
		if (parent instanceof ExpressionStatement) {
			return false;
		}
		if (parent instanceof LambdaExpression) {
			return false;
		}
		if (isLeftValue(node)) {
			return false;
		}
		if (isReferringToLocalVariableFromFor((Expression) node)) {
			return false;
		}
		if (isUsedInForInitializerOrUpdater((Expression) node)) {
			return false;
		}
		if (parent instanceof SwitchCase) {
			return false;
		}
		return true;
	}

	private static boolean isMethodParameter(ASTNode node) {
		return (node instanceof SimpleName) && (node.getParent() instanceof SingleVariableDeclaration) && (node.getParent().getParent() instanceof MethodDeclaration);
	}

	private static boolean isThrowableInCatchBlock(ASTNode node) {
		return (node instanceof SimpleName) && (node.getParent() instanceof SingleVariableDeclaration) && (node.getParent().getParent() instanceof CatchClause);
	}

	private static boolean isLeftValue(ASTNode node) {
		ASTNode parent = node.getParent();
		if (parent instanceof Assignment) {
			Assignment assignment = (Assignment) parent;
			if (assignment.getLeftHandSide() == node) {
				return true;
			}
		}
		if (parent instanceof PostfixExpression) {
			return true;
		}
		if (parent instanceof PrefixExpression) {
			PrefixExpression.Operator op = ((PrefixExpression) parent).getOperator();
			if (op.equals(PrefixExpression.Operator.DECREMENT)) {
				return true;
			}
			if (op.equals(PrefixExpression.Operator.INCREMENT)) {
				return true;
			}
			return false;
		}
		return false;
	}

	private IASTFragment[] getMatchingFragments() throws JavaModelException {
			return new IASTFragment[] { getSelectedExpression() };
	}

	private RefactoringStatus checkMatchingFragments() throws JavaModelException {
		RefactoringStatus result = new RefactoringStatus();
		IASTFragment[] matchingFragments = getMatchingFragments();
		for (int i = 0; i < matchingFragments.length; i++) {
			ASTNode node = matchingFragments[i].getAssociatedNode();
			if (isLeftValue(node) && !isReferringToLocalVariableFromFor((Expression) node)) {
				String msg = RefactoringCoreMessages.ExtractTempRefactoring_assigned_to;
				result.addWarning(msg, JavaStatusContext.create(fCu, node));
			}
		}
		return result;
	}

	private int getModifiers() {
		int flags = fVisibility;
		if (isDeclaredInStaticMethod()) {
			flags |= Modifier.STATIC;
		}

		return flags;
	}

	private boolean isDeclaredInStaticMethod() {
		try {
			return Modifier.isStatic(getMethodDeclaration().getModifiers());
		} catch (JavaModelException e) {
			// do nothing
		}
		return false;
	}

	private static final class ForStatementChecker extends ASTVisitor {

		private final Collection<IVariableBinding> fForInitializerVariables;

		private boolean fReferringToForVariable = false;

		public ForStatementChecker(Collection<IVariableBinding> forInitializerVariables) {
			Assert.isNotNull(forInitializerVariables);
			fForInitializerVariables = forInitializerVariables;
		}

		public boolean isReferringToForVariable() {
			return fReferringToForVariable;
		}

		@Override
		public boolean visit(SimpleName node) {
			IBinding binding = node.resolveBinding();
			if (binding != null && fForInitializerVariables.contains(binding)) {
				fReferringToForVariable = true;
			}
			return false;
		}
	}

	private static class LocalTypeAndVariableUsageAnalyzer extends HierarchicalASTVisitor {
		private final List<IBinding> fLocalDefinitions = new ArrayList<>(0); // List of IBinding (Variable and Type)
		private final List<SimpleName> fLocalReferencesToEnclosing = new ArrayList<>(0); // List of ASTNodes
		private final List<ITypeBinding> fMethodTypeVariables;
		private boolean fClassTypeVariablesUsed = false;

		public LocalTypeAndVariableUsageAnalyzer(ITypeBinding[] methodTypeVariables) {
			fMethodTypeVariables = Arrays.asList(methodTypeVariables);
		}

		public List<SimpleName> getUsageOfEnclosingNodes() {
			return fLocalReferencesToEnclosing;
		}

		public boolean getClassTypeVariablesUsed() {
			return fClassTypeVariablesUsed;
		}

		@Override
		public boolean visit(SimpleName node) {
			ITypeBinding typeBinding = node.resolveTypeBinding();
			if (typeBinding != null && typeBinding.isLocal()) {
				if (node.isDeclaration()) {
					fLocalDefinitions.add(typeBinding);
				} else if (!fLocalDefinitions.contains(typeBinding)) {
					fLocalReferencesToEnclosing.add(node);
				}
			}
			if (typeBinding != null && typeBinding.isTypeVariable()) {
				if (node.isDeclaration()) {
					fLocalDefinitions.add(typeBinding);
				} else if (!fLocalDefinitions.contains(typeBinding)) {
					if (fMethodTypeVariables.contains(typeBinding)) {
						fLocalReferencesToEnclosing.add(node);
					} else {
						fClassTypeVariablesUsed = true;
					}
				}
			}
			IBinding binding = node.resolveBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE && !((IVariableBinding) binding).isField()) {
				if (node.isDeclaration()) {
					fLocalDefinitions.add(binding);
				} else if (!fLocalDefinitions.contains(binding)) {
					fLocalReferencesToEnclosing.add(node);
				}
			}
			return super.visit(node);
		}
	}
}
