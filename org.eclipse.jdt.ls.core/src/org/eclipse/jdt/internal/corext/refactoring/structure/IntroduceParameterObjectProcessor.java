/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.descriptors.IntroduceParameterObjectDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.IntroduceParameterObjectDescriptor.Parameter;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.participants.IRefactoringProcessorIds;

import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

public class IntroduceParameterObjectProcessor extends ChangeSignatureProcessor {

	private final class ParameterObjectCreator implements IDefaultValueAdvisor {
		@Override
		public Expression createDefaultExpression(List<Expression> invocationArguments, ParameterInfo addedInfo, List<ParameterInfo> parameterInfos, MethodDeclaration enclosingMethod, boolean isRecursive, CompilationUnitRewrite cuRewrite) {
			final AST ast= cuRewrite.getAST();
			final ASTRewrite rewrite= cuRewrite.getASTRewrite();
			if (isRecursive && canReuseParameterObject(invocationArguments, addedInfo, parameterInfos, enclosingMethod)) {
				return ast.newSimpleName(addedInfo.getNewName());
			}
			ClassInstanceCreation classCreation= ast.newClassInstanceCreation();

			int startPosition= enclosingMethod != null ? enclosingMethod.getStartPosition() : cuRewrite.getRoot().getStartPosition();
			ContextSensitiveImportRewriteContext context= fParameterObjectFactory.createParameterClassAwareContext(fCreateAsTopLevel, cuRewrite, startPosition);
			classCreation.setType(fParameterObjectFactory.createType(fCreateAsTopLevel, cuRewrite, startPosition));
			List<Expression> constructorArguments= classCreation.arguments();
			for (Iterator<ParameterInfo> iter= parameterInfos.iterator(); iter.hasNext();) {
				ParameterInfo pi= iter.next();
				if (isValidField(pi)) {
					if (pi.isOldVarargs()) {
						boolean isLastParameter= !iter.hasNext();
						constructorArguments.addAll(computeVarargs(invocationArguments, pi, isLastParameter, cuRewrite, context));
					} else {
						Expression exp= invocationArguments.get(pi.getOldIndex());
						importNodeTypes(exp, cuRewrite, context);
						constructorArguments.add(moveNode(exp, rewrite));
					}
				}
			}
			return classCreation;
		}

		@Override
		public Type createType(String newTypeName, int startPosition, CompilationUnitRewrite cuRewrite) {
			return fParameterObjectFactory.createType(fCreateAsTopLevel, cuRewrite, startPosition);
		}

		private boolean canReuseParameterObject(List<Expression> invocationArguments, ParameterInfo addedInfo, List<ParameterInfo> parameterInfos, MethodDeclaration enclosingMethod) {
			Assert.isNotNull(enclosingMethod);
			List<SingleVariableDeclaration> parameters= enclosingMethod.parameters();
			for (ParameterInfo pi : parameterInfos) {
				if (isValidField(pi)) {
					if (!pi.isInlined())
						return false;
					ASTNode node= invocationArguments.get(pi.getOldIndex());
					if (!isParameter(pi, node, parameters, addedInfo.getNewName())) {
						return false;
					}
				}
			}
			return true;
		}

		private List<Expression> computeVarargs(List<Expression> invocationArguments, ParameterInfo varArgPI, boolean isLastParameter, CompilationUnitRewrite cuRewrite, ContextSensitiveImportRewriteContext context) {
			boolean isEmptyVarArg= varArgPI.getOldIndex() >= invocationArguments.size();
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getAST();
			ASTNode lastNode= isEmptyVarArg ? null : invocationArguments.get(varArgPI.getOldIndex());
			List<Expression> constructorArguments= new ArrayList<>();
			if (lastNode instanceof ArrayCreation) {
				ArrayCreation creation= (ArrayCreation) lastNode;
				ITypeBinding arrayType= creation.resolveTypeBinding();
				if (arrayType != null && arrayType.isAssignmentCompatible(varArgPI.getNewTypeBinding())) {
					constructorArguments.add(moveNode(creation, rewrite));
					return constructorArguments;
				}
			}
			if (isLastParameter) {
				// copy all varargs
				for (int i= varArgPI.getOldIndex(); i < invocationArguments.size(); i++) {
					Expression node= invocationArguments.get(i);
					importNodeTypes(node, cuRewrite, context);
					constructorArguments.add(moveNode(node, rewrite));
				}
			} else { // new signature would be String...args, int
				if (lastNode instanceof NullLiteral) {
					NullLiteral nullLiteral= (NullLiteral) lastNode;
					constructorArguments.add(moveNode(nullLiteral, rewrite));
				} else {
					ArrayCreation creation= ast.newArrayCreation();
					creation.setType((ArrayType) importBinding(varArgPI.getNewTypeBinding(), cuRewrite, context));
					ArrayInitializer initializer= ast.newArrayInitializer();
					List<Expression> expressions= initializer.expressions();
					for (int i= varArgPI.getOldIndex(); i < invocationArguments.size(); i++) {
						Expression node= invocationArguments.get(i);
						importNodeTypes(node, cuRewrite, context);
						expressions.add(moveNode(node, rewrite));
					}
					if (expressions.isEmpty())
						creation.dimensions().add(ast.newNumberLiteral("0")); //$NON-NLS-1$
					else
						creation.setInitializer(initializer);
					constructorArguments.add(creation);
				}
			}
			return constructorArguments;
		}

		public Type importBinding(ITypeBinding newTypeBinding, CompilationUnitRewrite cuRewrite, ImportRewriteContext context) {
			Type type= cuRewrite.getImportRewrite().addImport(newTypeBinding, cuRewrite.getAST(), context);
			cuRewrite.getImportRemover().registerAddedImports(type);
			return type;
		}

		private void importNodeTypes(ASTNode node, final CompilationUnitRewrite cuRewrite, final ImportRewriteContext context) {
			ASTResolving.visitAllBindings(node, nodeBinding -> {
				importBinding(nodeBinding, cuRewrite, context);
				return false;
			});
		}
	}

	private boolean isParameter(ParameterInfo pi, ASTNode node, List<SingleVariableDeclaration> enclosingMethodParameters, String qualifier) {
		if (node instanceof Name) {
			Name name= (Name) node;
			IVariableBinding binding= ASTNodes.getVariableBinding(name);
			if (binding != null && binding.isParameter()) {
				return binding.getName().equals(getNameInScope(pi, enclosingMethodParameters));
			} else {
				if (node instanceof QualifiedName) {
					QualifiedName qn= (QualifiedName) node;
					return qn.getFullyQualifiedName().equals(JavaModelUtil.concatenateName(qualifier, getNameInScope(pi, enclosingMethodParameters)));
				}
			}
		}
		return false;
	}

	private final class RewriteParameterBody extends BodyUpdater {
		@Override
		public void updateBody(MethodDeclaration methodDeclaration, final CompilationUnitRewrite cuRewrite, RefactoringStatus result) throws CoreException {
			// ensure that the parameterObject is imported
			fParameterObjectFactory.createType(fCreateAsTopLevel, cuRewrite, methodDeclaration.getStartPosition());
			if (cuRewrite.getCu().equals(getCompilationUnit()) && !fParameterClassCreated) {
				createParameterClass(methodDeclaration, cuRewrite);
				fParameterClassCreated= true;
			}
			Block body= methodDeclaration.getBody();
			final List<SingleVariableDeclaration> parameters= methodDeclaration.parameters();
			if (body != null) { // abstract methods don't have bodies
				final ASTRewrite rewriter= cuRewrite.getASTRewrite();
				ListRewrite bodyStatements= rewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
				ImportRewriteContext context=new ContextSensitiveImportRewriteContext(body, cuRewrite.getImportRewrite());
				for (ParameterInfo pi : getParameterInfos()) {
					if (isValidField(pi)) {
						if (isReadOnly(pi, body, parameters, null)) {
							body.accept(new ASTVisitor(false) {

								@Override
								public boolean visit(SimpleName node) {
									updateSimpleName(rewriter, pi, node, parameters, cuRewrite.getCu().getJavaProject());
									return false;
								}

							});
							pi.setInlined(true);
						} else {
							ExpressionStatement initializer= fParameterObjectFactory.createInitializer(pi, getParameterName(), cuRewrite, context);
							bodyStatements.insertFirst(initializer, null);
						}
					}
				}
			}


		}

		private void updateSimpleName(ASTRewrite rewriter, ParameterInfo pi, SimpleName node, List<SingleVariableDeclaration> enclosingParameters, IJavaProject project) {
			AST ast= rewriter.getAST();
			IBinding binding= node.resolveBinding();
			Expression replacementNode= fParameterObjectFactory.createFieldReadAccess(pi, getParameterName(), ast, project, false, null);
			if (binding instanceof IVariableBinding) {
				IVariableBinding variable= (IVariableBinding) binding;
				if (variable.isParameter() && variable.getName().equals(getNameInScope(pi, enclosingParameters))) {
					rewriter.replace(node, replacementNode, null);
				}
			} else {
				ASTNode parent= node.getParent();
				if (!(parent instanceof QualifiedName)
						&& !(parent instanceof FieldAccess)
						&& !(parent instanceof SuperFieldAccess)) {
					if (node.getIdentifier().equals(getNameInScope(pi, enclosingParameters))) {
						rewriter.replace(node, replacementNode, null);
					}
				}
			}
		}

		private boolean isReadOnly(final ParameterInfo pi, Block block, final List<SingleVariableDeclaration> enclosingMethodParameters, final String qualifier) {
			class NotWrittenDetector extends ASTVisitor {
				boolean notWritten= true;

				@Override
				public boolean visit(SimpleName node) {
					if (isParameter(pi, node, enclosingMethodParameters, qualifier) && ASTResolving.isWriteAccess(node))
						notWritten= false;
					return false;
				}

				@Override
				public boolean visit(SuperFieldAccess node) {
					return false;
				}
			}
			NotWrittenDetector visitor= new NotWrittenDetector();
			block.accept(visitor);
			return visitor.notWritten;
		}

		@Override
		public boolean needsParameterUsedCheck() {
			return false;
		}

	}

	private static final String PARAMETER_CLASS_APPENDIX= "Parameter"; //$NON-NLS-1$

	private static final String DEFAULT_PARAMETER_OBJECT_NAME= "parameterObject"; //$NON-NLS-1$

	private MethodDeclaration fMethodDeclaration;

	private ParameterObjectFactory fParameterObjectFactory;

	private boolean fCreateAsTopLevel= true;

	private ParameterInfo fParameterObjectReference;

	private boolean fParameterClassCreated= false;

	private List<ResourceChange> fOtherChanges;

	public IntroduceParameterObjectProcessor(IntroduceParameterObjectDescriptor descriptor) throws JavaModelException {
		super(descriptor.getMethod());
		IMethod method= descriptor.getMethod();
		Assert.isNotNull(method);
		initializeFields(method);
		setBodyUpdater(new RewriteParameterBody());
		setDefaultValueAdvisor(new ParameterObjectCreator());
		configureRefactoring(descriptor, this);
	}

	private void configureRefactoring(final IntroduceParameterObjectDescriptor parameter, IntroduceParameterObjectProcessor ref) {
		ref.setCreateAsTopLevel(parameter.isTopLevel());
		ref.setCreateGetter(parameter.isGetters());
		ref.setCreateSetter(parameter.isSetters());
		ref.setDelegateUpdating(parameter.isDelegate());
		ref.setDeprecateDelegates(parameter.isDeprecateDelegate());
		if (parameter.getClassName() != null)
			ref.setClassName(parameter.getClassName());
		if (parameter.getPackageName() != null)
			ref.setPackage(parameter.getPackageName());
		if (parameter.getParameterName() != null)
			ref.setParameterName(parameter.getParameterName());
		List<ParameterInfo> pis= ref.getParameterInfos();
		Parameter[] parameters= parameter.getParameters();
		if (parameters == null)
			parameters= IntroduceParameterObjectDescriptor.createParameters(getMethod());
		Map<Integer, ParameterInfo> paramIndex= new HashMap<>();
		for (ParameterInfo pi : pis) {
			paramIndex.put(pi.getOldIndex(), pi);
		}
		paramIndex.put(ParameterInfo.INDEX_FOR_ADDED, fParameterObjectReference);
		pis.clear();
		for (Parameter param : parameters) {
			ParameterInfo pi= paramIndex.get(Integer.valueOf(param.getIndex()));
			pis.add(pi);
			if (param != IntroduceParameterObjectDescriptor.PARAMETER_OBJECT) {
				pi.setCreateField(param.isCreateField());
				if (pi.isCreateField()) {
					String fieldName= param.getFieldName();
					if (fieldName != null)
						pi.setNewName(fieldName);
				}
			}
		}
	}

	private void initializeFields(IMethod method) {
		fParameterObjectFactory= new ParameterObjectFactory();
		String methodName= method.getElementName();
		String className= String.valueOf(Character.toUpperCase(methodName.charAt(0)));
		if (methodName.length() > 1)
			className+= methodName.substring(1);
		className+= PARAMETER_CLASS_APPENDIX;

		fParameterObjectReference= ParameterInfo.createInfoForAddedParameter(className, DEFAULT_PARAMETER_OBJECT_NAME);
		fParameterObjectFactory.setClassName(className);

		IType declaringType= method.getDeclaringType();
		Assert.isNotNull(declaringType);
		fParameterObjectFactory.setPackage(declaringType.getPackageFragment().getElementName());

		updateReferenceType();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException, OperationCanceledException {
		RefactoringStatus status= new RefactoringStatus();
		IMethod method= getMethod();
		// TODO: Check for availability
		status.merge(Checks.checkTypeName(fParameterObjectFactory.getClassName(), method));
		status.merge(Checks.checkIdentifier(getParameterName(), method));
		if (status.hasFatalError())
			return status;
		status.merge(super.checkFinalConditions(pm, context));
		return status;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus status= new RefactoringStatus();
		status.merge(super.checkInitialConditions(pm));
		if (status.hasFatalError())
			return status;
		CompilationUnit astRoot= getBaseCuRewrite().getRoot();
		ISourceRange nameRange= getMethod().getNameRange();
		ASTNode selectedNode= NodeFinder.perform(astRoot, nameRange.getOffset(), nameRange.getLength());
		if (selectedNode == null) {
			return mappingErrorFound(status, selectedNode);
		}
		fMethodDeclaration= ASTNodes.getParent(selectedNode, MethodDeclaration.class);
		if (fMethodDeclaration == null) {
			return mappingErrorFound(status, selectedNode);
		}
		IMethodBinding resolveBinding= fMethodDeclaration.resolveBinding();
		if (resolveBinding == null) {
			if (!processCompilerError(status, selectedNode))
				status.addFatalError(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_error_cannot_resolve_type);
			return status;
		}

		ITypeBinding declaringClass= resolveBinding.getDeclaringClass();
		if (fParameterObjectFactory.getPackage() == null)
			fParameterObjectFactory.setPackage(declaringClass.getPackage().getName());
		if (fParameterObjectFactory.getEnclosingType() == null)
			fParameterObjectFactory.setEnclosingType(declaringClass.getQualifiedName());

		List<ParameterInfo> parameterInfos= super.getParameterInfos();
		for (ParameterInfo pi : parameterInfos) {
			if (!pi.isAdded()) {
				if (pi.getOldName().equals(pi.getNewName())) // may have been
					// set to
					// something
					// else after
					// creation
					pi.setNewName(getFieldName(pi));
			}
		}
		if (!parameterInfos.contains(fParameterObjectReference)) {
			parameterInfos.add(0, fParameterObjectReference);
		}
		Map<String, IVariableBinding> bindingMap= new HashMap<>();
		for (Iterator<SingleVariableDeclaration> iter= fMethodDeclaration.parameters().iterator(); iter.hasNext();) {
			SingleVariableDeclaration sdv= iter.next();
			bindingMap.put(sdv.getName().getIdentifier(), sdv.resolveBinding());
		}
		for (ParameterInfo pi : parameterInfos) {
			if (pi != fParameterObjectReference)
				pi.setOldBinding(bindingMap.get(pi.getOldName()));
		}
		fParameterObjectFactory.setVariables(parameterInfos);
		return status;
	}

	@Override
	protected boolean shouldReport(IProblem problem, CompilationUnit cu) {
		if (!super.shouldReport(problem, cu))
			return false;
		ASTNode node= ASTNodeSearchUtil.getAstNode(cu, problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart() + 1);
		if (node instanceof Type) {
			Type type= (Type) node;
			if (problem.getID() == IProblem.UndefinedType && getClassName().equals(ASTNodes.getTypeName(type))) {
				return false;
			}
		}
		if (node instanceof Name) {
			Name name= (Name) node;
			if (problem.getID() == IProblem.ImportNotFound && getPackage().indexOf(name.getFullyQualifiedName()) != -1)
				return false;
			if (problem.getID() == IProblem.MissingTypeInMethod) {
				StructuralPropertyDescriptor locationInParent= name.getLocationInParent();
				String[] arguments= problem.getArguments();
				if ((locationInParent == MethodInvocation.NAME_PROPERTY || locationInParent == SuperMethodInvocation.NAME_PROPERTY)
						&& arguments.length > 3
						&& arguments[3].endsWith(getClassName()))
					return false;
			}
		}
		return true;
	}

	public String getClassName() {
		return fParameterObjectFactory.getClassName();
	}

	public ITypeBinding getContainingClass() {
		return fMethodDeclaration.resolveBinding().getDeclaringClass();
	}

	private String getMappingErrorMessage() {
		return RefactoringCoreMessages.IntroduceParameterObjectRefactoring_cannotalanyzemethod_mappingerror;
	}

	public String getFieldName(ParameterInfo element) {
		IJavaProject javaProject= getCompilationUnit().getJavaProject();
		String stripped= NamingConventions.getBaseName(NamingConventions.VK_PARAMETER, element.getOldName(), javaProject);
		int dim= element.getNewTypeBinding() != null ? element.getNewTypeBinding().getDimensions() : 0;
		return StubUtility.getVariableNameSuggestions(NamingConventions.VK_INSTANCE_FIELD, javaProject, stripped, dim, null, true)[0];
	}

	@Override
	public Change[] getAllChanges() {
		ArrayList<Change> changes= new ArrayList<>(Arrays.asList(super.getAllChanges()));
		changes.addAll(fOtherChanges);
		return changes.toArray(new Change[changes.size()]);
	}

	@Override
	protected void clearManagers() {
		super.clearManagers();
		fOtherChanges= new ArrayList<>();
		fParameterClassCreated= false;
	}

	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.IntroduceParameterObjectRefactoring_refactoring_name;
	}

	@Override
	public String getIdentifier() {
		return IRefactoringProcessorIds.INTRODUCE_PARAMETER_OBJECT_PROCESSOR;
	}

	@Override
	public JavaRefactoringDescriptor createDescriptor() {
		IntroduceParameterObjectDescriptor ipod= RefactoringSignatureDescriptorFactory.createIntroduceParameterObjectDescriptor();
		ipod.setMethod(getMethod());
		ipod.setClassName(getClassName());
		ipod.setDelegate(getDelegateUpdating());
		ipod.setDeprecateDelegate(getDeprecateDelegates());
		ipod.setGetters(isCreateGetter());
		ipod.setSetters(isCreateSetter());
		ipod.setPackageName(getPackage());
		ipod.setParameterName(getParameterName());
		ipod.setTopLevel(isCreateAsTopLevel());

		ArrayList<Parameter> parameters= new ArrayList<>();
		List<ParameterInfo> pis= getParameterInfos();
		for (ParameterInfo pi : pis) {
			if (pi.isAdded()) {
				parameters.add(IntroduceParameterObjectDescriptor.PARAMETER_OBJECT);
			} else {
				IntroduceParameterObjectDescriptor.Parameter parameter= new IntroduceParameterObjectDescriptor.Parameter(pi.getOldIndex());
				if (pi.isCreateField()) {
					parameter.setCreateField(true);
					parameter.setFieldName(pi.getNewName());
				}
				parameters.add(parameter);
			}
		}
		ipod.setParameters(parameters.toArray(new Parameter[parameters.size()]));
		String project= getCompilationUnit().getJavaProject().getElementName();
		try {
			ipod.setComment(createComment(project).asString());
		} catch (JavaModelException e) {
			JavaManipulationPlugin.log(e);
		}
		ipod.setProject(project);
		ipod.setDescription(getProcessorName());
		ipod.setFlags(getDescriptorFlags());
		return ipod;
	}

	private JDTRefactoringDescriptorComment createComment(String project) throws JavaModelException {
		String header= Messages.format(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_description, getOldMethodSignature());
		JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
		comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_object_class, BasicElementLabels.getJavaElementName(fParameterObjectFactory.getClassName())));
		if (fCreateAsTopLevel) {
			comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_package, BasicElementLabels.getJavaElementName(fParameterObjectFactory.getPackage())));
		} else {
			comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_enclosing_type, BasicElementLabels.getJavaElementName(fParameterObjectFactory.getEnclosingType())));
		}
		List<String> kept= new ArrayList<>();
		List<String> fields= new ArrayList<>();
		for (ParameterInfo pi : getParameterInfos()) {
			if (pi.isCreateField()) {
				fields.add(pi.getNewName());
			} else {
				if (!pi.isAdded()) {
					kept.add(pi.getNewName());
				}
			}
		}

		comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_fields, fields.toArray(new String[0])));
		if (!kept.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_keep_parameter, kept.toArray(new String[0])));
		if (fParameterObjectFactory.isCreateGetter())
			comment.addSetting(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_create_getter);
		if (fParameterObjectFactory.isCreateSetter())
			comment.addSetting(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_descriptor_create_setter);
		return comment;
	}

	@Override
	protected String doGetRefactoringChangeName() {
		return getProcessorName();
	}

	public String getParameterName() {
		return fParameterObjectReference.getNewName();
	}

	public boolean isCreateGetter() {
		return fParameterObjectFactory.isCreateGetter();
	}

	public boolean isCreateSetter() {
		return fParameterObjectFactory.isCreateSetter();
	}

	public boolean isCreateAsTopLevel() {
		return fCreateAsTopLevel;
	}

	/**
	 * Checks if the given parameter info has been selected for field creation
	 *
	 * @param pi parameter info
	 * @return true if the given parameter info has been selected for field
	 *         creation
	 */
	private boolean isValidField(ParameterInfo pi) {
		return pi.isCreateField() & !pi.isAdded();
	}

	private RefactoringStatus mappingErrorFound(RefactoringStatus result, ASTNode node) {
		if (node != null && (node.getFlags() & ASTNode.MALFORMED) != 0 && processCompilerError(result, node))
			return result;
		result.addFatalError(getMappingErrorMessage());
		return result;
	}

	public void moveFieldDown(ParameterInfo selected) {
		fParameterObjectFactory.moveDown(selected);
	}

	public void moveFieldUp(ParameterInfo selected) {
		fParameterObjectFactory.moveUp(selected);
	}

	private boolean processCompilerError(RefactoringStatus result, ASTNode node) {
		Message[] messages= ASTNodes.getMessages(node, ASTNodes.INCLUDE_ALL_PARENTS);
		if (messages.length == 0)
			return false;
		result.addFatalError(Messages.format(RefactoringCoreMessages.IntroduceParameterObjectRefactoring_cannotanalysemethod_compilererror,
				new String[] { messages[0].getMessage() }));
		return true;
	}

	public void setClassName(String className) {
		fParameterObjectFactory.setClassName(className);
		updateReferenceType();
	}

	private void updateReferenceType() {
		if (fCreateAsTopLevel)
			fParameterObjectReference.setNewTypeName(JavaModelUtil.concatenateName(fParameterObjectFactory.getPackage(), fParameterObjectFactory
					.getClassName()));
		else
			fParameterObjectReference.setNewTypeName(JavaModelUtil.concatenateName(fParameterObjectFactory.getEnclosingType(),
					fParameterObjectFactory.getClassName()));
	}

	public void setCreateGetter(boolean createGetter) {
		fParameterObjectFactory.setCreateGetter(createGetter);
	}

	public void setCreateSetter(boolean createSetter) {
		fParameterObjectFactory.setCreateSetter(createSetter);
	}

	public void setPackageName(String packageName) {
		fParameterObjectFactory.setPackage(packageName);
		updateReferenceType();
	}

	public void setParameterName(String paramName) {
		this.fParameterObjectReference.setNewName(paramName);
	}

	public void setCreateAsTopLevel(boolean topLevel) {
		this.fCreateAsTopLevel= topLevel;
		updateReferenceType();
	}

	public void updateParameterPosition() {
		fParameterObjectFactory.updateParameterPosition(fParameterObjectReference);
	}

	private void createParameterClass(MethodDeclaration methodDeclaration, CompilationUnitRewrite cuRewrite) throws CoreException {
		if (fCreateAsTopLevel) {
			IPackageFragmentRoot root= (IPackageFragmentRoot) cuRewrite.getCu().getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			fOtherChanges.addAll(fParameterObjectFactory.createTopLevelParameterObject(root));
		} else {
			ASTRewrite rewriter= cuRewrite.getASTRewrite();
			TypeDeclaration enclosingType= (TypeDeclaration) methodDeclaration.getParent();
			ContextSensitiveImportRewriteContext context=new ContextSensitiveImportRewriteContext(enclosingType, cuRewrite.getImportRewrite());
			ListRewrite bodyRewrite= rewriter.getListRewrite(enclosingType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			String fqn= enclosingType.getName().getFullyQualifiedName();
			TypeDeclaration classDeclaration= fParameterObjectFactory.createClassDeclaration(fqn, cuRewrite, null, context);
			classDeclaration.modifiers().add(rewriter.getAST().newModifier(ModifierKeyword.PUBLIC_KEYWORD));
			classDeclaration.modifiers().add(rewriter.getAST().newModifier(ModifierKeyword.STATIC_KEYWORD));
			bodyRewrite.insertBefore(classDeclaration, methodDeclaration, null);
		}
	}

	public String getPackage() {
		return fParameterObjectFactory.getPackage();
	}

	public void setPackage(String typeQualifier) {
		fParameterObjectFactory.setPackage(typeQualifier);
	}

	private String getNameInScope(ParameterInfo pi, List<SingleVariableDeclaration> enclosingMethodParameters) {
		Assert.isNotNull(enclosingMethodParameters);
		boolean emptyVararg= pi.getOldIndex() >= enclosingMethodParameters.size();
		if (!emptyVararg) {
			SingleVariableDeclaration svd= enclosingMethodParameters.get(pi.getOldIndex());
			return svd.getName().getIdentifier();
		}
		return null;
	}

	public String getNewTypeName() {
		return fParameterObjectReference.getNewTypeName();
	}

	public ICompilationUnit getCompilationUnit() {
		return getBaseCuRewrite().getCu();
	}

	@Override
	protected int getDescriptorFlags() {
		return super.getDescriptorFlags() | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
	}

}
