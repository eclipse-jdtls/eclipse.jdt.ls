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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;

/**
 * Method implementations extracted from JDT UI. Mostly from
 * <code>org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal</code>
 *
 * @author aboyko
 *
 * Copied from Flux project.
 *
 */
public class TypeProposalUtils {

	private static final String PACKAGE_INFO_JAVA = "package-info.java"; //$NON-NLS-1$
	private static final String[] IMPORTS_ORDER = new String[] { "java",
			"javax", "org", "com" };
	private static final int IMPORTS_THRESHOLD = 99;

	static void createName(ITypeBinding type, boolean includePackage,
			List<String> list) {
		ITypeBinding baseType = type;
		if (type.isArray()) {
			baseType = type.getElementType();
		}
		if (!baseType.isPrimitive() && !baseType.isNullType()) {
			ITypeBinding declaringType = baseType.getDeclaringClass();
			if (declaringType != null) {
				createName(declaringType, includePackage, list);
			} else if (includePackage && !baseType.getPackage().isUnnamed()) {
				String[] components = baseType.getPackage().getNameComponents();
				for (int i = 0; i < components.length; i++) {
					list.add(components[i]);
				}
			}
		}
		if (!baseType.isAnonymous()) {
			list.add(type.getName());
		} else {
			list.add("$local$"); //$NON-NLS-1$
		}
	}

	static String getTypeQualifiedName(ITypeBinding type) {
		List<String> result= new ArrayList<>(5);
		createName(type, false, result);
		return String.join(".",result);
	}

	static String[] getSuperTypeSignatures(IType subType, IType superType) throws JavaModelException {
		if (superType.isInterface())
			return subType.getSuperInterfaceTypeSignatures();
		else
			return new String[] {subType.getSuperclassTypeSignature()};
	}

	static String findMatchingSuperTypeSignature(IType subType, IType superType) throws JavaModelException {
		String[] signatures= getSuperTypeSignatures(subType, superType);
		for (String signature : signatures) {
			String qualified= SignatureUtil.qualifySignature(signature, subType);
			String subFQN= SignatureUtil.stripSignatureToFQN(qualified);

			String superFQN= superType.getFullyQualifiedName();
			if (subFQN.equals(superFQN)) {
				return signature;
			}

			// TODO handle local types
		}

		return null;
		//		throw new JavaModelException(new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, "Illegal hierarchy", null))); //$NON-NLS-1$
	}

	static int findMatchingTypeArgumentIndex(String signature, String argument) {
		String[] typeArguments= Signature.getTypeArguments(signature);
		for (int i= 0; i < typeArguments.length; i++) {
			if (Signature.getSignatureSimpleName(typeArguments[i]).equals(argument))
				return i;
		}
		return -1;
	}

	static int mapTypeParameterIndex(IType[] path, int pathIndex, int paramIndex) throws JavaModelException, ArrayIndexOutOfBoundsException {
		if (pathIndex == 0)
			// break condition: we've reached the top of the hierarchy
			return paramIndex;

		IType subType= path[pathIndex];
		IType superType= path[pathIndex - 1];

		String superSignature= findMatchingSuperTypeSignature(subType, superType);
		ITypeParameter param= subType.getTypeParameters()[paramIndex];
		int index= findMatchingTypeArgumentIndex(superSignature, param.getElementName());
		if (index == -1) {
			// not mapped through
			return -1;
		}

		return mapTypeParameterIndex(path, pathIndex - 1, index);
	}

	static IType[] computeInheritancePath(IType subType, IType superType) throws JavaModelException {
		if (superType == null)
			return null;

		// optimization: avoid building the type hierarchy for the identity case
		if (superType.equals(subType))
			return new IType[] { subType };

		ITypeHierarchy hierarchy= subType.newSupertypeHierarchy(new NullProgressMonitor());
		if (!hierarchy.contains(superType))
			return null; // no path

		List<IType> path= new LinkedList<>();
		path.add(superType);
		do {
			// any sub type must be on a hierarchy chain from superType to subType
			superType= hierarchy.getSubtypes(superType)[0];
			path.add(superType);
		} while (!superType.equals(subType)); // since the equality case is handled above, we can spare one check

		return path.toArray(new IType[path.size()]);
	}

	static boolean isPackageInfo(ICompilationUnit cu) {
		return PACKAGE_INFO_JAVA.equals(cu.getElementName());
	}

	static ImportRewrite createImportRewrite(ICompilationUnit compilationUnit) {
		try {
			ImportRewrite rewrite = ImportRewrite.create(compilationUnit, true);
			rewrite.setImportOrder(IMPORTS_ORDER);
			rewrite.setOnDemandImportThreshold(IMPORTS_THRESHOLD);
			rewrite.setStaticOnDemandImportThreshold(IMPORTS_THRESHOLD);
			return rewrite;
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	static boolean isImplicitImport(String qualifier, ICompilationUnit cu) {
		if ("java.lang".equals(qualifier)) { //$NON-NLS-1$
			return true;
		}
		String packageName = cu.getParent().getElementName();
		if (qualifier.equals(packageName)) {
			return true;
		}
		String typeName = JavaCore.removeJavaLikeExtension(cu.getElementName());
		String mainTypeName = concatenateName(packageName, typeName);
		return qualifier.equals(mainTypeName);
	}

	private static String concatenateName(String name1, String name2) {
		StringBuilder buf = new StringBuilder();
		if (name1 != null && name1.length() > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length() > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}
		return buf.toString();
	}

}
