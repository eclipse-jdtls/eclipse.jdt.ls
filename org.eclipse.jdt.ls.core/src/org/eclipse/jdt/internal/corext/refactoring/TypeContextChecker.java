/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
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

 package org.eclipse.jdt.internal.corext.refactoring;

 import java.util.ArrayList;
 import java.util.Collections;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;

 import org.eclipse.core.runtime.Assert;
 import org.eclipse.core.runtime.CoreException;
 import org.eclipse.core.runtime.IProgressMonitor;
 import org.eclipse.core.runtime.NullProgressMonitor;

 import org.eclipse.core.resources.IProject;

 import org.eclipse.ltk.core.refactoring.RefactoringStatus;

 import org.eclipse.jdt.core.Flags;
 import org.eclipse.jdt.core.ICompilationUnit;
 import org.eclipse.jdt.core.IJavaProject;
 import org.eclipse.jdt.core.IMethod;
 import org.eclipse.jdt.core.IPackageFragment;
 import org.eclipse.jdt.core.IPackageFragmentRoot;
 import org.eclipse.jdt.core.ISourceRange;
 import org.eclipse.jdt.core.IType;
 import org.eclipse.jdt.core.ITypeParameter;
 import org.eclipse.jdt.core.JavaCore;
 import org.eclipse.jdt.core.JavaModelException;
 import org.eclipse.jdt.core.WorkingCopyOwner;
 import org.eclipse.jdt.core.compiler.IProblem;
 import org.eclipse.jdt.core.dom.ASTNode;
 import org.eclipse.jdt.core.dom.ASTParser;
 import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
 import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
 import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
 import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
 import org.eclipse.jdt.core.dom.ArrayType;
 import org.eclipse.jdt.core.dom.Block;
 import org.eclipse.jdt.core.dom.BodyDeclaration;
 import org.eclipse.jdt.core.dom.ClassInstanceCreation;
 import org.eclipse.jdt.core.dom.CompilationUnit;
 import org.eclipse.jdt.core.dom.EnumDeclaration;
 import org.eclipse.jdt.core.dom.IExtendedModifier;
 import org.eclipse.jdt.core.dom.ITypeBinding;
 import org.eclipse.jdt.core.dom.ImportDeclaration;
 import org.eclipse.jdt.core.dom.MethodDeclaration;
 import org.eclipse.jdt.core.dom.Modifier;
 import org.eclipse.jdt.core.dom.Name;
 import org.eclipse.jdt.core.dom.NameQualifiedType;
 import org.eclipse.jdt.core.dom.NodeFinder;
 import org.eclipse.jdt.core.dom.PackageDeclaration;
 import org.eclipse.jdt.core.dom.PrimitiveType;
 import org.eclipse.jdt.core.dom.QualifiedName;
 import org.eclipse.jdt.core.dom.QualifiedType;
 import org.eclipse.jdt.core.dom.RecordDeclaration;
 import org.eclipse.jdt.core.dom.SimpleName;
 import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
 import org.eclipse.jdt.core.dom.Type;
 import org.eclipse.jdt.core.dom.TypeDeclaration;
 import org.eclipse.jdt.core.dom.TypeParameter;
 import org.eclipse.jdt.core.manipulation.TypeNameMatchCollector;
 import org.eclipse.jdt.core.search.IJavaSearchConstants;
 import org.eclipse.jdt.core.search.IJavaSearchScope;
 import org.eclipse.jdt.core.search.SearchEngine;
 import org.eclipse.jdt.core.search.SearchPattern;
 import org.eclipse.jdt.core.search.TypeNameMatch;

 import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
 import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
 import org.eclipse.jdt.internal.corext.dom.ASTFlattener;
 import org.eclipse.jdt.internal.corext.dom.ASTNodes;
 import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
 import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
 import org.eclipse.jdt.internal.corext.dom.Selection;
 import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
 import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
 import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
 import org.eclipse.jdt.internal.corext.util.Messages;

 import org.eclipse.jdt.internal.ui.refactoring.contentassist.JavaTypeCompletionProcessorCore;

 public class TypeContextChecker {
	 private static class MethodTypesChecker {

		 private static final String METHOD_NAME= "__$$__"; //$NON-NLS-1$

		 private final IMethod fMethod;
		 private final StubTypeContext fStubTypeContext;
		 private final List<ParameterInfo> fParameterInfos;
		 private final ReturnTypeInfo fReturnTypeInfo;

		 public MethodTypesChecker(IMethod method, StubTypeContext stubTypeContext, List<ParameterInfo> parameterInfos, ReturnTypeInfo returnTypeInfo) {
			 fMethod= method;
			 fStubTypeContext= stubTypeContext;
			 fParameterInfos= parameterInfos;
			 fReturnTypeInfo= returnTypeInfo;
		 }

		 public RefactoringStatus[] checkAndResolveMethodTypes() throws CoreException {
			 RefactoringStatus[] results= new MethodTypesSyntaxChecker(fMethod, fParameterInfos, fReturnTypeInfo).checkSyntax();
			 for (RefactoringStatus result : results) {
				 if (result != null && result.hasFatalError()) {
					 return results;
				 }
			 }

			 int parameterCount= fParameterInfos.size();
			 String[] types= new String[parameterCount + 1];
			 for (int i= 0; i < parameterCount; i++)
				 types[i]= ParameterInfo.stripEllipsis((fParameterInfos.get(i)).getNewTypeName());
			 types[parameterCount]= fReturnTypeInfo.getNewTypeName();
			 RefactoringStatus[] semanticsResults= new RefactoringStatus[parameterCount + 1];
			 ITypeBinding[] typeBindings= resolveBindings(types, semanticsResults, true);

			 boolean needsSecondPass= false;
			 for (int i= 0; i < types.length; i++)
				 if (typeBindings[i] == null || ! semanticsResults[i].isOK())
					 needsSecondPass= true;

			 RefactoringStatus[] semanticsResults2= new RefactoringStatus[parameterCount + 1];
			 if (needsSecondPass)
				 typeBindings= resolveBindings(types, semanticsResults2, false);

			 for (int i= 0; i < fParameterInfos.size(); i++) {
				 ParameterInfo parameterInfo= fParameterInfos.get(i);
				 if (!parameterInfo.isResolve())
					 continue;
				 if (parameterInfo.getOldTypeBinding() != null && ! parameterInfo.isTypeNameChanged()) {
					 parameterInfo.setNewTypeBinding(parameterInfo.getOldTypeBinding());
				 } else {
					 parameterInfo.setNewTypeBinding(typeBindings[i]);
					 if (typeBindings[i] == null || (needsSecondPass && ! semanticsResults2[i].isOK())) {
						 if (results[i] == null)
							 results[i]= semanticsResults2[i];
						 else
							 results[i].merge(semanticsResults2[i]);
					 }
				 }
			 }
			 fReturnTypeInfo.setNewTypeBinding(typeBindings[fParameterInfos.size()]);
			 if (typeBindings[parameterCount] == null || (needsSecondPass && ! semanticsResults2[parameterCount].isOK())) {
				 if (results[parameterCount] == null)
					 results[parameterCount]= semanticsResults2[parameterCount];
				 else
					 results[parameterCount].merge(semanticsResults2[parameterCount]);
			 }

			 return results;
		 }

		 private ITypeBinding[] resolveBindings(String[] types, RefactoringStatus[] results, boolean firstPass) throws CoreException {
			 //TODO: split types into parameterTypes and returnType
			 int parameterCount= types.length - 1;
			 ITypeBinding[] typeBindings= new ITypeBinding[types.length];

			 StringBuilder cuString= new StringBuilder();
			 cuString.append(fStubTypeContext.getBeforeString());
			 int offsetBeforeMethodName= appendMethodDeclaration(cuString, types, parameterCount);
			 cuString.append(fStubTypeContext.getAfterString());

			 // need a working copy to tell the parser where to resolve (package visible) types
			 ICompilationUnit wc= fMethod.getCompilationUnit().getWorkingCopy(new WorkingCopyOwner() {/*subclass*/}, new NullProgressMonitor());
			 try {
				 wc.getBuffer().setContents(cuString.toString());
				 CompilationUnit compilationUnit= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(wc, true);
				 ASTNode method= NodeFinder.perform(compilationUnit, offsetBeforeMethodName, METHOD_NAME.length()).getParent();
				 Type[] typeNodes= new Type[types.length];
				 if (method instanceof MethodDeclaration) {
					 MethodDeclaration methodDeclaration= (MethodDeclaration) method;
					 typeNodes[parameterCount]= methodDeclaration.getReturnType2();
					 List<SingleVariableDeclaration> parameters= methodDeclaration.parameters();
					 for (int i= 0; i < parameterCount; i++)
						 typeNodes[i]= parameters.get(i).getType();

				 } else if (method instanceof AnnotationTypeMemberDeclaration) {
					 typeNodes[0]= ((AnnotationTypeMemberDeclaration) method).getType();
				 }

				 for (int i= 0; i < types.length; i++) {
					 Type type= typeNodes[i];
					 if (type == null) {
						 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_couldNotResolveType, BasicElementLabels.getJavaElementName(types[i]));
						 results[i]= RefactoringStatus.createErrorStatus(msg);
						 continue;
					 }
					 results[i]= new RefactoringStatus();
					 for (IProblem problem : ASTNodes.getProblems(type, ASTNodes.NODE_ONLY, ASTNodes.PROBLEMS)) {
						 if (isError(problem, type)) {
							 results[i].addError(problem.getMessage());
						 }
					 }
					 ITypeBinding binding= handleBug84585(type.resolveBinding());
					 if (firstPass && (binding == null || binding.isRecovered())) {
						 types[i]= qualifyTypes(type, results[i]);
					 }
					 typeBindings[i]= binding;
				 }
				 return typeBindings;
			 } finally {
				 wc.discardWorkingCopy();
			 }
		 }

		 /**
		  * Decides if a problem matters.
		  * @param problem the problem
		  * @param type the current type
		  * @return return if a problem matters.
		  */
		 private boolean isError(IProblem problem, Type type) {
			 return true;
		 }

		 private int appendMethodDeclaration(StringBuilder cuString, String[] types, int parameterCount) throws JavaModelException {
			 int flags= fMethod.getFlags();
			 if (Flags.isStatic(flags)) {
				 cuString.append("static "); //$NON-NLS-1$
			 } else if (Flags.isDefaultMethod(flags)) {
				 cuString.append("default "); //$NON-NLS-1$
			 }

			 ITypeParameter[] methodTypeParameters= fMethod.getTypeParameters();
			 if (methodTypeParameters.length != 0) {
				 cuString.append('<');
				 for (int i= 0; i < methodTypeParameters.length; i++) {
					 ITypeParameter typeParameter= methodTypeParameters[i];
					 if (i > 0)
						 cuString.append(',');
					 cuString.append(typeParameter.getElementName());
				 }
				 cuString.append("> "); //$NON-NLS-1$
			 }

			 cuString.append(types[parameterCount]).append(' ');
			 int offsetBeforeMethodName= cuString.length();
			 cuString.append(METHOD_NAME).append('(');
			 for (int i= 0; i < parameterCount; i++) {
				 if (i > 0)
					 cuString.append(',');
				 cuString.append(types[i]).append(" p").append(i); //$NON-NLS-1$
			 }
			 cuString.append(");"); //$NON-NLS-1$

			 return offsetBeforeMethodName;
		 }

		 private String qualifyTypes(Type type, final RefactoringStatus result) throws CoreException {
			 class NestedException extends RuntimeException {
				 private static final long serialVersionUID= 1L;
				 NestedException(CoreException e) {
					 super(e);
				 }
			 }
			 ASTFlattener flattener= new ASTFlattener() {
				 @Override
				 public boolean visit(SimpleName node) {
					 appendResolved(node.getIdentifier());
					 return false;
				 }
				 @Override
				 public boolean visit(QualifiedName node) {
					 appendResolved(node.getFullyQualifiedName());
					 return false;
				 }
				 @Override
				 public boolean visit(QualifiedType node) {
					 appendResolved(ASTNodes.getQualifiedTypeName(node));
					 return false;
				 }
				 @Override
				 public boolean visit(NameQualifiedType node) {
					 appendResolved(ASTNodes.getQualifiedTypeName(node));
					 return false;
				 }
				 private void appendResolved(String typeName) {
					 String resolvedType;
					 try {
						 resolvedType= resolveType(typeName, result, fMethod.getDeclaringType(), null);
					 } catch (CoreException e) {
						 throw new NestedException(e);
					 }
					 this.fBuffer.append(resolvedType);
				 }
			 };
			 try {
				 type.accept(flattener);
			 } catch (NestedException e) {
				 throw ((CoreException) e.getCause());
			 }
			 return flattener.getResult();
		 }

		 private static String resolveType(String elementTypeName, RefactoringStatus status, IType declaringType, IProgressMonitor pm) throws CoreException {
			 String[][] fqns= declaringType.resolveType(elementTypeName);
			 if (fqns != null) {
				 if (fqns.length == 1) {
					 return JavaModelUtil.concatenateName(fqns[0][0], fqns[0][1]);
				 } else if (fqns.length > 1){
					 String[] keys= { BasicElementLabels.getJavaElementName(elementTypeName), String.valueOf(fqns.length)};
					 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_ambiguous, keys);
					 status.addError(msg);
					 return elementTypeName;
				 }
			 }

			 List<TypeNameMatch> typeRefsFound= findTypeInfos(elementTypeName, declaringType, pm);
			 if (typeRefsFound.isEmpty()){
				 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_not_unique, BasicElementLabels.getJavaElementName(elementTypeName));
				 status.addError(msg);
				 return elementTypeName;
			 } else if (typeRefsFound.size() == 1){
				 TypeNameMatch typeInfo= typeRefsFound.get(0);
				 return typeInfo.getFullyQualifiedName();
			 } else {
				 Assert.isTrue(typeRefsFound.size() > 1);
				 String[] keys= {BasicElementLabels.getJavaElementName(elementTypeName), String.valueOf(typeRefsFound.size())};
				 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_ambiguous, keys);
				 status.addError(msg);
				 return elementTypeName;
			 }
		 }

		 private static List<TypeNameMatch> findTypeInfos(String typeName, IType contextType, IProgressMonitor pm) throws JavaModelException {
			 IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaProject[]{contextType.getJavaProject()}, true);
			 IPackageFragment currPackage= contextType.getPackageFragment();
			 ArrayList<TypeNameMatch> collectedInfos= new ArrayList<>();
			 TypeNameMatchCollector requestor= new TypeNameMatchCollector(collectedInfos);
			 int matchMode= SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE;
			 new SearchEngine().searchAllTypeNames(null, matchMode, typeName.toCharArray(), matchMode, IJavaSearchConstants.TYPE, scope, requestor, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, pm);

			 List<TypeNameMatch> result= new ArrayList<>();
			 for (TypeNameMatch curr : collectedInfos) {
				 IType type= curr.getType();
				 if (type != null) {
					 boolean visible=true;
					 try {
						 visible= JavaModelUtil.isVisible(type, currPackage);
					 } catch (JavaModelException e) {
						 //Assume visibile if not available
					 }
					 if (visible) {
						 result.add(curr);
					 }
				 }
			 }
			 return result;
		 }

	 }

	 private static class MethodTypesSyntaxChecker {

		 private final IMethod fMethod;
		 private final List<ParameterInfo> fParameterInfos;
		 private final ReturnTypeInfo fReturnTypeInfo;

		 public MethodTypesSyntaxChecker(IMethod method, List<ParameterInfo> parameterInfos, ReturnTypeInfo returnTypeInfo) {
			 fMethod= method;
			 fParameterInfos= parameterInfos;
			 fReturnTypeInfo= returnTypeInfo;
		 }

		 public RefactoringStatus[] checkSyntax() {
			 int parameterCount= fParameterInfos.size();
			 RefactoringStatus[] results= new RefactoringStatus[parameterCount + 1];
			 results[parameterCount]= checkReturnTypeSyntax();
			 for (int i= 0; i < parameterCount; i++) {
				 ParameterInfo info= fParameterInfos.get(i);
				 if (!info.isDeleted())
					 results[i]= checkParameterTypeSyntax(info);
			 }
			 return results;
		 }

		 private RefactoringStatus checkParameterTypeSyntax(ParameterInfo info) {
			 if (!info.isAdded() && !info.isTypeNameChanged() && !info.isDeleted())
				 return null;
			 return TypeContextChecker.checkParameterTypeSyntax(info.getNewTypeName(), fMethod.getJavaProject());
		 }

		 private RefactoringStatus checkReturnTypeSyntax() {
			 String newTypeName= fReturnTypeInfo.getNewTypeName();
			 if ("".equals(newTypeName.trim())) { //$NON-NLS-1$
				 String msg= RefactoringCoreMessages.TypeContextChecker_return_type_not_empty;
				 return RefactoringStatus.createFatalErrorStatus(msg);
			 }
			 List<String> problemsCollector= new ArrayList<>(0);
			 Type parsedType= parseType(newTypeName, fMethod.getJavaProject(), problemsCollector);
			 if (parsedType == null) {
				 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_invalid_return_type, BasicElementLabels.getJavaElementName(newTypeName));
				 return RefactoringStatus.createFatalErrorStatus(msg);
			 }
			 if (problemsCollector.isEmpty())
				 return null;

			 RefactoringStatus result= new RefactoringStatus();
			 for (String problem : problemsCollector) {
				 String[] keys= new String[]{ BasicElementLabels.getJavaElementName(newTypeName), BasicElementLabels.getJavaElementName(problem)};
				 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_invalid_return_type_syntax, keys);
				 result.addError(msg);
			 }
			 return result;
		 }

		 private static boolean isVoidArrayType(Type type){
			 if (! type.isArrayType())
				 return false;

			 ArrayType arrayType= (ArrayType)type;
			 if (! arrayType.getElementType().isPrimitiveType())
				 return false;
			 PrimitiveType primitiveType= (PrimitiveType) arrayType.getElementType();
			 return (primitiveType.getPrimitiveTypeCode() == PrimitiveType.VOID);
		 }

	 }

	 private static Type parseType(String typeString, IJavaProject javaProject, List<String> problemsCollector) {
		 if ("".equals(typeString.trim())) //speed up for a common case //$NON-NLS-1$
			 return null;
		 if (! typeString.trim().equals(typeString))
			 return null;

		 StringBuilder cuBuff= new StringBuilder();
		 cuBuff.append("interface A{"); //$NON-NLS-1$
		 int offset= cuBuff.length();
		 cuBuff.append(typeString).append(" m();}"); //$NON-NLS-1$

		 ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		 p.setSource(cuBuff.toString().toCharArray());
		 p.setProject(javaProject);
		 CompilationUnit cu= (CompilationUnit) p.createAST(null);
		 Selection selection= Selection.createFromStartLength(offset, typeString.length());
		 SelectionAnalyzer analyzer= new SelectionAnalyzer(selection, false);
		 cu.accept(analyzer);
		 ASTNode selected= analyzer.getFirstSelectedNode();
		 if (!(selected instanceof Type))
			 return null;
		 Type type= (Type)selected;
		 if (MethodTypesSyntaxChecker.isVoidArrayType(type))
			 return null;
		 for (IProblem problem : ASTNodes.getProblems(type, ASTNodes.NODE_ONLY, ASTNodes.PROBLEMS)) {
			 problemsCollector.add(problem.getMessage());
		 }
		 String typeNodeRange= cuBuff.substring(type.getStartPosition(), ASTNodes.getExclusiveEnd(type));
		 if (typeString.equals(typeNodeRange))
			 return type;
		 else
			 return null;
	 }

	 private static ITypeBinding handleBug84585(ITypeBinding typeBinding) {
		 if (typeBinding != null
				 && typeBinding.isGenericType()
				 && !typeBinding.isRawType()
				 && !typeBinding.isParameterizedType()) {
			 return null;
		 }

		 return typeBinding;
	 }

	 public static RefactoringStatus[] checkAndResolveMethodTypes(IMethod method, StubTypeContext stubTypeContext, List<ParameterInfo> parameterInfos, ReturnTypeInfo returnTypeInfo) throws CoreException {
		 MethodTypesChecker checker= new MethodTypesChecker(method, stubTypeContext, parameterInfos, returnTypeInfo);
		 return checker.checkAndResolveMethodTypes();
	 }

	 public static RefactoringStatus[] checkMethodTypesSyntax(IMethod method, List<ParameterInfo> parameterInfos, ReturnTypeInfo returnTypeInfo) {
		 MethodTypesSyntaxChecker checker= new MethodTypesSyntaxChecker(method, parameterInfos, returnTypeInfo);
		 return checker.checkSyntax();
	 }

	 public static RefactoringStatus checkParameterTypeSyntax(String type, IJavaProject project) {
		 String newTypeName= ParameterInfo.stripEllipsis(type.trim()).trim();
		 String typeLabel= BasicElementLabels.getJavaElementName(type);

		 if ("".equals(newTypeName.trim())){ //$NON-NLS-1$
			 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_parameter_type, typeLabel);
			 return RefactoringStatus.createFatalErrorStatus(msg);
		 }

		 if (ParameterInfo.isVarargs(type) && ! JavaModelUtil.is50OrHigher(project)) {
			 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_no_vararg_below_50, typeLabel);
			 return RefactoringStatus.createFatalErrorStatus(msg);
		 }

		 List<String> problemsCollector= new ArrayList<>(0);
		 Type parsedType= parseType(newTypeName, project, problemsCollector);
		 boolean valid= parsedType != null;
		 if (valid && parsedType instanceof PrimitiveType)
			 valid= ! PrimitiveType.VOID.equals(((PrimitiveType) parsedType).getPrimitiveTypeCode());
		 if (! valid) {
			 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_invalid_type_name, BasicElementLabels.getJavaElementName(newTypeName));
			 return RefactoringStatus.createFatalErrorStatus(msg);
		 }
		 if (problemsCollector.isEmpty())
			 return null;

		 RefactoringStatus result= new RefactoringStatus();
		 for (String problem : problemsCollector) {
			 String msg= Messages.format(RefactoringCoreMessages.TypeContextChecker_invalid_type_syntax,
					 new String[]{BasicElementLabels.getJavaElementName(newTypeName), BasicElementLabels.getJavaElementName(problem)});
			 result.addError(msg);
		 }
		 return result;
	 }

	 public static StubTypeContext createStubTypeContext(ICompilationUnit cu, CompilationUnit root, int focalPosition) throws CoreException {
		 StringBuilder bufBefore= new StringBuilder();
		 StringBuilder bufAfter= new StringBuilder();

		 int introEnd= 0;
		 PackageDeclaration pack= root.getPackage();
		 if (pack != null)
			 introEnd= pack.getStartPosition() + pack.getLength();
		 List<ImportDeclaration> imports= root.imports();
		 if (imports.size() > 0) {
			 ImportDeclaration lastImport= imports.get(imports.size() - 1);
			 introEnd= lastImport.getStartPosition() + lastImport.getLength();
		 }
		 bufBefore.append(cu.getBuffer().getText(0, introEnd));

		 fillWithTypeStubs(bufBefore, bufAfter, focalPosition, root.types());
		 bufBefore.append(' ');
		 bufAfter.insert(0, ' ');
		 return new StubTypeContext(cu, bufBefore.toString(), bufAfter.toString());
	 }

	 private static void fillWithTypeStubs(final StringBuilder bufBefore, final StringBuilder bufAfter, final int focalPosition, List<? extends BodyDeclaration> types) {
		 StringBuilder buf;
		 for (BodyDeclaration bodyDeclaration : types) {
			 if (! (bodyDeclaration instanceof AbstractTypeDeclaration)) {
				 //account for local classes:
				 if (! (bodyDeclaration instanceof MethodDeclaration))
					 continue;
				 int bodyStart= bodyDeclaration.getStartPosition();
				 int bodyEnd= bodyDeclaration.getStartPosition() + bodyDeclaration.getLength();
				 if ((bodyStart >= focalPosition) || (focalPosition >= bodyEnd))
					 continue;
				 MethodDeclaration methodDeclaration= (MethodDeclaration) bodyDeclaration;
				 buf= bufBefore;
				 appendModifiers(buf, methodDeclaration.modifiers());
				 appendTypeParameters(buf, methodDeclaration.typeParameters());
				 buf.append(" void "); //$NON-NLS-1$
				 buf.append(methodDeclaration.getName().getIdentifier());
				 buf.append("(){\n"); //$NON-NLS-1$
				 Block body= methodDeclaration.getBody();
				 body.accept(new HierarchicalASTVisitor() {
					 @Override
					 public boolean visit(AbstractTypeDeclaration node) {
						 fillWithTypeStubs(bufBefore, bufAfter, focalPosition, Collections.singletonList(node));
						 return false;
					 }
					 @Override
					 public boolean visit(ClassInstanceCreation node) {
						 AnonymousClassDeclaration anonDecl= node.getAnonymousClassDeclaration();
						 if (anonDecl == null)
							 return true; // could be in CIC parameter list
						 int anonStart= anonDecl.getStartPosition();
						 int anonEnd= anonDecl.getStartPosition() + anonDecl.getLength();
						 if ((anonStart >= focalPosition) || (focalPosition >= anonEnd))
							 return false;
						 bufBefore.append(" new "); //$NON-NLS-1$
						 bufBefore.append(node.getType().toString());
						 bufBefore.append("(){\n"); //$NON-NLS-1$
						 fillWithTypeStubs(bufBefore, bufAfter, focalPosition, anonDecl.bodyDeclarations());
						 bufAfter.append("};\n"); //$NON-NLS-1$
						 return false;
					 }
				 });
				 buf= bufAfter;
				 buf.append("}\n"); //$NON-NLS-1$
				 continue;
			 }

			 AbstractTypeDeclaration decl= (AbstractTypeDeclaration) bodyDeclaration;
			 buf= decl.getStartPosition() < focalPosition ? bufBefore : bufAfter;
			 appendModifiers(buf, decl.modifiers());

			 if (decl instanceof TypeDeclaration) {
				 TypeDeclaration type= (TypeDeclaration) decl;
				 buf.append(type.isInterface() ? "interface " : "class "); //$NON-NLS-1$//$NON-NLS-2$
				 buf.append(type.getName().getIdentifier());
				 appendTypeParameters(buf, type.typeParameters());
				 if (type.getSuperclassType() != null) {
					 buf.append(" extends "); //$NON-NLS-1$
					 buf.append(ASTNodes.asString(type.getSuperclassType()));
				 }
				 List<Type> superInterfaces= type.superInterfaceTypes();
				 appendSuperInterfaces(buf, superInterfaces);

			 } else if (decl instanceof AnnotationTypeDeclaration) {
				 AnnotationTypeDeclaration annotation= (AnnotationTypeDeclaration) decl;
				 buf.append("@interface "); //$NON-NLS-1$
				 buf.append(annotation.getName().getIdentifier());

			 } else if (decl instanceof EnumDeclaration) {
				 EnumDeclaration enumDecl= (EnumDeclaration) decl;
				 buf.append("enum "); //$NON-NLS-1$
				 buf.append(enumDecl.getName().getIdentifier());
				 List<Type> superInterfaces= enumDecl.superInterfaceTypes();
				 appendSuperInterfaces(buf, superInterfaces);
			 } else if (decl instanceof RecordDeclaration) {
				 RecordDeclaration recordDecl= (RecordDeclaration) decl;
				 buf.append("record "); //$NON-NLS-1$
				 buf.append(recordDecl.getName().getIdentifier());
				 buf.append("("); //$NON-NLS-1$
				 appendRecordComponents(buf, recordDecl.recordComponents());
				 buf.append(")"); //$NON-NLS-1$
				 appendTypeParameters(buf, recordDecl.typeParameters());
				 List<Type> superInterfaces= recordDecl.superInterfaceTypes();
				 appendSuperInterfaces(buf, superInterfaces);
			 }

			 buf.append("{\n"); //$NON-NLS-1$
			 if (decl instanceof EnumDeclaration)
				 buf.append(";\n"); //$NON-NLS-1$
			 fillWithTypeStubs(bufBefore, bufAfter, focalPosition, decl.bodyDeclarations());
			 buf= decl.getStartPosition() + decl.getLength() < focalPosition ? bufBefore : bufAfter;
			 buf.append("}\n"); //$NON-NLS-1$
		 }
	 }

	 private static void appendRecordComponents(StringBuilder buf, List<SingleVariableDeclaration> recordComponents) {
		 int recCompsCount= recordComponents.size();
		 if (recCompsCount > 0) {
			 for (int i= 0; i < recCompsCount; i++) {
				 SingleVariableDeclaration recComp= recordComponents.get(i);
				 buf.append(ASTNodes.asString(recComp));
				 if (i < recCompsCount - 1)
					 buf.append(',');
			 }
		 }
	 }

	 private static void appendTypeParameters(StringBuilder buf, List<TypeParameter> typeParameters) {
		 int typeParametersCount= typeParameters.size();
		 if (typeParametersCount > 0) {
			 buf.append('<');
			 for (int i= 0; i < typeParametersCount; i++) {
				 TypeParameter typeParameter= typeParameters.get(i);
				 buf.append(ASTNodes.asString(typeParameter));
				 if (i < typeParametersCount - 1)
					 buf.append(',');
			 }
			 buf.append('>');
		 }
	 }

	 private static void appendModifiers(StringBuilder buf, List<IExtendedModifier> modifiers) {
		 for (IExtendedModifier extendedModifier : modifiers) {
			 if (extendedModifier.isModifier()) {
				 Modifier modifier= (Modifier) extendedModifier;
				 buf.append(modifier.getKeyword().toString()).append(' ');
			 }
		 }
	 }

	 private static void appendSuperInterfaces(StringBuilder buf, List<Type> superInterfaces) {
		 int superInterfaceCount= superInterfaces.size();
		 if (superInterfaceCount > 0) {
			 buf.append(" implements "); //$NON-NLS-1$
			 for (int i= 0; i < superInterfaceCount; i++) {
				 Type superInterface= superInterfaces.get(i);
				 buf.append(ASTNodes.asString(superInterface));
				 if (i < superInterfaceCount - 1)
					 buf.append(',');
			 }
		 }
	 }

	 public static StubTypeContext createSuperInterfaceStubTypeContext(String typeName, IType enclosingType, IPackageFragment packageFragment) {
		 return createSupertypeStubTypeContext(typeName, true, enclosingType, packageFragment);
	 }

	 public static StubTypeContext createSuperClassStubTypeContext(String typeName, IType enclosingType, IPackageFragment packageFragment) {
		 return createSupertypeStubTypeContext(typeName, false, enclosingType, packageFragment);
	 }

	 private static StubTypeContext createSupertypeStubTypeContext(String typeName, boolean isInterface, IType enclosingType, IPackageFragment packageFragment) {
		 StubTypeContext stubTypeContext;
		 String prolog= "class " + typeName + (isInterface ? " implements " : " extends "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		 String epilog= " {} "; //$NON-NLS-1$
		 if (enclosingType != null) {
			 try {
				 ICompilationUnit cu= enclosingType.getCompilationUnit();
				 ISourceRange typeSourceRange= enclosingType.getSourceRange();
				 int focalPosition= typeSourceRange.getOffset() + typeSourceRange.getLength() - 1; // before closing brace

				 ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				 parser.setSource(cu);
				 parser.setFocalPosition(focalPosition);
				 CompilationUnit compilationUnit= (CompilationUnit) parser.createAST(null);

				 stubTypeContext= createStubTypeContext(cu, compilationUnit, focalPosition);
				 stubTypeContext= new StubTypeContext(stubTypeContext.getCuHandle(),
						 stubTypeContext.getBeforeString() + prolog,
						 epilog + stubTypeContext.getAfterString());
			 } catch (CoreException e) {
				 JavaManipulationPlugin.log(e);
				 stubTypeContext= new StubTypeContext(null, null, null);
			 }

		 } else if (packageFragment != null) {
			 ICompilationUnit cu= packageFragment.getCompilationUnit(JavaTypeCompletionProcessorCore.DUMMY_CU_NAME);
			 stubTypeContext= new StubTypeContext(cu, "package " + packageFragment.getElementName() + ";" + prolog, epilog);  //$NON-NLS-1$//$NON-NLS-2$

		 } else {
			 stubTypeContext= new StubTypeContext(null, null, null);
		 }
		 return stubTypeContext;
	 }

	 public static StubTypeContext createAnnotationStubTypeContext(/*@NonNull*/ IProject project) {
		 try {
			 for (IPackageFragmentRoot root : JavaCore.create(project).getPackageFragmentRoots()) {
				 if (!root.isReadOnly()) {
					 IPackageFragment packageFragment= root.getPackageFragment(""); //$NON-NLS-1$
					 String prolog= "abstract class __X__ {\n\tabstract @"; //$NON-NLS-1$
					 String epilog= " __X__ dummy();\n} "; //$NON-NLS-1$
					 ICompilationUnit cu= packageFragment.getCompilationUnit(JavaTypeCompletionProcessorCore.DUMMY_CU_NAME);
					 return new StubTypeContext(cu, prolog, epilog);
				 }
			 }
		 } catch (JavaModelException e) {
			 // fall through
		 }
		 return new StubTypeContext(null, null, null);
	 }

	 public static Type parseSuperClass(String superClass) {
		 return parseSuperType(superClass, false);
	 }

	 public static Type parseSuperInterface(String superInterface) {
		 return parseSuperType(superInterface, true);
	 }

	 private static Type parseSuperType(String superType, boolean isInterface) {
		 if (! superType.trim().equals(superType)) {
			 return null;
		 }

		 StringBuilder cuBuff= new StringBuilder();
		 if (isInterface)
			 cuBuff.append("class __X__ implements "); //$NON-NLS-1$
		 else
			 cuBuff.append("class __X__ extends "); //$NON-NLS-1$
		 int offset= cuBuff.length();
		 cuBuff.append(superType).append(" {}"); //$NON-NLS-1$

		 ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		 p.setSource(cuBuff.toString().toCharArray());
		 Map<String, String> options= new HashMap<>();
		 JavaModelUtil.setComplianceOptions(options, JavaModelUtil.VERSION_LATEST);
		 p.setCompilerOptions(options);
		 CompilationUnit cu= (CompilationUnit) p.createAST(null);
		 ASTNode selected= NodeFinder.perform(cu, offset, superType.length());
		 if (selected instanceof Name)
			 selected= selected.getParent();
		 if (selected.getStartPosition() != offset
				 || selected.getLength() != superType.length()
				 || ! (selected instanceof Type)
				 || selected instanceof PrimitiveType) {
			 return null;
		 }
		 Type type= (Type) selected;

		 String typeNodeRange= cuBuff.substring(type.getStartPosition(), ASTNodes.getExclusiveEnd(type));
		 if (! superType.equals(typeNodeRange)){
			 return null;
		 }
		 return type;
	 }

	 public static ITypeBinding resolveSuperClass(String superclass, IType typeHandle, StubTypeContext superClassContext) {
		 StringBuilder cuString= new StringBuilder();
		 cuString.append(superClassContext.getBeforeString());
		 cuString.append(superclass);
		 cuString.append(superClassContext.getAfterString());

		 try {
			 ICompilationUnit wc= typeHandle.getCompilationUnit().getWorkingCopy(new WorkingCopyOwner() {/*subclass*/}, new NullProgressMonitor());
			 try {
				 wc.getBuffer().setContents(cuString.toString());
				 CompilationUnit compilationUnit= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(wc, true);
				 ASTNode type= NodeFinder.perform(compilationUnit, superClassContext.getBeforeString().length(), superclass.length());
				 if (type instanceof Type) {
					 return handleBug84585(((Type) type).resolveBinding());
				 } else if (type instanceof Name) {
					 ASTNode parent= type.getParent();
					 if (parent instanceof Type)
						 return handleBug84585(((Type) parent).resolveBinding());
				 }
				 throw new IllegalStateException();
			 } finally {
				 wc.discardWorkingCopy();
			 }
		 } catch (JavaModelException e) {
			 return null;
		 }
	 }

	 public static ITypeBinding[] resolveSuperInterfaces(String[] interfaces, IType typeHandle, StubTypeContext superInterfaceContext) {
		 ITypeBinding[] result= new ITypeBinding[interfaces.length];

		 int[] interfaceOffsets= new int[interfaces.length];
		 StringBuilder cuString= new StringBuilder();
		 cuString.append(superInterfaceContext.getBeforeString());
		 int last= interfaces.length - 1;
		 for (int i= 0; i <= last; i++) {
			 interfaceOffsets[i]= cuString.length();
			 cuString.append(interfaces[i]);
			 if (i != last)
				 cuString.append(", "); //$NON-NLS-1$
		 }
		 cuString.append(superInterfaceContext.getAfterString());

		 try {
			 ICompilationUnit wc= typeHandle.getCompilationUnit().getWorkingCopy(new WorkingCopyOwner() {/*subclass*/}, new NullProgressMonitor());
			 try {
				 wc.getBuffer().setContents(cuString.toString());
				 CompilationUnit compilationUnit= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(wc, true);
				 for (int i= 0; i <= last; i++) {
					 ASTNode type= NodeFinder.perform(compilationUnit, interfaceOffsets[i], interfaces[i].length());
					 if (type instanceof Type) {
						 result[i]= handleBug84585(((Type) type).resolveBinding());
					 } else if (type instanceof Name) {
						 ASTNode parent= type.getParent();
						 if (parent instanceof Type) {
							 result[i]= handleBug84585(((Type) parent).resolveBinding());
						 } else {
							 throw new IllegalStateException();
						 }
					 } else {
						 throw new IllegalStateException();
					 }
				 }
			 } finally {
				 wc.discardWorkingCopy();
			 }
		 } catch (JavaModelException e) {
			 // won't happen
		 }
		 return result;
	 }

	 private TypeContextChecker() {
	 }
 }
