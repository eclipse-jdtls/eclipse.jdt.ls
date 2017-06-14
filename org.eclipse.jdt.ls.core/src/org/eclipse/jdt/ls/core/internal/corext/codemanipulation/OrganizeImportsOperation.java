/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/codemanipulation/OrganizeImportsOperation.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.codemanipulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.corext.utils.TypeNameMatchCollector;
import org.eclipse.jdt.ls.core.internal.corrections.SimilarElementsRequestor;
import org.eclipse.text.edits.TextEdit;

public class OrganizeImportsOperation {
	public static interface IChooseImportQuery {
		/**
		 * Selects imports from a list of choices.
		 * @param openChoices From each array, a type reference has to be selected
		 * @param ranges For each choice the range of the corresponding  type reference.
		 * @return Returns <code>null</code> to cancel the operation, or the
		 *         selected imports.
		 */
		TypeNameMatch[] chooseImports(TypeNameMatch[][] openChoices, ISourceRange[] ranges);
	}

	/**
	 * Matches unresolvable import declarations (those having associated
	 * {@link IProblem#ImportNotFound} problems) to unresolved simple names.
	 * <p>
	 * For a given simple name, looks first for single imports of that simple name and then,
	 * in the absence of such, for any on-demand imports. Considers type imports for simple names
	 * of unresolved types and static imports for simple names of unresolved static members.
	 * <p>
	 * @see <a href="https://bugs.eclipse.org/357795">Bug 357795</a>
	 */
	private static class UnresolvableImportMatcher {
		static UnresolvableImportMatcher forCompilationUnit(CompilationUnit cu) {
			Collection<ImportDeclaration> unresolvableImports= determineUnresolvableImports(cu);

			Map<String, Set<String>> typeImportsBySimpleName= new HashMap<>();
			Map<String, Set<String>> staticImportsBySimpleName= new HashMap<>();
			for (ImportDeclaration importDeclaration : unresolvableImports) {
				String qualifiedName= importDeclaration.isOnDemand()
						? importDeclaration.getName().getFullyQualifiedName() + ".*" //$NON-NLS-1$
								: importDeclaration.getName().getFullyQualifiedName();

				String simpleName= qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);

				Map<String, Set<String>> importsBySimpleName= importDeclaration.isStatic()
						? staticImportsBySimpleName : typeImportsBySimpleName;
				Set<String> importsWithSimpleName= importsBySimpleName.get(simpleName);
				if (importsWithSimpleName == null) {
					importsWithSimpleName= new HashSet<>();
					importsBySimpleName.put(simpleName, importsWithSimpleName);
				}

				importsWithSimpleName.add(qualifiedName);
			}

			return new UnresolvableImportMatcher(typeImportsBySimpleName, staticImportsBySimpleName);
		}

		private static Collection<ImportDeclaration> determineUnresolvableImports(CompilationUnit cu) {
			Collection<ImportDeclaration> unresolvableImports= new ArrayList<>(cu.imports().size());
			for (IProblem problem : cu.getProblems()) {
				if (problem.getID() == IProblem.ImportNotFound) {
					ImportDeclaration problematicImport= getProblematicImport(problem, cu);
					if (problematicImport != null) {
						unresolvableImports.add(problematicImport);
					}
				}
			}

			return unresolvableImports;
		}

		private static ImportDeclaration getProblematicImport(IProblem problem, CompilationUnit astRoot) {
			ASTNode coveringNode = new NodeFinder(astRoot, problem.getSourceStart(),
					problem.getSourceEnd() - problem.getSourceEnd()).getCoveringNode();
			if (coveringNode != null) {
				ASTNode importNode= ASTNodes.getParent(coveringNode, ASTNode.IMPORT_DECLARATION);
				if (importNode instanceof ImportDeclaration) {
					return (ImportDeclaration) importNode;
				}
			}
			return null;
		}

		private final Map<String, Set<String>> fTypeImportsBySimpleName;
		private final Map<String, Set<String>> fStaticImportsBySimpleName;

		private UnresolvableImportMatcher(
				Map<String, Set<String>> typeImportsBySimpleName, Map<String, Set<String>> staticImportsBySimpleName) {
			fTypeImportsBySimpleName= typeImportsBySimpleName;
			fStaticImportsBySimpleName= staticImportsBySimpleName;
		}

		private Set<String> matchImports(boolean isStatic, String simpleName) {
			Map<String, Set<String>> importsBySimpleName= isStatic
					? fStaticImportsBySimpleName : fTypeImportsBySimpleName;

			Set<String> matchingSingleImports= importsBySimpleName.get(simpleName);
			if (matchingSingleImports != null) {
				return Collections.unmodifiableSet(matchingSingleImports);
			}

			Set<String> matchingOnDemandImports= importsBySimpleName.get("*"); //$NON-NLS-1$
			if (matchingOnDemandImports != null) {
				return Collections.unmodifiableSet(matchingOnDemandImports);
			}

			return Collections.emptySet();
		}

		Set<String> matchTypeImports(String simpleName) {
			return matchImports(false, simpleName);
		}

		Set<String> matchStaticImports(String simpleName) {
			return matchImports(true, simpleName);
		}
	}

	private static class TypeReferenceProcessor {

		private static class UnresolvedTypeData {
			final SimpleName ref;
			final int typeKinds;
			final List<TypeNameMatch> foundInfos;

			public UnresolvedTypeData(SimpleName ref) {
				this.ref= ref;
				this.typeKinds = org.eclipse.jdt.ls.core.internal.corrections.ASTResolving.getPossibleTypeKinds(ref,
						true);
				this.foundInfos= new ArrayList<>(3);
			}

			public void addInfo(TypeNameMatch info) {
				for (int i= this.foundInfos.size() - 1; i >= 0; i--) {
					TypeNameMatch curr= this.foundInfos.get(i);
					if (curr.getTypeContainerName().equals(info.getTypeContainerName())) {
						return; // not added. already contains type with same name
					}
				}
				foundInfos.add(info);
			}
		}

		private Set<String> fOldSingleImports;
		private Set<String> fOldDemandImports;

		private Set<String> fImplicitImports;

		private ImportRewrite fImpStructure;

		private boolean fDoIgnoreLowerCaseNames;

		private final UnresolvableImportMatcher fUnresolvableImportMatcher;

		private IPackageFragment fCurrPackage;

		private ScopeAnalyzer fAnalyzer;
		private boolean fAllowDefaultPackageImports;

		private Map<String, UnresolvedTypeData> fUnresolvedTypes;
		private Set<String> fImportsAdded;
		private TypeNameMatch[][] fOpenChoices;
		private SourceRange[] fSourceRanges;


		public TypeReferenceProcessor(Set<String> oldSingleImports, Set<String> oldDemandImports, CompilationUnit root, ImportRewrite impStructure, boolean ignoreLowerCaseNames, UnresolvableImportMatcher unresolvableImportMatcher) {
			fOldSingleImports= oldSingleImports;
			fOldDemandImports= oldDemandImports;
			fImpStructure= impStructure;
			fDoIgnoreLowerCaseNames= ignoreLowerCaseNames;
			fUnresolvableImportMatcher= unresolvableImportMatcher;

			ICompilationUnit cu= impStructure.getCompilationUnit();

			fImplicitImports= new HashSet<>(3);
			fImplicitImports.add(""); //$NON-NLS-1$
			fImplicitImports.add("java.lang"); //$NON-NLS-1$
			fImplicitImports.add(cu.getParent().getElementName());

			fAnalyzer= new ScopeAnalyzer(root);

			fCurrPackage= (IPackageFragment) cu.getParent();

			fAllowDefaultPackageImports= cu.getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true).equals(JavaCore.VERSION_1_3);

			fImportsAdded= new HashSet<>();
			fUnresolvedTypes= new HashMap<>();
		}

		private boolean needsImport(ITypeBinding typeBinding, SimpleName ref) {
			if (!typeBinding.isTopLevel() && !typeBinding.isMember() || typeBinding.isRecovered()) {
				return false; // no imports for anonymous, local, primitive types or parameters types
			}
			int modifiers= typeBinding.getModifiers();
			if (Modifier.isPrivate(modifiers)) {
				return false; // imports for privates are not required
			}
			ITypeBinding currTypeBinding= Bindings.getBindingOfParentType(ref);
			if (currTypeBinding == null) {
				if (ASTNodes.getParent(ref, ASTNode.PACKAGE_DECLARATION) != null) {
					return true; // reference in package-info.java
				}
				return false; // not in a type
			}
			if (!Modifier.isPublic(modifiers)) {
				if (!currTypeBinding.getPackage().getName().equals(typeBinding.getPackage().getName())) {
					return false; // not visible
				}
			}

			ASTNode parent= ref.getParent();
			while (parent instanceof Type) {
				parent= parent.getParent();
			}
			if (parent instanceof AbstractTypeDeclaration && parent.getParent() instanceof CompilationUnit) {
				return true;
			}

			if (typeBinding.isMember()) {
				if (fAnalyzer.isDeclaredInScope(typeBinding, ref, ScopeAnalyzer.TYPES | ScopeAnalyzer.CHECK_VISIBILITY)) {
					return false;
				}
			}
			return true;
		}


		/**
		 * Tries to find the given type name and add it to the import structure.
		 * @param ref the name node
		 */
		public void add(SimpleName ref) {
			String typeName= ref.getIdentifier();

			if (fImportsAdded.contains(typeName)) {
				return;
			}

			IBinding binding= ref.resolveBinding();
			if (binding != null) {
				if (binding.getKind() != IBinding.TYPE) {
					return;
				}
				ITypeBinding typeBinding= (ITypeBinding) binding;
				if (typeBinding.isArray()) {
					typeBinding= typeBinding.getElementType();
				}
				typeBinding= typeBinding.getTypeDeclaration();
				if (!typeBinding.isRecovered()) {
					if (needsImport(typeBinding, ref)) {
						fImpStructure.addImport(typeBinding);
						fImportsAdded.add(typeName);
					}
					return;
				}
			} else {
				if (fDoIgnoreLowerCaseNames && typeName.length() > 0) {
					char ch= typeName.charAt(0);
					if (Strings.isLowerCase(ch) && Character.isLetter(ch)) {
						return;
					}
				}
			}

			fImportsAdded.add(typeName);
			fUnresolvedTypes.put(typeName, new UnresolvedTypeData(ref));
		}

		public boolean process(IProgressMonitor monitor) throws JavaModelException {
			try {
				int nUnresolved= fUnresolvedTypes.size();
				if (nUnresolved == 0) {
					return false;
				}
				char[][] allTypes= new char[nUnresolved][];
				int i= 0;
				for (Iterator<String> iter= fUnresolvedTypes.keySet().iterator(); iter.hasNext();) {
					allTypes[i++]= iter.next().toCharArray();
				}
				final ArrayList<TypeNameMatch> typesFound= new ArrayList<>();
				final IJavaProject project= fCurrPackage.getJavaProject();
				IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { project });
				TypeNameMatchCollector collector= new TypeNameMatchCollector(typesFound);
				new SearchEngine().searchAllTypeNames(null, allTypes, scope, collector, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);

				boolean is50OrHigher= JavaModelUtil.is50OrHigher(project);

				for (i= 0; i < typesFound.size(); i++) {
					TypeNameMatch curr= typesFound.get(i);
					UnresolvedTypeData data= fUnresolvedTypes.get(curr.getSimpleTypeName());
					if (data != null && isVisible(curr) && isOfKind(curr, data.typeKinds, is50OrHigher)) {
						if (fAllowDefaultPackageImports || curr.getPackageName().length() > 0) {
							data.addInfo(curr);
						}
					}
				}

				for (Entry<String, UnresolvedTypeData> entry : fUnresolvedTypes.entrySet()) {
					if (entry.getValue().foundInfos.size() == 0) { // No result found in search
						Set<String> matchingUnresolvableImports= fUnresolvableImportMatcher.matchTypeImports(entry.getKey());
						if (!matchingUnresolvableImports.isEmpty()) {
							// If there are matching unresolvable import(s), rely on them to provide the type.
							for (String string : matchingUnresolvableImports) {
								fImpStructure.addImport(string, UNRESOLVABLE_IMPORT_CONTEXT);
							}
						}
					}
				}

				ArrayList<TypeNameMatch[]> openChoices= new ArrayList<>(nUnresolved);
				ArrayList<SourceRange> sourceRanges= new ArrayList<>(nUnresolved);
				for (Iterator<UnresolvedTypeData> iter= fUnresolvedTypes.values().iterator(); iter.hasNext();) {
					UnresolvedTypeData data= iter.next();
					TypeNameMatch[] openChoice= processTypeInfo(data.foundInfos);
					if (openChoice != null) {
						openChoices.add(openChoice);
						sourceRanges.add(new SourceRange(data.ref.getStartPosition(), data.ref.getLength()));
					}
				}
				if (openChoices.isEmpty()) {
					return false;
				}
				fOpenChoices= openChoices.toArray(new TypeNameMatch[openChoices.size()][]);
				fSourceRanges= sourceRanges.toArray(new SourceRange[sourceRanges.size()]);
				return true;
			} finally {
				monitor.done();
			}
		}

		private TypeNameMatch[] processTypeInfo(List<TypeNameMatch> typeRefsFound) {
			int nFound= typeRefsFound.size();
			if (nFound == 0) {
				// nothing found
				return null;
			} else if (nFound == 1) {
				TypeNameMatch typeRef= typeRefsFound.get(0);
				fImpStructure.addImport(typeRef.getFullyQualifiedName());
				return null;
			} else {
				String typeToImport= null;
				boolean ambiguousImports= false;

				// multiple found, use old imports to find an entry
				for (int i= 0; i < nFound; i++) {
					TypeNameMatch typeRef= typeRefsFound.get(i);
					String fullName= typeRef.getFullyQualifiedName();
					String containerName= typeRef.getTypeContainerName();
					if (fOldSingleImports.contains(fullName)) {
						// was single-imported
						fImpStructure.addImport(fullName);
						return null;
					} else if (fOldDemandImports.contains(containerName) || fImplicitImports.contains(containerName)) {
						if (typeToImport == null) {
							typeToImport= fullName;
						} else {  // more than one import-on-demand
							ambiguousImports= true;
						}
					}
				}

				if (typeToImport != null && !ambiguousImports) {
					fImpStructure.addImport(typeToImport);
					return null;
				}
				// return the open choices
				return typeRefsFound.toArray(new TypeNameMatch[nFound]);
			}
		}

		private boolean isOfKind(TypeNameMatch curr, int typeKinds, boolean is50OrHigher) {
			int flags= curr.getModifiers();
			if (Flags.isAnnotation(flags)) {
				return is50OrHigher && (typeKinds & SimilarElementsRequestor.ANNOTATIONS) != 0;
			}
			if (Flags.isEnum(flags)) {
				return is50OrHigher && (typeKinds & SimilarElementsRequestor.ENUMS) != 0;
			}
			if (Flags.isInterface(flags)) {
				return (typeKinds & SimilarElementsRequestor.INTERFACES) != 0;
			}
			return (typeKinds & SimilarElementsRequestor.CLASSES) != 0;
		}

		private boolean isVisible(TypeNameMatch curr) {
			int flags= curr.getModifiers();
			if (Flags.isPrivate(flags)) {
				return false;
			}
			boolean isPublic;
			try {
				isPublic= JdtFlags.isPublic(curr.getType());
			} catch (JavaModelException e) {
				isPublic= Flags.isPublic(flags);
			}
			if (isPublic || Flags.isProtected(flags)) {
				return true;
			}
			return curr.getPackageName().equals(fCurrPackage.getElementName());
		}

		public TypeNameMatch[][] getChoices() {
			return fOpenChoices;
		}

		public ISourceRange[] getChoicesSourceRanges() {
			return fSourceRanges;
		}
	}

	/**
	 * Used to ensure that unresolvable imports don't get reduced into on-demand imports.
	 */
	private static ImportRewriteContext UNRESOLVABLE_IMPORT_CONTEXT= new ImportRewriteContext() {
		@Override
		public int findInContext(String qualifier, String name, int kind) {
			return RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT;
		}
	};

	private boolean fDoSave;

	private boolean fIgnoreLowerCaseNames;

	private IChooseImportQuery fChooseImportQuery;

	private int fNumberOfImportsAdded;
	private int fNumberOfImportsRemoved;

	private IProblem fParsingError;
	private ICompilationUnit fCompilationUnit;

	private CompilationUnit fASTRoot;

	private final boolean fAllowSyntaxErrors;

	public OrganizeImportsOperation(ICompilationUnit cu, CompilationUnit astRoot, boolean ignoreLowerCaseNames, boolean save, boolean allowSyntaxErrors, IChooseImportQuery chooseImportQuery) {
		fCompilationUnit= cu;
		fASTRoot= astRoot;

		fDoSave= save;
		fIgnoreLowerCaseNames= ignoreLowerCaseNames;
		fAllowSyntaxErrors= allowSyntaxErrors;
		fChooseImportQuery= chooseImportQuery;

		fNumberOfImportsAdded= 0;
		fNumberOfImportsRemoved= 0;

		fParsingError= null;
	}

	public TextEdit createTextEdit(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		try {
			fNumberOfImportsAdded= 0;
			fNumberOfImportsRemoved= 0;

			monitor.beginTask(
					Messages.format("Organizing imports of {0}...", BasicElementLabels.getFileName(fCompilationUnit)),
					9);

			CompilationUnit astRoot= fASTRoot;
			if (astRoot == null) {
				astRoot = SharedASTProvider.getInstance().getAST(fCompilationUnit, new SubProgressMonitor(monitor, 2));
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
			} else {
				monitor.worked(2);
			}

			ImportRewrite importsRewrite= StubUtility.createImportRewrite(astRoot, false);

			Set<String> oldSingleImports= new HashSet<>();
			Set<String>  oldDemandImports= new HashSet<>();
			List<SimpleName> typeReferences= new ArrayList<>();
			List<SimpleName> staticReferences= new ArrayList<>();

			if (!collectReferences(astRoot, typeReferences, staticReferences, oldSingleImports, oldDemandImports)) {
				return null;
			}

			monitor.worked(1);

			UnresolvableImportMatcher unresolvableImportMatcher =
					UnresolvableImportMatcher.forCompilationUnit(astRoot);

			TypeReferenceProcessor processor= new TypeReferenceProcessor(
					oldSingleImports,
					oldDemandImports,
					astRoot,
					importsRewrite,
					fIgnoreLowerCaseNames,
					unresolvableImportMatcher);

			Iterator<SimpleName> refIterator= typeReferences.iterator();
			while (refIterator.hasNext()) {
				SimpleName typeRef= refIterator.next();
				processor.add(typeRef);
			}

			boolean hasOpenChoices= processor.process(new SubProgressMonitor(monitor, 3));
			addStaticImports(staticReferences, importsRewrite, unresolvableImportMatcher);

			if (hasOpenChoices && fChooseImportQuery != null) {
				TypeNameMatch[][] choices= processor.getChoices();
				ISourceRange[] ranges= processor.getChoicesSourceRanges();
				TypeNameMatch[] chosen= fChooseImportQuery.chooseImports(choices, ranges);
				if (chosen == null) {
					// cancel pressed by the user
					throw new OperationCanceledException();
				}
				for (int i= 0; i < chosen.length; i++) {
					TypeNameMatch typeInfo= chosen[i];
					if (typeInfo != null) {
						importsRewrite.addImport(typeInfo.getFullyQualifiedName());
					} else { // Skipped by user
						String typeName= choices[i][0].getSimpleTypeName();
						Set<String> matchingUnresolvableImports= unresolvableImportMatcher.matchTypeImports(typeName);
						if (!matchingUnresolvableImports.isEmpty()) {
							// If there are matching unresolvable import(s), rely on them to provide the type.
							for (String string : matchingUnresolvableImports) {
								importsRewrite.addImport(string, UNRESOLVABLE_IMPORT_CONTEXT);
							}
						}
					}
				}
			}

			TextEdit result= importsRewrite.rewriteImports(new SubProgressMonitor(monitor, 3));

			determineImportDifferences(importsRewrite, oldSingleImports, oldDemandImports);

			return result;
		} finally {
			monitor.done();
		}
	}

	private void determineImportDifferences(ImportRewrite importsStructure, Set<String> oldSingleImports, Set<String> oldDemandImports) {
		ArrayList<String> importsAdded= new ArrayList<>();
		importsAdded.addAll(Arrays.asList(importsStructure.getCreatedImports()));
		importsAdded.addAll(Arrays.asList(importsStructure.getCreatedStaticImports()));

		Object[] content= oldSingleImports.toArray();
		for (int i= 0; i < content.length; i++) {
			String importName= (String) content[i];
			if (importsAdded.remove(importName)) {
				oldSingleImports.remove(importName);
			}
		}
		content= oldDemandImports.toArray();
		for (int i= 0; i < content.length; i++) {
			String importName= (String) content[i];
			if (importsAdded.remove(importName + ".*")) {
				oldDemandImports.remove(importName);
			}
		}
		fNumberOfImportsAdded= importsAdded.size();
		fNumberOfImportsRemoved= oldSingleImports.size() + oldDemandImports.size();
	}


	private void addStaticImports(
			Collection<SimpleName> staticReferences,
			ImportRewrite importRewrite,
			UnresolvableImportMatcher unresolvableImportMatcher) {
		for (SimpleName name : staticReferences) {
			IBinding binding= name.resolveBinding();
			if (binding != null) {
				importRewrite.addStaticImport(binding);
			} else {
				// This could be an unresolvable reference to a static member.
				String identifier= name.getIdentifier();
				Set<String> unresolvableImports= unresolvableImportMatcher.matchStaticImports(identifier);
				for (String unresolvableImport : unresolvableImports) {
					int lastDotIndex= unresolvableImport.lastIndexOf('.');
					// It's OK to skip invalid imports.
					if (lastDotIndex != -1) {
						String declaringTypeName= unresolvableImport.substring(0, lastDotIndex);
						String simpleName= unresolvableImport.substring(lastDotIndex + 1);
						// Whether name refers to a field or to a method is unknown.
						boolean isField= false;
						importRewrite.addStaticImport(declaringTypeName, simpleName, isField, UNRESOLVABLE_IMPORT_CONTEXT);
					}
				}
			}
		}
	}


	// find type references in a compilation unit
	private boolean collectReferences(CompilationUnit astRoot, List<SimpleName> typeReferences, List<SimpleName> staticReferences, Set<String> oldSingleImports, Set<String> oldDemandImports) {
		if (!fAllowSyntaxErrors) {
			IProblem[] problems= astRoot.getProblems();
			for (int i= 0; i < problems.length; i++) {
				IProblem curr= problems[i];
				if (curr.isError() && (curr.getID() & IProblem.Syntax) != 0) {
					fParsingError= problems[i];
					return false;
				}
			}
		}
		List<ImportDeclaration> imports= astRoot.imports();
		for (int i= 0; i < imports.size(); i++) {
			ImportDeclaration curr= imports.get(i);
			String id= ASTResolving.getFullName(curr.getName());
			if (curr.isOnDemand()) {
				oldDemandImports.add(id);
			} else {
				oldSingleImports.add(id);
			}
		}

		IJavaProject project= fCompilationUnit.getJavaProject();
		ImportReferencesCollector.collect(astRoot, project, null, typeReferences, staticReferences);

		return true;
	}

	/**
	 * After executing the operation, returns <code>null</code> if the operation has been executed successfully or
	 * the range where parsing failed.
	 * @return returns the parse error
	 */
	public IProblem getParseError() {
		return fParsingError;
	}

	public int getNumberOfImportsAdded() {
		return fNumberOfImportsAdded;
	}

	public int getNumberOfImportsRemoved() {
		return fNumberOfImportsRemoved;
	}

	/**
	 * @return Returns the scheduling rule for this operation
	 */
	public ISchedulingRule getScheduleRule() {
		return fCompilationUnit.getResource();
	}

}
