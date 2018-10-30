/*******************************************************************************
 * Copyright (c) 2018 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.HighlightedPositionCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.lsp4j.SemanticHighlightingInformation;
import org.eclipse.lsp4j.SemanticHighlightingParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.util.SemanticHighlightingTokens;

import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * A stateful service for installing, un-installing, and updating semantic
 * highlighting information on the text documents. Behaves as a NOOP if the
 * semantic highlighting support is disabled on the language client.
 */
public class SemanticHighlightingService {

	private static Builder<Integer, List<String>> BUILDER = ImmutableBiMap.builder();
	static {
		//@formatter:off
		final AtomicInteger i = new AtomicInteger();
		Stream.of(SemanticHighlightings.getSemanticHighlightings())
			.map(SemanticHighlightingLS::getScopes)
			.forEach(scopes -> BUILDER.put(i.getAndIncrement(), scopes));
		//@formatter:on
	}
	/**
	 * Lookup table for the scopes.
	 */
	private static BiMap<Integer, List<String>> LOOKUP_TABLE = BUILDER.build();

	/**
	 * Returns with a view of all scopes supported by the LS.
	 */
	public static List<List<String>> getAllScopes() {
		List<List<String>> result = newArrayList();
		for (int i = 0; i < LOOKUP_TABLE.keySet().size(); i++) {
			List<String> scopes = LOOKUP_TABLE.get(i);
			Assert.isNotNull(scopes, "No scopes are available for index: " + i);
			result.add(scopes);
		}
		return ImmutableList.copyOf(result);
	}

	/**
	 * Returns with the scopes for the given scope index. Never {@code null} nor
	 * empty. If not found, throws an exception.
	 */
	public static List<String> getScopes(int index) {
		List<String> scopes = LOOKUP_TABLE.get(index);
		Assert.isNotNull(scopes, "No scopes were registered for index: " + index);
		return scopes;
	}

	/**
	 * Returns with the scope for the index. If not found, throws an exception.
	 */
	public static int getIndex(List<String> scopes) {
		Integer index = LOOKUP_TABLE.inverse().get(scopes);
		Assert.isNotNull(index, "Cannot get index for scopes: " + Iterables.toString(scopes));
		return index;
	}

	/**
	 * Wraps contextual information about highlighted position changes. Although the
	 * instances are immutable, the references are not.
	 */
	public static class HighlightedPositionDiffContext {

		//@formatter:off
		public final IDocument oldState;
		public final IDocument newState;
		public final DocumentEvent event;
		public final List<HighlightedPositionCore> oldPositions;
		public final List<HighlightedPositionCore> newPositions;

		public HighlightedPositionDiffContext(
				IDocument oldState,
				DocumentEvent event,
				Iterable<? extends HighlightedPositionCore> oldPositions,
				Iterable<? extends HighlightedPositionCore> newPositions) {

			this.oldState = oldState;
			this.newState = event.fDocument;
			this.event = event;
			this.oldPositions = ImmutableList.copyOf(oldPositions);
			this.newPositions = ImmutableList.copyOf(newPositions);
		}
		//@formatter:on

	}

	private final Supplier<Boolean> enabled;
	private final JavaClientConnection connection;
	private final Map<String, List<HighlightedPositionCore>> cache;
	private CoreASTProvider astProvider;
	private SemanticHighlightingDiffCalculator diffCalculator;

	public SemanticHighlightingService(JavaClientConnection connection, CoreASTProvider astProvider, PreferenceManager preferenceManager) {
		this(connection, astProvider, memoize(() -> preferenceManager.getClientPreferences().isSemanticHighlightingSupported()));
	}

	public SemanticHighlightingService(JavaClientConnection connection, CoreASTProvider astProvider, Supplier<Boolean> enabled) {
		this.connection = connection;
		this.astProvider = astProvider;
		this.enabled = enabled; // XXX: move this out and have a factory instead, that creates a NOOP service instance.
		this.cache = newHashMap();
		this.diffCalculator = new SemanticHighlightingDiffCalculator();
	}

	public boolean isEnabled() {
		return enabled.get();
	}

	public void uninstall(String uri) {
		if (enabled.get()) {
			this.cache.remove(uri);
		}
	}

	public List<Position> install(ICompilationUnit unit) throws JavaModelException, BadPositionCategoryException {
		if (enabled.get()) {
			List<HighlightedPositionCore> positions = calculateHighlightedPositions(unit, false);
			String uri = JDTUtils.getFileURI(unit.getResource());
			this.cache.put(uri, positions);
			if (!positions.isEmpty()) {
				IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
				List<SemanticHighlightingInformation> infos = toInfos(document, positions);
				VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier(uri, 1);
				notifyClient(textDocument, infos);
			}
			return ImmutableList.copyOf(positions);
		}
		return emptyList();
	}

	public List<HighlightedPositionCore> calculateHighlightedPositions(ICompilationUnit unit, boolean cache) throws JavaModelException, BadPositionCategoryException {
		if (enabled.get()) {
			IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
			ASTNode ast = getASTNode(unit);
			List<HighlightedPositionCore> positions = calculateHighlightedPositions(document, ast);
			if (cache) {
				String uri = JDTUtils.getFileURI(unit.getResource());
				this.cache.put(uri, positions);
			}
			return ImmutableList.copyOf(positions);
		}
		return emptyList();
	}

	public List<HighlightedPositionCore> getHighlightedPositions(String uri) {
		return ImmutableList.copyOf(cache.getOrDefault(uri, emptyList()));
	}

	public void update(VersionedTextDocumentIdentifier textDocument, List<HighlightedPositionDiffContext> diffContexts) throws BadLocationException, BadPositionCategoryException, JavaModelException {
		if (enabled.get() && !diffContexts.isEmpty()) {
			List<SemanticHighlightingInformation> deltaInfos = newArrayList();
			for (HighlightedPositionDiffContext context : diffContexts) {
				deltaInfos.addAll(diffCalculator.getDiffInfos(context));
			}
			if (!deltaInfos.isEmpty()) {
				notifyClient(textDocument, deltaInfos);
			}
		}
	}

	protected List<HighlightedPositionCore> calculateHighlightedPositions(IDocument document, ASTNode ast) throws BadPositionCategoryException {
		return new SemanticHighlightingReconciler().reconciled(document, ast, false, new NullProgressMonitor());
	}

	protected ASTNode getASTNode(ICompilationUnit unit) {
		// TODO: This seems odd here.
		// I had problems when opened the second compilation unit in the editor.
		// It was still using the previous AST.
		this.astProvider.disposeAST();
		return this.astProvider.getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
	}

	protected List<SemanticHighlightingInformation> toInfos(IDocument document, List<HighlightedPositionCore> positions) {
		Multimap<Integer, SemanticHighlightingTokens.Token> infos = HashMultimap.create();
		for (HighlightedPositionCore position : positions) {
			int[] lineAndColumn = JsonRpcHelpers.toLine(document, position.offset);
			if (lineAndColumn == null) {
				JavaLanguageServerPlugin.logError("Cannot locate line and column information for the semantic highlighting position: " + position + ". Skipping it.");
				continue;
			}
			int line = lineAndColumn[0];
			int character = lineAndColumn[1];
			int length = position.length;
			int scope = LOOKUP_TABLE.inverse().get(position.getHighlighting());
			infos.put(line, new SemanticHighlightingTokens.Token(character, length, scope));
		}
		//@formatter:off
		return infos.asMap().entrySet().stream()
			.map(entry -> new SemanticHighlightingInformation(entry.getKey(), SemanticHighlightingTokens.encode(entry.getValue())))
			.collect(Collectors.toList());
		//@formatter:on
	}

	protected void notifyClient(VersionedTextDocumentIdentifier textDocument, List<SemanticHighlightingInformation> infos) {
		if (infos.isEmpty()) {
			return;
		}
		this.connection.semanticHighlighting(new SemanticHighlightingParams(textDocument, infos));
	}

}
