/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.changes.RenamePackageChange;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.corext.util.JavaElementUtil;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;
import org.eclipse.text.edits.TextEdit;

/**
 * Utility methods for converting Refactoring changes.
 *
 * @author Valeriy Svydenko
 *
 */
public class ChangeUtil {

	/**
	 * Converts changes to resource changes if resource changes are supported by the
	 * client otherwise converts to TextEdit changes.
	 *
	 * @param change
	 *            changes after Refactoring operation
	 * @param edit
	 *            instance of workspace edit changes
	 * @param manager
	 *            preference manager
	 * @throws CoreException
	 */
	public static void convertChanges(Change change, WorkspaceEdit edit) throws CoreException {
		if (!(change instanceof CompositeChange)) {
			return;
		}

		Change[] changes = ((CompositeChange) change).getChildren();
		for (Change ch : changes) {
			if (ch instanceof DynamicValidationRefactoringChange) {
				CompositeChange compositeChange = (CompositeChange) ch;
				for (Change child : compositeChange.getChildren()) {
					convertCompositeChange(child, edit);
				}
			} else {
				convertCompositeChange(ch, edit);
			}
		}
	}

	private static void convertCompositeChange(Change change, WorkspaceEdit edit) throws CoreException {
		Object modifiedElement = change.getModifiedElement();
		if (!(modifiedElement instanceof IJavaElement)) {
			return;
		}

		if (change instanceof TextChange) {
			convertTextChange(edit, (IJavaElement) modifiedElement, (TextChange) change);
		} else if (change instanceof ResourceChange) {
			ResourceChange resourceChange = (ResourceChange) change;
			convertResourceChange(edit, resourceChange);
		}
	}

	private static void convertResourceChange(WorkspaceEdit edit, ResourceChange resourceChange) throws CoreException {
		if (!JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceEditResourceChangesSupported()) {
			return;
		}

		// Resource change is needed and supported by client
		if (resourceChange instanceof RenameCompilationUnitChange) {
			convertCUResourceChange(edit, (RenameCompilationUnitChange) resourceChange);
		} else if (resourceChange instanceof RenamePackageChange) {
			convertRenamePackcageChange(edit, (RenamePackageChange) resourceChange);
		}
	}

	private static void convertRenamePackcageChange(WorkspaceEdit edit, RenamePackageChange packageChange) throws CoreException {
		IPackageFragment pack = (IPackageFragment) packageChange.getModifiedElement();
		List<ICompilationUnit> units = new ArrayList<>();
		if (packageChange.getRenameSubpackages()) {
			IPackageFragment[] allPackages = JavaElementUtil.getPackageAndSubpackages(pack);
			for (IPackageFragment currentPackage : allPackages) {
				units.addAll(Arrays.asList(currentPackage.getCompilationUnits()));
			}
		} else {
			units.addAll(Arrays.asList(pack.getCompilationUnits()));
		}

		//update package's declaration
		for (ICompilationUnit cu : units) {
			CompilationUnit unit = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(cu, true);
			ASTRewrite rewrite = ASTRewrite.create(unit.getAST());
			updatePackageStatement(unit, packageChange.getNewName(), rewrite, cu);
			TextEdit textEdit = rewrite.rewriteAST();
			convertTextEdit(edit, cu, textEdit);
		}

		org.eclipse.lsp4j.ResourceChange rc = new org.eclipse.lsp4j.ResourceChange();
		IPath newPackageFragment = new Path(packageChange.getNewName().replace('.', IPath.SEPARATOR));
		IPath oldPackageFragment = new Path(packageChange.getOldName().replace('.', IPath.SEPARATOR));
		IPath newPackagePath = pack.getResource().getLocation().removeLastSegments(oldPackageFragment.segmentCount()).append(newPackageFragment);
		rc.setNewUri(ResourceUtils.fixURI(newPackagePath.toFile().toURI()));
		if (packageChange.getRenameSubpackages()) {
			rc.setCurrent(ResourceUtils.fixURI(pack.getResource().getRawLocationURI()));
			edit.getResourceChanges().add(Either.forLeft(rc));
		} else {
			edit.getResourceChanges().add(Either.forLeft(rc));
			for (ICompilationUnit unit : units) {
				org.eclipse.lsp4j.ResourceChange cuResourceChange = new org.eclipse.lsp4j.ResourceChange();
				cuResourceChange.setCurrent(ResourceUtils.fixURI(unit.getResource().getLocationURI()));
				IPath newCUPath = newPackagePath.append(unit.getPath().lastSegment());
				cuResourceChange.setNewUri(ResourceUtils.fixURI(newCUPath.toFile().toURI()));
				edit.getResourceChanges().add(Either.forLeft(cuResourceChange));
			}
		}
	}

	private static void convertCUResourceChange(WorkspaceEdit edit, RenameCompilationUnitChange cuChange) {
		ICompilationUnit modifiedCU = (ICompilationUnit) cuChange.getModifiedElement();
		org.eclipse.lsp4j.ResourceChange rc = new org.eclipse.lsp4j.ResourceChange();
		String newCUName = cuChange.getNewName();
		IPath currentPath = modifiedCU.getResource().getLocation();
		rc.setCurrent(ResourceUtils.fixURI(modifiedCU.getResource().getRawLocationURI()));
		IPath newPath = currentPath.removeLastSegments(1).append(newCUName);
		rc.setNewUri(ResourceUtils.fixURI(newPath.toFile().toURI()));
		edit.getResourceChanges().add(Either.forLeft(rc));
	}

	private static void convertTextChange(WorkspaceEdit root, IJavaElement element, TextChange textChange) {
		TextEdit textEdits = textChange.getEdit();
		if (textEdits == null) {
			return;
		}
		ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		convertTextEdit(root, compilationUnit, textEdits);
	}

	private static void convertTextEdit(WorkspaceEdit root, ICompilationUnit unit, TextEdit textEdits) {
		TextEdit[] children = textEdits.getChildren();
		if (children.length == 0) {
			return;
		}
		for (TextEdit textEdit : children) {
			TextEditConverter converter = new TextEditConverter(unit, textEdit);
			String uri = JDTUtils.toURI(unit);
			if (JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceEditResourceChangesSupported()) {
				List<Either<org.eclipse.lsp4j.ResourceChange, TextDocumentEdit>> changes = root.getResourceChanges();
				if (changes == null) {
					changes = new LinkedList<>();
					root.setResourceChanges(changes);
				}
				changes.add(Either.forRight(converter.convertToTextDocumentEdit(0)));
			} else {
				Map<String, List<org.eclipse.lsp4j.TextEdit>> changes = root.getChanges();
				if (changes.containsKey(uri)) {
					changes.get(uri).addAll(converter.convert());
				} else {
					changes.put(uri, converter.convert());
				}
			}
		}
	}

	private static ICompilationUnit getNewCompilationUnit(IType type, String newName) {
		ICompilationUnit cu = type.getCompilationUnit();
		if (isPrimaryType(type)) {
			IPackageFragment parent = type.getPackageFragment();
			String renamedCUName = JavaModelUtil.getRenamedCUName(cu, newName);
			return parent.getCompilationUnit(renamedCUName);
		} else {
			return cu;
		}
	}

	private static boolean isPrimaryType(IType type) {
		String cuName = type.getCompilationUnit().getElementName();
		String typeName = type.getElementName();
		return type.getDeclaringType() == null && JavaCore.removeJavaLikeExtension(cuName).equals(typeName);
	}

	private static void updatePackageStatement(CompilationUnit astCU, String pkgName, ASTRewrite rewriter, ICompilationUnit cu) throws JavaModelException {
		boolean defaultPackage = pkgName.isEmpty();
		AST ast = astCU.getAST();
		if (defaultPackage) {
			// remove existing package statement
			PackageDeclaration pkg = astCU.getPackage();
			if (pkg != null) {
				int pkgStart;
				Javadoc javadoc = pkg.getJavadoc();
				if (javadoc != null) {
					pkgStart = javadoc.getStartPosition() + javadoc.getLength() + 1;
				} else {
					pkgStart = pkg.getStartPosition();
				}
				int extendedStart = astCU.getExtendedStartPosition(pkg);
				if (pkgStart != extendedStart) {
					String commentSource = cu.getSource().substring(extendedStart, pkgStart);
					ASTNode comment = rewriter.createStringPlaceholder(commentSource, ASTNode.PACKAGE_DECLARATION);
					rewriter.set(astCU, CompilationUnit.PACKAGE_PROPERTY, comment, null);
				} else {
					rewriter.set(astCU, CompilationUnit.PACKAGE_PROPERTY, null, null);
				}
			}
		} else {
			org.eclipse.jdt.core.dom.PackageDeclaration pkg = astCU.getPackage();
			if (pkg != null) {
				// rename package statement
				Name name = ast.newName(pkgName);
				rewriter.set(pkg, PackageDeclaration.NAME_PROPERTY, name, null);
			} else {
				// create new package statement
				pkg = ast.newPackageDeclaration();
				pkg.setName(ast.newName(pkgName));
				rewriter.set(astCU, CompilationUnit.PACKAGE_PROPERTY, pkg, null);
			}
		}
	}

}
