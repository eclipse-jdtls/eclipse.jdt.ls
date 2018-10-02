/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.ICompilationUnit.NO_AST;
import static org.eclipse.jdt.core.IJavaElement.CLASS_FILE;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;
import static org.eclipse.jdt.core.IJavaElement.METHOD;
import static org.eclipse.jdt.core.IJavaElement.TYPE;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ALL_DEFAULT;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.DEFAULT_QUALIFIED;
import static org.eclipse.lsp4j.TypeHierarchyDirection.Both;
import static org.eclipse.lsp4j.TypeHierarchyDirection.Children;
import static org.eclipse.lsp4j.TypeHierarchyDirection.Parents;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TypeHierarchyDirection;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyParams;

import com.google.common.collect.ImmutableMap;

/**
 * Handler for serving the {@code textDocument/typeHierarchy} method. Retrieves
 * type hierarchy items based on the cursor location in the text document. On
 * demand, can resolve sub- and supertypes.
 *
 */
public class TypeHierarchyHandler {

	//@formatter:off
	protected static Map<Integer, Function<IJavaElement, IType>> SUPPORTED_TYPES = ImmutableMap.<Integer, Function<IJavaElement, IType>>of(
			TYPE, type -> (IType) type,
			METHOD, method -> ((IMethod) method).getDeclaringType()
	);
	//@formatter:on

	protected PreferenceManager preferenceManager;

	public TypeHierarchyHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public TypeHierarchyItem typeHierarchy(TypeHierarchyParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");
		Assert.isNotNull(params.getPosition(), "params.position");
		Assert.isNotNull(params.getTextDocument(), "params.textDocument");

		String uri = params.getTextDocument().getUri();
		Position position = params.getPosition();
		TypeHierarchyDirection direction = params.getDirection() == null ? Children : params.getDirection();
		int resolve = params.getResolve();
		return getTypeHierarchy(uri, position, direction, resolve, monitor);
	}

	protected TypeHierarchyItem getTypeHierarchy(String uri, Position position, TypeHierarchyDirection direction, int resolve, IProgressMonitor monitor) {
		Assert.isNotNull(uri, "uri");
		Assert.isNotNull(position, "position");
		Assert.isLegal(resolve >= 0, "'resolve' must be a non-negative integer. Was: " + resolve);

		try {
			final Pair<TypeHierarchyItem, IType> toResolve = getTypeHierarchyItem(uri, position, monitor);
			if (toResolve == null || toResolve.getLeft() == null) {
				return null;
			}

			if (monitor.isCanceled()) {
				return toResolve.getKey();
			}

			TypeHierarchyItem copy = shallowCopy(toResolve.getKey());
			try {
				if (direction == Children || direction == Both) {
					int depth = resolve;
					resolveChildren(Collections.singletonList(toResolve), depth, monitor);
				}
				if (direction == Parents || direction == Both) {
					int depth = resolve;
					resolveParents(Collections.singletonList(toResolve), depth, monitor);
				}
			} catch (OperationCanceledException e) {
				return copy;
			}

			return toResolve.getLeft();
		} catch (JavaModelException e) {
			logException("Error when retrieving the type hierarchy in " + uri + " at " + position + ".", e);
			return null;
		}
	}

	protected void resolveChildren(Collection<Pair<TypeHierarchyItem, IType>> pairs, int depth, IProgressMonitor monitor) throws JavaModelException {
		if (depth > 0) {
			for (Pair<TypeHierarchyItem, IType> pair : pairs) {
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				IType type = pair.getRight();
				ITypeHierarchy typeHierarchy = type.newTypeHierarchy(monitor);
				List<Pair<TypeHierarchyItem, IType>> subtypes = Arrays.stream(typeHierarchy.getSubtypes(type)).map(t -> toTypeHierarchyItem(t)).collect(toList());
				pair.getLeft().setChildren(subtypes.stream().map(p -> p.getLeft()).collect(toList()));
				resolveChildren(subtypes, depth--, monitor);
			}
		}
	}

	protected void resolveParents(Collection<Pair<TypeHierarchyItem, IType>> pairs, int depth, IProgressMonitor monitor) throws JavaModelException {
		if (depth > 0) {
			for (Pair<TypeHierarchyItem, IType> pair : pairs) {
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				IType type = pair.getRight();
				ITypeHierarchy typeHierarchy = type.newSupertypeHierarchy(monitor);
				List<Pair<TypeHierarchyItem, IType>> supertypes = Arrays.stream(typeHierarchy.getSupertypes(type)).map(t -> toTypeHierarchyItem(t)).collect(toList());
				pair.getLeft().setParents(supertypes.stream().map(p -> p.getLeft()).collect(toList()));
				resolveParents(supertypes, depth--, monitor);
			}
		}
	}

	/**
	 * Returns with a copy of the argument without the {@code parents} and
	 * {@code children}.
	 */
	private TypeHierarchyItem shallowCopy(TypeHierarchyItem other) {
		TypeHierarchyItem copy = new TypeHierarchyItem();
		copy.setName(other.getName());
		copy.setDetail(other.getDetail());
		copy.setKind(other.getKind());
		copy.setDeprecated(other.getDeprecated());
		copy.setUri(other.getUri());
		copy.setRange(other.getRange());
		copy.setSelectionRange(other.getSelectionRange());
		return copy;
	}

	private Pair<TypeHierarchyItem, IType> getTypeHierarchyItem(String uri, Position position, IProgressMonitor monitor) throws JavaModelException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 3);
		ITypeRoot root = JDTUtils.resolveTypeRoot(uri);
		if (root == null) {
			return null;
		}
		if (root instanceof ICompilationUnit) {
			ICompilationUnit unit = (ICompilationUnit) root;
			if (root.getResource() == null) {
				return null;
			}
			reconcile(unit, subMonitor.newChild(1));
		}

		int line = position.getLine();
		int character = position.getCharacter();
		IJavaElement selectedElement = JDTUtils.findElementAtSelection(root, line, character, preferenceManager, subMonitor.newChild(1));
		if (!isSupportedType(selectedElement)) {
			selectedElement = getFallbackElement(root, selectedElement);
			if (!isSupportedType(selectedElement)) {
				return null;
			}
		}

		IType selectedType = getType(selectedElement);
		if (selectedType != null) {
			return toTypeHierarchyItem(selectedType);
		}
		return null;
	}

	/**
	 * If the {@link IJavaElement selectedElement} argument is <b>not</b>
	 * {@link #isSupportedType(IJavaElement) supported}, it returns with the primary
	 * type from the {@code root} argument. Otherwise, returns the
	 * {@code selectedElement} argument.
	 */
	private IJavaElement getFallbackElement(ITypeRoot root, IJavaElement selectedElement) {
		if (!isSupportedType(selectedElement)) {
			int rootType = root.getElementType();
			if (rootType == CLASS_FILE && root instanceof IOrdinaryClassFile) {
				selectedElement = ((IOrdinaryClassFile) root).getType();
			} else if (rootType == COMPILATION_UNIT) {
				selectedElement = ((ICompilationUnit) root).findPrimaryType();
			}
		}
		return isSupportedType(selectedElement) ? selectedElement : null;
	}

	private void reconcile(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		unit.reconcile(NO_AST, false, null, monitor);
	}

	/**
	 * {@code true} if the {@code element} argument is not {@code null} and its
	 * {@link IJavaElement#getElementType() type} is covered by this handler.
	 */
	private boolean isSupportedType(IJavaElement element) {
		return element != null && SUPPORTED_TYPES.keySet().contains(element.getElementType());
	}

	/**
	 * Returns the {@link IType type} from the {@link IJavaElement element} argument
	 * if it is supported. Otherwise, returns {@code null}.
	 */
	private IType getType(IJavaElement element) {
		return SUPPORTED_TYPES.getOrDefault(element.getElementType(), type -> null).apply(element);
	}

	/**
	 * Maps the type argument into the corresponding LSP specific type hierarchy
	 * item. Returns with {@code null} if the type is not supported. The
	 * {@link TypeHierarchyItem#getChildren() children} and
	 * {@link TypeHierarchyItem#getParents() parents} will be left as {@code null}.
	 */
	private Pair<TypeHierarchyItem, IType> toTypeHierarchyItem(IType type) {
		if (!isSupportedType(type)) {
			return null;
		}
		try {
			Location fullLocation = getLocation(type, LocationType.FULL_RANGE);
			Range range = fullLocation.getRange();
			String uri = fullLocation.getUri();
			TypeHierarchyItem item = new TypeHierarchyItem();
			item.setName(JDTUtils.getName(type));
			item.setKind(JDTUtils.getSymbolKind(type));
			item.setRange(range);
			item.setSelectionRange(getLocation(type, LocationType.NAME_RANGE).getRange());
			item.setUri(uri);
			item.setDetail(getDetail(type));
			item.setDeprecated(JDTUtils.isDeprecated(type));
			return Pair.of(item, type);
		} catch (JavaModelException e) {
			logException("Error when mapping type " + type + " into a type hierarchy item symbol.", e);
		}
		return null;
	}

	/**
	 * Gets the location of the Java {@code element} based on the desired
	 * {@code locationType}.
	 */
	private Location getLocation(IJavaElement element, LocationType locationType) throws JavaModelException {
		Assert.isNotNull(element, "element");
		Assert.isNotNull(locationType, "locationType");
		Location location = locationType.toLocation(element);
		if (location == null && element instanceof IType) {
			IType type = (IType) element;
			ICompilationUnit unit = (ICompilationUnit) type.getAncestor(COMPILATION_UNIT);
			IClassFile classFile = (IClassFile) type.getAncestor(CLASS_FILE);
			if (unit != null || (classFile != null && classFile.getSourceRange() != null)) {
				return locationType.toLocation(type);
			}
			if (type instanceof IMember && ((IMember) type).getClassFile() != null) {
				return JDTUtils.toLocation(((IMember) type).getClassFile());
			}
		}
		return location;
	}

	/**
	 * The FQN of the container package of the {@code type} argument.
	 */
	private String getDetail(IType type) {
		IPackageFragment packageFragment = type.getPackageFragment();
		if (packageFragment != null) {
			String name = JavaElementLabels.getElementLabel(packageFragment, ALL_DEFAULT);
			return name == null ? packageFragment.getElementName() : name;
		}
		String fqnName = JavaElementLabels.getElementLabel(type, DEFAULT_QUALIFIED);
		if (fqnName != null) {
			String name = JDTUtils.getName(type);
			if (name != null && fqnName.endsWith(name)) {
				return fqnName.substring(0, fqnName.length() - (name.length() + 1));
			}
		}
		return null;
	}

}
