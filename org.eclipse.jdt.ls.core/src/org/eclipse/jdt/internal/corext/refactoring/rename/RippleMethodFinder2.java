/*******************************************************************************
 * Copyright (c) 2000, 2022 IBM Corporation and others.
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
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [rename] https://bugs.eclipse.org/99622
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

public class RippleMethodFinder2 {

	private final IMethod fMethod;
	private Set<IMethod> fDeclarations;
	private ITypeHierarchy fHierarchy;
	private MultiMap<IType, IMethod> fTypeToMethod;
	private Set<IType> fRootTypes;
	private MultiMap<IType, IType> fRootReps;
	private Map<IType, ITypeHierarchy> fRootHierarchies;
	private UnionFind fUnionFind;

	private final boolean fExcludeBinaries;
	private final ReferencesInBinaryContext fBinaryRefs;
	private Map<IMethod, SearchMatch> fDeclarationToMatch;
	private boolean fSearchOnlyInCompilationUnit = false;

	private static class MultiMap<K, V> {
		HashMap<K, Collection<V>> fImplementation= new HashMap<>();

		public void put(K key, V value) {
			Collection<V> collection= fImplementation.get(key);
			if (collection == null) {
				collection= new HashSet<>();
				fImplementation.put(key, collection);
			}
			collection.add(value);
		}

		public Collection<V> get(K key) {
			return fImplementation.get(key);
		}
	}
	private static class UnionFind {
		HashMap<IType, IType> fElementToRepresentative= new HashMap<>();

		public void init(IType type) {
			fElementToRepresentative.put(type, type);
		}

		//path compression:
		public IType find(IType element) {
			IType root= element;
			IType rep= fElementToRepresentative.get(root);
			while (rep != null && ! rep.equals(root)) {
				root= rep;
				rep= fElementToRepresentative.get(root);
			}
			if (rep == null)
				return null;

			rep= fElementToRepresentative.get(element);
			while (! rep.equals(root)) {
				IType temp= element;
				element= rep;
				fElementToRepresentative.put(temp, root);
				rep= fElementToRepresentative.get(element);
			}
			return root;
		}

//		//straightforward:
//		public IType find(IType element) {
//			IType current= element;
//			IType rep= (IType) fElementToRepresentative.get(current);
//			while (rep != null && ! rep.equals(current)) {
//				current= rep;
//				rep= (IType) fElementToRepresentative.get(current);
//			}
//			if (rep == null)
//				return null;
//			else
//				return current;
//		}

		public void union(IType rep1, IType rep2) {
			fElementToRepresentative.put(rep1, rep2);
		}
	}


	private RippleMethodFinder2(IMethod method, boolean excludeBinaries, boolean searchOnlyInCompilationUnit){
		fMethod= method;
		fExcludeBinaries= excludeBinaries;
		fSearchOnlyInCompilationUnit= searchOnlyInCompilationUnit;
		fBinaryRefs= null;
	}

	private RippleMethodFinder2(IMethod method, ReferencesInBinaryContext binaryRefs) {
		fMethod= method;
		fExcludeBinaries= true;
		fDeclarationToMatch= new HashMap<>();
		fBinaryRefs= binaryRefs;
	}

	public static IMethod[] getRelatedMethods(IMethod method, boolean excludeBinaries, IProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		try{
			if (! MethodChecks.isVirtual(method))
				return new IMethod[]{ method };

			return new RippleMethodFinder2(method, excludeBinaries, false).getAllRippleMethods(pm, owner);
		} finally{
			pm.done();
		}
	}
	public static IMethod[] getRelatedMethodsInCompilationUnit(IMethod method, NullProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		try{
			if (! MethodChecks.isVirtual(method))
				return new IMethod[]{ method };

			return new RippleMethodFinder2(method, true, true).getAllRippleMethods(pm, owner);
		} finally{
			pm.done();
		}
	}
	public static IMethod[] getRelatedMethods(IMethod method, IProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		return getRelatedMethods(method, true, pm, owner);
	}

	public static IMethod[] getRelatedMethods(IMethod method, ReferencesInBinaryContext binaryRefs, IProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		try {
			if (! MethodChecks.isVirtual(method))
				return new IMethod[]{ method };

			return new RippleMethodFinder2(method, binaryRefs).getAllRippleMethods(pm, owner);
		} finally{
			pm.done();
		}
	}

	private IMethod[] getAllRippleMethods(IProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		IMethod[] rippleMethods= findAllRippleMethods(pm, owner);
		if (fDeclarationToMatch == null)
			return rippleMethods;

		List<IMethod> filteredMethods= new ArrayList<>(rippleMethods.length / 2);
		for (IMethod currentMethod : rippleMethods) {
			Object match= fDeclarationToMatch.get(currentMethod);
			if (match != null) {
				fBinaryRefs.add((SearchMatch) match);
			} else {
				filteredMethods.add(currentMethod);
			}
		}
		fDeclarationToMatch= null;
		return toArray(filteredMethods);
	}

	private IMethod[] findAllRippleMethods(IProgressMonitor pm, WorkingCopyOwner owner) throws CoreException {
		pm.beginTask("", 4); //$NON-NLS-1$

		findAllDeclarations(new SubProgressMonitor(pm, 1), owner);

		//TODO: report assertion as error status and fall back to only return fMethod
		//check for bug 81058:
		if (! fDeclarations.contains(fMethod)) {
			if (fSearchOnlyInCompilationUnit) {
				return new IMethod[0];
			} else {
				Assert.isTrue(false, "Search for method declaration did not find original element: " + fMethod.toString()); //$NON-NLS-1$
			}
		}

		createHierarchyOfDeclarations(new SubProgressMonitor(pm, 1), owner);
		addMissedSuperTypes();
		createTypeToMethod();
		createUnionFind();
		checkCanceled(pm);

		fHierarchy= null;
		fRootTypes= null;

		Map<IType, List<IType>> partitioning= new HashMap<>();
		for (IType type : fTypeToMethod.fImplementation.keySet()) {
			IType rep= fUnionFind.find(type);
			List<IType> types= partitioning.get(rep);
			if (types == null)
				types= new ArrayList<>();
			types.add(type);
			partitioning.put(rep, types);
		}
		Assert.isTrue(partitioning.size() > 0);
		if (partitioning.size() == 1)
			return fDeclarations.toArray(new IMethod[fDeclarations.size()]);

		//Multiple partitions; must look out for nasty marriage cases
		//(types inheriting method from two ancestors, but without redeclaring it).
		IType methodTypeRep= fUnionFind.find(fMethod.getDeclaringType());
		List<IType> relatedTypes= partitioning.get(methodTypeRep);
		boolean hasRelatedInterfaces= false;
		List<IMethod> relatedMethods= new ArrayList<>();
		for (IType relatedType : relatedTypes) {
			relatedMethods.addAll(fTypeToMethod.get(relatedType));
			if (relatedType.isInterface())
				hasRelatedInterfaces= true;
		}

		int numberOfSearchMatches= fDeclarations.size();
		//Definition: An alien type is a type that is not a related type. The set of
		// alien types diminishes as new types become related (a.k.a marry a relatedType).

		Set<IMethod> alienDeclarations= new LinkedHashSet<>(fDeclarations);
		fDeclarations= null;
		alienDeclarations.removeAll(relatedMethods);
		Set<IType> alienTypes= new LinkedHashSet<>();
		boolean hasAlienInterfaces= false;
		for (IMethod alienDeclaration : alienDeclarations) {
			IType alienType= alienDeclaration.getDeclaringType();
			alienTypes.add(alienType);
			if (alienType.isInterface())
				hasAlienInterfaces= true;
		}
		if (alienTypes.isEmpty()) //no nasty marriage scenarios without types to marry with...
			return toArray(relatedMethods);
		if (! hasRelatedInterfaces && ! hasAlienInterfaces) //no nasty marriage scenarios without interfaces...
			return toArray(relatedMethods);

		/*
		 * Go down the hierarchy of the type under rename, and build hierarchies of its sub-types.
		 * Check if any sub-type implements another interface with the same method name.
		 * If not, we cannot have married alien types, so we can skip building type hierarchies for the alien types.
		 */
		checkCanceled(pm);
		IType methodType= fMethod.getDeclaringType();
		ITypeHierarchy methodHierarchy= hierarchy(pm, owner, fUnionFind.find(methodType));
		IType[] methodTypeSubtypes= methodHierarchy.getAllSubtypes(methodType);
		// don't spend time on this check, unless we have a small hierarchy for the type under rename and a lot of search matches
		if (methodTypeSubtypes.length <= numberOfSearchMatches / 10) {
			boolean couldHaveMarriedAlienTypes= couldHaveMarriedAlienTypes(pm, owner, methodHierarchy, methodTypeSubtypes);
			if (!couldHaveMarriedAlienTypes) {
				return toArray(relatedMethods);
			}
		}

		//find all subtypes of related types:
		HashSet<IType> relatedSubTypes= new HashSet<>();
		List<IType> relatedTypesToProcess= new ArrayList<>(relatedTypes);
		while (relatedTypesToProcess.size() > 0) {
			//TODO: would only need subtype hierarchies of all top-of-ripple relatedTypesToProcess
			for (IType relatedType : relatedTypesToProcess) {
				checkCanceled(pm);
				ITypeHierarchy hierarchy= hierarchy(pm, owner, relatedType);
				IType[] allSubTypes= hierarchy.getAllSubtypes(relatedType);
				relatedSubTypes.addAll(Arrays.asList(allSubTypes));
			}
			relatedTypesToProcess.clear(); //processed; make sure loop terminates

			HashSet<IType> marriedAlienTypeReps= new HashSet<>();
			for (IType alienType : alienTypes) {
				checkCanceled(pm);
				Collection<IMethod> alienMethods= fTypeToMethod.get(alienType);
				for (IMethod alienMethod : alienMethods) {
					ITypeHierarchy hierarchy= hierarchy(pm, owner, alienType);

					for (IType subtype : hierarchy.getAllSubtypes(alienType)) {
						if (relatedSubTypes.contains(subtype)) {
							if (JavaModelUtil.isVisibleInHierarchy(alienMethod, subtype.getPackageFragment())) {
								marriedAlienTypeReps.add(fUnionFind.find(alienType));
							} else {
								// not overridden
							}
						}
					}
				}
			}

			if (marriedAlienTypeReps.isEmpty())
				return toArray(relatedMethods);

			for (IType marriedAlienTypeRep : marriedAlienTypeReps) {
				List<IType> marriedAlienTypes= partitioning.get(marriedAlienTypeRep);
				for (IType marriedAlienInterfaceType : marriedAlienTypes) {
					relatedMethods.addAll(fTypeToMethod.get(marriedAlienInterfaceType));
				}
				alienTypes.removeAll(marriedAlienTypes); //not alien any more
				relatedTypesToProcess.addAll(marriedAlienTypes); //process freshly married types again
			}
		}

		fRootReps= null;
		fRootHierarchies= null;
		fTypeToMethod= null;
		fUnionFind= null;

		return toArray(relatedMethods);
	}

	/**
	 * For the method under rename, checks if any sub-type inherits a method with the same name from a different super type.
	 *
	 * @param pm progress monitor to
	 * @param owner owner of the compilation unit under rename
	 * @param methodHierarchy the type hierarchy of the method under rename
	 * @param methodTypeSubtypes the sub-types of the type under rename
	 * @return {@code false} if there can be no married alien types to the method under rename, {@code true} otherwise.
	 * @throws JavaModelException if creating a type hierarchy fails
	 */
	private boolean couldHaveMarriedAlienTypes(IProgressMonitor pm, WorkingCopyOwner owner, ITypeHierarchy methodHierarchy, IType[] methodTypeSubtypes) throws JavaModelException {
		Set<IType> allTypesInMethodHierarchy= new HashSet<>(Arrays.asList(methodHierarchy.getAllClasses()));
		allTypesInMethodHierarchy.addAll(Arrays.asList(methodHierarchy.getAllInterfaces()));


		for (IType methodTypeSubtype : methodTypeSubtypes) {
			checkCanceled(pm);
			ITypeHierarchy subtypeHierarchy= methodTypeSubtype.newTypeHierarchy(owner, pm);
			IType[] subtypeSuperTypes= subtypeHierarchy.getAllSupertypes(methodTypeSubtype);
			for (IType subtypeSuperType : subtypeSuperTypes) {
				checkCanceled(pm);
				if (!allTypesInMethodHierarchy.contains(subtypeSuperType)) {
					if (definesSimilarMethod(subtypeSuperType, fMethod)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * @return {@code true} if the specified type defines a method which shares method name and parameter types with the specified method.
	 *
	 * @param type the type to check in
	 * @param method the method to check for
	 * @throws JavaModelException if listing the methods of the type fails
	 */
	private static boolean definesSimilarMethod(IType type, IMethod method) throws JavaModelException {
		/*
		 * We don't use type.getMethod(method.getElementName(), method.getParameterTypes()),
		 * since types that come from binaries can list different parameter names. So we could miss a matching definition.
		 */
		String methodName= method.getElementName();
		IMethod[] typeMethods= type.getMethods();
		return Arrays.asList(typeMethods).stream().anyMatch(typeMethod -> methodName.equals(typeMethod.getElementName()));
	}

	private static IMethod[] toArray(List<IMethod> methods) {
		return methods.toArray(new IMethod[methods.size()]);
	}

	private static void checkCanceled(IProgressMonitor pm) {
		if (pm.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	private ITypeHierarchy hierarchy(IProgressMonitor pm, WorkingCopyOwner owner, IType type)
			throws JavaModelException {
		ITypeHierarchy hierarchy= getCachedHierarchy(type, owner, new SubProgressMonitor(pm, 1));
		if (hierarchy == null)
			hierarchy= type.newTypeHierarchy(owner, new SubProgressMonitor(pm, 1));
		return hierarchy;
	}

	private void addMissedSuperTypes() throws JavaModelException {
		Set<IMethod> newDeclarations = new HashSet<>();
		for (IMethod method : fDeclarations) {
			MethodOverrideTester methodOverrideTester= new MethodOverrideTester(method.getDeclaringType(), fHierarchy);
			newDeclarations.addAll(methodOverrideTester.findAllOverridenMethods(method));
		}
		fDeclarations.addAll(newDeclarations);

	}

	private ITypeHierarchy getCachedHierarchy(IType type, WorkingCopyOwner owner, IProgressMonitor monitor) throws JavaModelException {
		IType rep= fUnionFind.find(type);
		if (rep != null) {
			for (IType root : fRootReps.get(rep)) {
				ITypeHierarchy hierarchy= fRootHierarchies.get(root);
				if (hierarchy == null) {
					hierarchy= root.newTypeHierarchy(owner, new SubProgressMonitor(monitor, 1));
					fRootHierarchies.put(root, hierarchy);
				}
				if (hierarchy.contains(type))
					return hierarchy;
			}
		}
		return null;
	}

	private void findAllDeclarations(IProgressMonitor monitor, WorkingCopyOwner owner) throws CoreException {
		fDeclarations= new HashSet<>();

		class MethodRequestor extends SearchRequestor {
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				IMethod method= (IMethod) match.getElement();

				boolean isVisible= JavaModelUtil.isVisibleInHierarchy(method, fMethod.getDeclaringType().getPackageFragment());

				if (isVisible) {
					boolean isBinary= method.isBinary();
					if (fBinaryRefs != null || !fExcludeBinaries || !isBinary) {
						fDeclarations.add(method);
					}
					if (isBinary && fBinaryRefs != null) {
						fDeclarationToMatch.put(method, match);
					}
				}
			}
		}

		int limitTo= IJavaSearchConstants.DECLARATIONS | IJavaSearchConstants.IGNORE_DECLARING_TYPE | IJavaSearchConstants.IGNORE_RETURN_TYPE;
		int matchRule= SearchPattern.R_ERASURE_MATCH | SearchPattern.R_CASE_SENSITIVE;
		SearchPattern pattern= SearchPattern.createPattern(fMethod, limitTo, matchRule);
		if (pattern == null) {
			return;
		}
		SearchParticipant[] participants= SearchUtils.getDefaultSearchParticipants();
		IJavaSearchScope scope;
		if (fSearchOnlyInCompilationUnit) {
			scope= RefactoringScopeFactory.create(fMethod.getCompilationUnit());
		} else {
			scope= RefactoringScopeFactory.createRelatedProjectsScope(fMethod.getJavaProject(), IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
		}
		MethodRequestor requestor= new MethodRequestor();
		SearchEngine searchEngine= owner != null ? new SearchEngine(owner) : new SearchEngine();

		searchEngine.search(pattern, participants, scope, requestor, monitor);
	}

	private void createHierarchyOfDeclarations(IProgressMonitor pm, WorkingCopyOwner owner) throws JavaModelException {
		Stream<IType> types= fDeclarations.stream().map(IMethod::getDeclaringType);
		fHierarchy= createHierarchyOfTypes(pm, owner, types);
	}

	private static ITypeHierarchy createHierarchyOfTypes(IProgressMonitor pm, WorkingCopyOwner owner, Stream<IType> types) throws JavaModelException {
		IRegion region= JavaCore.newRegion();
		for (Iterator<IType> iter= types.iterator(); iter.hasNext(); ) {
			IType type= iter.next();
			region.add(type);
		}
		return JavaCore.newTypeHierarchy(region, owner, pm);
	}

	private void createTypeToMethod() {
		fTypeToMethod= new MultiMap<>();
		for (IMethod declaration : fDeclarations) {
			fTypeToMethod.put(declaration.getDeclaringType(), declaration);
		}
	}

	private void createUnionFind() throws JavaModelException {
		fRootTypes= new HashSet<>(fTypeToMethod.fImplementation.keySet());
		fUnionFind= new UnionFind();
		for (IType type : fTypeToMethod.fImplementation.keySet()) {
			fUnionFind.init(type);
		}
		for (IType type : fTypeToMethod.fImplementation.keySet()) {
			uniteWithSupertypes(type, type);
		}
		fRootReps= new MultiMap<>();
		for (IType type : fRootTypes) {
			IType rep= fUnionFind.find(type);
			if (rep != null)
				fRootReps.put(rep, type);
		}
		fRootHierarchies= new HashMap<>();
	}

	private void uniteWithSupertypes(IType anchor, IType type) throws JavaModelException {
		for (IType supertype : fHierarchy.getSupertypes(type)) {
			IType superRep= fUnionFind.find(supertype);
			if (superRep == null) {
				//Type doesn't declare method, but maybe supertypes?
				uniteWithSupertypes(anchor, supertype);
			} else {
				//check whether method in supertype is really overridden:
				Collection<IMethod> superMethods= fTypeToMethod.get(supertype);
				for (IMethod superMethod : superMethods) {
					if (JavaModelUtil.isVisibleInHierarchy(superMethod, anchor.getPackageFragment())) {
						IType rep= fUnionFind.find(anchor);
						fUnionFind.union(rep, superRep);
						// current type is no root anymore
						fRootTypes.remove(anchor);
						uniteWithSupertypes(supertype, supertype);
					} else {
						//Not overridden -> overriding chain ends here.
					}
				}
			}
		}
	}
}
