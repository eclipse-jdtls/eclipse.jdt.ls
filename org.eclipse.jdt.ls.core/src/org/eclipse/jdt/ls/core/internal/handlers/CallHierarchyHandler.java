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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.ICompilationUnit.NO_AST;
import static org.eclipse.jdt.core.IJavaElement.CLASS_FILE;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.ls.core.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.ls.core.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.lsp4j.CallHierarchyDirection;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handler for the {@link TextDocumentService#callHierarchy(CallHierarchyParams)
 * <code>textDocument/callHierarchy</code>} language server method.
 */
public class CallHierarchyHandler {

	protected static ThreadLocal<CallHierarchyItem> THREAD_LOCAL = new ThreadLocal<>();

	public CallHierarchyItem callHierarchy(CallHierarchyParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");
		Assert.isLegal(params.getResolve() >= 0, "'resolve' must a non-negative integer. Was: " + params.getResolve());

		CallHierarchyDirection direction = params.getDirection() == null ? CallHierarchyDirection.Incoming : params.getDirection();
		String uri = params.getTextDocument().getUri();
		int line = params.getPosition().getLine();
		int character = params.getPosition().getCharacter();
		int resolve = params.getResolve();

		try {
			return getCallHierarchyItemAt(uri, line, character, resolve, direction, monitor);
		} catch (OperationCanceledException e) {
			return THREAD_LOCAL.get();
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		} finally {
			if (THREAD_LOCAL.get() != null) {
				THREAD_LOCAL.set(null);
			}
		}
		return null;
	}

	private CallHierarchyItem getCallHierarchyItemAt(String uri, int line, int character, int resolve, CallHierarchyDirection direction, IProgressMonitor monitor) throws JavaModelException {
		Assert.isNotNull(uri, "uri");
		Assert.isLegal(resolve >= 0, "Expected a non negative integer for 'resolve'. Was: " + resolve);
		Assert.isNotNull(direction, "direction");

		ITypeRoot root = JDTUtils.resolveTypeRoot(uri);
		if (root == null) {
			return null;
		}

		if (root instanceof ICompilationUnit) {
			ICompilationUnit unit = (ICompilationUnit) root;
			if (root.getResource() == null) {
				return null;
			}
			reconcile(unit, monitor);
		}

		checkMonitor(monitor);

		// Try to locate the member at the given position.
		IMember candidate = null;
		int offset = JsonRpcHelpers.toOffset(root, line, character);
		List<IJavaElement> selectedElements = codeResolve(root, offset);
		Stream<IJavaElement> possibleElements = selectedElements.stream().filter(CallHierarchy::isPossibleInputElement);
		Optional<IJavaElement> firstElement = possibleElements.findFirst();
		if (firstElement.isPresent() && firstElement.get() instanceof IMember) {
			candidate = (IMember) firstElement.get();
		}

		// If the member cannot be resolved, retrieve the enclosing method.
		if (candidate == null) {
			candidate = getEnclosingMember(root, offset);
			if (candidate == null) {
				return null;
			}
		}

		checkMonitor(monitor);

		MethodWrapper wrapper = toMethodWrapper(candidate, direction);
		if (wrapper == null) {
			return null;
		}

		return toCallHierarchyItem(wrapper, resolve, monitor);
	}

	private List<IJavaElement> codeResolve(IJavaElement input, int offset) throws JavaModelException {
		if (input instanceof ICodeAssist) {
			return Arrays.asList(((ICodeAssist) input).codeSelect(offset, 0));
		}
		return emptyList();
	}

	private MethodWrapper toMethodWrapper(IMember member, CallHierarchyDirection direction) {
		Assert.isNotNull(member, "member");

		IMember[] members = { member };
		CallHierarchy callHierarchy = CallHierarchy.getDefault();
		final MethodWrapper[] result;
		if (direction == CallHierarchyDirection.Incoming) {
			result = callHierarchy.getCallerRoots(members);
		} else {
			result = callHierarchy.getCalleeRoots(members);
		}
		if (result == null || result.length < 1) {
			return null;
		}
		return result[0];
	}

	private CallHierarchyItem toCallHierarchyItem(MethodWrapper wrapper, int resolve, IProgressMonitor monitor) {
		checkMonitor(monitor);

		if (wrapper == null || wrapper.getMember() == null) {
			return null;
		}

		try {
			IMember member = wrapper.getMember();
			Location fullLocation = getLocation(member, LocationType.FULL_RANGE);
			Range range = fullLocation.getRange();
			String uri = fullLocation.getUri();
			CallHierarchyItem item = new CallHierarchyItem();
			item.setName(JDTUtils.getName(member));
			item.setKind(JDTUtils.getSymbolKind(member));
			item.setRange(range);
			item.setSelectionRange(getLocation(member, LocationType.NAME_RANGE).getRange());
			item.setUri(uri);
			item.setDetail(JDTUtils.getDetail(member));
			item.setDeprecated(JDTUtils.isDeprecated(member));

			Collection<CallLocation> callLocations = wrapper.getMethodCall().getCallLocations();
			// The `callLocations` should be `null` for the root item.
			if (callLocations != null) {
				item.setCallLocations(callLocations.stream().map(location -> {
					IOpenable openable = location.getMember().getCompilationUnit();
					if (openable == null) {
						openable = location.getMember().getTypeRoot();
					}
					int[] start = JsonRpcHelpers.toLine(openable, location.getStart());
					int[] end = JsonRpcHelpers.toLine(openable, location.getEnd());
					Assert.isNotNull(start, "start");
					Assert.isNotNull(end, "end");
					Assert.isLegal(start[0] == end[0], "Expected equal start and end lines. Start was: " + Arrays.toString(start) + " End was:" + Arrays.toString(end));
					Range callRange = new Range(new Position(start[0], start[1]), new Position(end[0], end[1]));
					return new Location(uri, callRange);
				}).collect(toList()));
			}

			// Set the copy of the unresolved item to the thread local, so that we can return with it in case of user abort. (`monitor#cancel()`)
			if (THREAD_LOCAL.get() == null) {
				THREAD_LOCAL.set(shallowCopy(item));
			}

			if (resolve > 0) {
				item.setCalls(Arrays.stream(wrapper.getCalls(monitor)).map(w -> toCallHierarchyItem(w, resolve - 1, monitor)).collect(toList()));
			}
			return item;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error when converting to a call hierarchy item.", e);
		}
		return null;
	}

	/**
	 * Throws an {@link OperationCanceledException} if the argument is canceled.
	 */
	private void checkMonitor(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	/**
	 * Returns with a copy of the argument without the
	 * {@link CallHierarchyItem#getCalls() calls} set.
	 */
	private CallHierarchyItem shallowCopy(CallHierarchyItem other) {
		CallHierarchyItem copy = new CallHierarchyItem();
		copy.setName(other.getName());
		copy.setDetail(other.getDetail());
		copy.setKind(other.getKind());
		copy.setDeprecated(other.getDeprecated());
		copy.setUri(other.getUri());
		copy.setRange(other.getRange());
		copy.setSelectionRange(other.getSelectionRange());
		copy.setCallLocations(other.getCallLocations());
		return copy;
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
				location = locationType.toLocation(type);
			}
		}
		if (location == null && element instanceof IMember && ((IMember) element).getClassFile() != null) {
			location = JDTUtils.toLocation(((IMember) element).getClassFile());
		}
		return location;
	}

	private IMember getEnclosingMember(ITypeRoot root, int offset) {
		Assert.isNotNull(root, "root");
		try {
			IJavaElement enclosingElement = root.getElementAt(offset);
			if (enclosingElement instanceof IMethod || enclosingElement instanceof IInitializer || enclosingElement instanceof IField) {
				// opening on the enclosing type would be too confusing (since the type resolves to the constructors)
				return (IMember) enclosingElement;
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		}
		return null;
	}

	private void reconcile(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		unit.reconcile(NO_AST, false, null, monitor);
	}

}
