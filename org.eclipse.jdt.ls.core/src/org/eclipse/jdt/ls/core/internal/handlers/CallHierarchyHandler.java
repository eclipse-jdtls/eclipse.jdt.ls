/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.Collections.emptyList;
import static org.eclipse.jdt.core.ICompilationUnit.NO_AST;
import static org.eclipse.jdt.core.IJavaElement.CLASS_FILE;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
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
import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchyCore;
import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolTag;

public class CallHierarchyHandler {
	private static Map<IJavaElement, MethodWrapper> incomingMethodWrapperCache = new ConcurrentHashMap<>();
	private static Map<IJavaElement, MethodWrapper> outgoingMethodWrapperCache = new ConcurrentHashMap<>();

	public List<CallHierarchyItem> prepareCallHierarchy(CallHierarchyPrepareParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");

		// trigger call hierarchy at a new position, clean the method wrapper cache.
		incomingMethodWrapperCache.clear();
		outgoingMethodWrapperCache.clear();

		String uri = params.getTextDocument().getUri();
		int line = params.getPosition().getLine();
		int character = params.getPosition().getCharacter();

		try {
			IMember candidate = getCallHierarchyElement(uri, line, character, monitor);
			if (candidate == null) {
				return null;
			}
			checkMonitor(monitor);
			return Arrays.asList(toCallHierarchyItem(candidate));
		} catch (OperationCanceledException e) {
			// do nothing
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return null;
	}

	public List<CallHierarchyIncomingCall> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");

		CallHierarchyItem item = params.getItem();
		Assert.isNotNull(item, "call item");
		Position position;
		switch (item.getKind()) {
			case Class:
			case Enum:
			case Interface:
				position = item.getSelectionRange().getStart();
				break;
			default:
				position = item.getRange().getStart();
				break;
		}
		int line = position.getLine();
		int character = position.getCharacter();

		try {
			return getIncomingCallItemsAt(item.getUri(), line, character, monitor);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		} catch (OperationCanceledException e) {
			// do nothing
		}

		return null;
	}

	public List<CallHierarchyOutgoingCall> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");

		CallHierarchyItem item = params.getItem();
		Assert.isNotNull(item, "call item");
		Position position = item.getRange().getStart();
		int line = position.getLine();
		int character = position.getCharacter();

		try {
			return getOutgoingCallItemsAt(item.getUri(), line, character, monitor);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		} catch (OperationCanceledException e) {
			// do nothing
		}

		return null;
	}

	private IMember getCallHierarchyElement(String uri, int line, int character, IProgressMonitor monitor) throws JavaModelException {
		Assert.isNotNull(uri, "uri");

		ITypeRoot root = JDTUtils.resolveTypeRoot(uri);
		if (root == null) {
			return null;
		}

		if (root instanceof ICompilationUnit unit) {
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
		Stream<IJavaElement> possibleElements = selectedElements.stream().filter(CallHierarchyCore::isPossibleInputElement);
		Optional<IJavaElement> firstElement = possibleElements.findFirst();
		if (firstElement.isPresent() && firstElement.get() instanceof IMember member) {
			candidate = member;
		}

		// If the member cannot be resolved, retrieve the enclosing method.
		if (candidate == null) {
			candidate = getEnclosingMember(root, offset);
		}

		return candidate;
	}

	private List<CallHierarchyIncomingCall> getIncomingCallItemsAt(String uri, int line, int character, IProgressMonitor monitor) throws JavaModelException {
		SubMonitor sub = SubMonitor.convert(monitor, 2);
		IMember candidate = getCallHierarchyElement(uri, line, character, sub.split(1));
		if (candidate == null) {
			return null;
		}

		checkMonitor(monitor);

		MethodWrapper wrapper = incomingMethodWrapperCache.containsKey(candidate) ?
			incomingMethodWrapperCache.get(candidate) : getCallRoot(candidate, true);
		if (wrapper == null || !wrapper.canHaveChildren()) {
			return null;
		}
		MethodWrapper[] calls = wrapper.getCalls(sub.split(1));
		if (calls == null) {
			return null;
		}

		List<CallHierarchyIncomingCall> result = new ArrayList<>();
		for (MethodWrapper call : calls) {
			Collection<CallLocation> callLocations = call.getMethodCall().getCallLocations();
			if (callLocations != null) {
				for (CallLocation location : callLocations) {
					IOpenable openable = getOpenable(location);
					Range callRange = getRange(openable, location);
					CallHierarchyItem symbol = toCallHierarchyItem(call.getMember());
					symbol.setSelectionRange(callRange);
					List<Range> ranges = toCallRanges(callLocations);
					result.add(new CallHierarchyIncomingCall(symbol, ranges));
				}
			}
			IMember member = call.getMember();
			if (member != null) {
				incomingMethodWrapperCache.put(member, call);
			}
		}

		return result;
	}

	private Range getRange(IOpenable openable, CallLocation location) {
		int[] start = JsonRpcHelpers.toLine(openable, location.getStart());
		int[] end = JsonRpcHelpers.toLine(openable, location.getEnd());
		Assert.isNotNull(start, "start");
		Assert.isNotNull(end, "end");
		// Assert.isLegal(start[0] == end[0], "Expected equal start and end lines. Start was: " + Arrays.toString(start) + " End was:" + Arrays.toString(end));
		Range callRange = new Range(new Position(start[0], start[1]), new Position(end[0], end[1]));
		return callRange;
	}

	private List<CallHierarchyOutgoingCall> getOutgoingCallItemsAt(String uri, int line, int character, IProgressMonitor monitor) throws JavaModelException {
		SubMonitor sub = SubMonitor.convert(monitor, 2);
		IMember candidate = getCallHierarchyElement(uri, line, character, sub.split(1));
		if (candidate == null) {
			return null;
		}

		checkMonitor(monitor);

		MethodWrapper wrapper = outgoingMethodWrapperCache.containsKey(candidate) ? outgoingMethodWrapperCache.get(candidate) : getCallRoot(candidate, false);
		if (wrapper == null) {
			return null;
		}

		MethodWrapper[] calls = wrapper.getCalls(sub.split(1));
		if (calls == null) {
			return null;
		}

		List<CallHierarchyOutgoingCall> result = new ArrayList<>();
		for (MethodWrapper call : calls) {
			Collection<CallLocation> callLocations = call.getMethodCall().getCallLocations();
			if (callLocations != null && !callLocations.isEmpty()) {
				List<Range> ranges = toCallRanges(callLocations);
				for (int i = 0; i < callLocations.size(); i++) {
					CallHierarchyItem symbol = toCallHierarchyItem(call.getMember());
					result.add(new CallHierarchyOutgoingCall(symbol, ranges));
				}
			}
			IMember member = call.getMember();
			if (member != null) {
				outgoingMethodWrapperCache.put(member, call);
			}
		}

		return result;
	}

	private List<IJavaElement> codeResolve(IJavaElement input, int offset) throws JavaModelException {
		if (input instanceof ICodeAssist codeAssist) {
			return Arrays.asList(codeAssist.codeSelect(offset, 0));
		}
		return emptyList();
	}

	private MethodWrapper getCallRoot(IMember member, boolean isIncomingCall) {
		Assert.isNotNull(member, "member");

		IMember[] members = { member };
		CallHierarchyCore callHierarchy = CallHierarchyCore.getDefault();
		final MethodWrapper[] result;
		if (isIncomingCall) {
			result = callHierarchy.getCallerRoots(members);
		} else {
			result = callHierarchy.getCalleeRoots(members);
		}
		if (result == null || result.length < 1) {
			return null;
		}
		return result[0];
	}

	private CallHierarchyItem toCallHierarchyItem(IMember member) throws JavaModelException {
		Location fullLocation = getLocation(member, LocationType.FULL_RANGE);
		Range range = fullLocation.getRange();
		String uri = fullLocation.getUri();
		CallHierarchyItem item = new CallHierarchyItem();
		item.setName(JDTUtils.getName(member));
		item.setKind(DocumentSymbolHandler.mapKind(member));
		item.setRange(range);
		item.setSelectionRange(getLocation(member, LocationType.NAME_RANGE).getRange());
		item.setUri(uri);
		IType declaringType = member.getDeclaringType();
		item.setDetail(declaringType == null ? null : declaringType.getFullyQualifiedName());
		if (JDTUtils.isDeprecated(member)) {
			item.setTags(Arrays.asList(SymbolTag.Deprecated));
		}

		return item;
	}

	private List<Range> toCallRanges(Collection<CallLocation> callLocations) {
		List<Range> ranges = new ArrayList<>();
		if (callLocations != null) {
			for (CallLocation location : callLocations) {
				IOpenable openable = getOpenable(location);
				Range callRange = getRange(openable, location);
				ranges.add(callRange);
			}
		}

		return ranges;
	}

	private IOpenable getOpenable(CallLocation location) {
		IOpenable openable = location.getMember().getCompilationUnit();
		if (openable == null) {
			openable = location.getMember().getTypeRoot();
		}
		return openable;
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
	 * Gets the location of the Java {@code element} based on the desired
	 * {@code locationType}.
	 */
	private Location getLocation(IJavaElement element, LocationType locationType) throws JavaModelException {
		Assert.isNotNull(element, "element");
		Assert.isNotNull(locationType, "locationType");

		Location location = locationType.toLocation(element);
		if (location == null && element instanceof IType type) {
			ICompilationUnit unit = (ICompilationUnit) type.getAncestor(COMPILATION_UNIT);
			IClassFile classFile = (IClassFile) type.getAncestor(CLASS_FILE);
			if (unit != null || (classFile != null && classFile.getSourceRange() != null)) {
				location = locationType.toLocation(type);
			}
		}
		if (location == null && element instanceof IMember member && member.getClassFile() != null) {
			location = JDTUtils.toLocation(member.getClassFile());
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
