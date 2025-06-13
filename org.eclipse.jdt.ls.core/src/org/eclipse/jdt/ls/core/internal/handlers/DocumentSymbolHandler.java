/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;
import static org.eclipse.jdt.core.IJavaElement.FIELD;
import static org.eclipse.jdt.core.IJavaElement.METHOD;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_DECLARATION;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT;
import static org.eclipse.jdt.core.IJavaElement.TYPE;
import static org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore.F_POST_QUALIFIED;
import static org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore.M_POST_QUALIFIED;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.filter;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.getDetail;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.getName;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.getRange;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.getSelectionRange;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.mapKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.ls.core.internal.DecompilerResult;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ContentProviderManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.eclipse.xtext.xbase.lib.Pure;

public class DocumentSymbolHandler {
	PreferenceManager preferenceManager;

	private static IScanner fScanner;

	public DocumentSymbolHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ExtendedDocumentSymbol> extendedDocumentSymbol(DocumentSymbolParams params, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (unit == null || !unit.exists()) {
			return Collections.emptyList();
		}
		List<DocumentSymbol> symbols = getHierarchicalOutline(unit, monitor, true);
		return symbols.stream().map(s -> (ExtendedDocumentSymbol) s).toList();
	}

	public List<Either<SymbolInformation, DocumentSymbol>> documentSymbol(DocumentSymbolParams params, IProgressMonitor monitor) {

		ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (unit == null || !unit.exists()) {
			return Collections.emptyList();
		}

		if (preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()) {
			List<DocumentSymbol> symbols = this.getHierarchicalOutline(unit, monitor, false);
			return symbols.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).collect(toList());
		} else {
			SymbolInformation[] elements = this.getOutline(unit, monitor);
			return Arrays.asList(elements).stream().map(Either::<SymbolInformation, DocumentSymbol>forLeft).collect(toList());
		}
	}

	private SymbolInformation[] getOutline(ITypeRoot unit, IProgressMonitor monitor) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<SymbolInformation> symbols = new ArrayList<>(elements.length);
			collectChildren(unit, elements, symbols, monitor);
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			if (!unit.exists()) {
				JavaLanguageServerPlugin.logError("Problem getting outline for " + unit.getElementName() + ": File not found.");
			} else {
				JavaLanguageServerPlugin.logException("Problem getting outline for " + unit.getElementName(), e);
			}
		}
		return new SymbolInformation[0];
	}

	private void collectChildren(ITypeRoot unit, IJavaElement[] elements, ArrayList<SymbolInformation> symbols,
			IProgressMonitor monitor)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			if (element instanceof IParent parent) {
				collectChildren(unit, filter(parent.getChildren()), symbols, monitor);
			}
			int type = element.getElementType();
			if (type != IJavaElement.TYPE && type != IJavaElement.FIELD && type != IJavaElement.METHOD) {
				continue;
			}
			if (element instanceof SourceMethod method && JDTUtils.isGenerated(method)) {
				continue;
			}
			Location location = JDTUtils.toLocation(element);
			if (location != null) {
				SymbolInformation si = new SymbolInformation();
				String name = JavaElementLabelsCore.getElementLabel(element, JavaElementLabelsCore.ALL_DEFAULT);
				si.setName(name == null ? element.getElementName() : name);
				si.setKind(mapKind(element));
				if (JDTUtils.isDeprecated(element)) {
					if (preferenceManager.getClientPreferences().isSymbolTagSupported()) {
						si.setTags(List.of(SymbolTag.Deprecated));
					}
					else {
						si.setDeprecated(true);
					}
				}
				if (element.getParent() != null) {
					si.setContainerName(element.getParent().getElementName());
				}
				location.setUri(ResourceUtils.toClientUri(location.getUri()));
				si.setLocation(location);
				if (!symbols.contains(si)) {
					symbols.add(si);
				}
			}
		}
	}

	List<DocumentSymbol> getHierarchicalOutline(ITypeRoot unit, IProgressMonitor monitor, boolean includeInherited) {
		try {
			if (unit instanceof IClassFile && unit.getSourceRange() == null) { // no source attached
				return getHierarchicalOutlineFromDecompiledSource(unit, monitor);
			}

			IJavaElement[] children = unit.getChildren();
			Stream<IJavaElement> childrenStream = Stream.of(filter(children));
			if (unit instanceof IClassFile) {
				// Prepend Package element as the first child
				childrenStream = Stream.concat(Stream.of(unit.getParent()), childrenStream);
				ISourceRange sourceRange = unit.getSourceRange();
				if (sourceRange != null) {
					final int shift = sourceRange.getOffset();
					IScanner scanner = getScanner(unit.getJavaProject());
					scanner.setSource(unit.getSource().toCharArray());
					scanner.resetTo(shift, shift + sourceRange.getLength());
				}
			}
			Set<IJavaElement> visited = new HashSet<>(); // avoid cycles (eg. an inner class super type is parent)
			return childrenStream.map(child -> toDocumentSymbol(child, unit, monitor, includeInherited, visited)).filter(Objects::nonNull).collect(Collectors.toList());
		} catch (OperationCanceledException e) {
			logInfo("User canceled while collecting the document symbols.");
		} catch (JavaModelException e) {
			if (!unit.exists()) {
				JavaLanguageServerPlugin.logError("Problem getting outline for " + unit.getElementName() + ": File not found.");
			} else {
				JavaLanguageServerPlugin.logException("Problem getting outline for " + unit.getElementName(), e);
			}
		}
		return emptyList();
	}

	private DocumentSymbol toDocumentSymbol(IJavaElement unit, ITypeRoot root, IProgressMonitor monitor, boolean includeInherited, Set<IJavaElement> visited) {
		visited.add(unit);
		int type = unit.getElementType();
		if (type != TYPE && type != FIELD && type != METHOD && type != PACKAGE_DECLARATION && type != COMPILATION_UNIT && type != PACKAGE_FRAGMENT) {
			return null;
		}
		if (unit instanceof SourceMethod method && JDTUtils.isGenerated(method)) {
			return null;
		}
		if (monitor.isCanceled()) {
			throw new OperationCanceledException("User canceled");
		}
		DocumentSymbol symbol = includeInherited ? new ExtendedDocumentSymbol() : new DocumentSymbol();
		try {
			String name = getName(unit);
			symbol.setName(name);
			if (type == PACKAGE_FRAGMENT) {
				IScanner scanner = getScanner(root.getJavaProject());
				int token = 0;
				int packageStart = -1;
				int packageEnd = -1;
				while (token != ITerminalSymbols.TokenNameEOF) {
					switch (token) {
						case ITerminalSymbols.TokenNamepackage:
							packageStart = scanner.getCurrentTokenStartPosition();
							packageEnd = scanner.getCurrentTokenEndPosition();
						default:
							break;
					}
					token = getNextToken(scanner);
				}
				Range packageRange = JDTUtils.toRange(root, packageStart, packageEnd);
				symbol.setRange(packageRange);
				symbol.setSelectionRange(packageRange);
			} else {
				symbol.setRange(getRange(unit));
				symbol.setSelectionRange(getSelectionRange(unit));
			}
			symbol.setKind(mapKind(unit));
			if (JDTUtils.isDeprecated(unit)) {
				if (preferenceManager.getClientPreferences().isSymbolTagSupported()) {
					symbol.setTags(List.of(SymbolTag.Deprecated));
				}
				else {
					symbol.setDeprecated(true);
				}
			}
			if (symbol instanceof ExtendedDocumentSymbol) {
				symbol.setDetail(getDetail(unit, name, M_POST_QUALIFIED | F_POST_QUALIFIED));
			} else {
				symbol.setDetail(getDetail(unit, name));
			}
			if (unit instanceof IParent parent) {
				List<IJavaElement> children = new ArrayList<>();
				children.addAll(Arrays.asList(filter(parent.getChildren())));
				if (symbol instanceof ExtendedDocumentSymbol) {
					Location loc = JDTUtils.toLocation(unit);
					if (loc != null) {
						((ExtendedDocumentSymbol) symbol).setUri(loc.getUri());
					}
					if (unit instanceof IType tp) {
						ITypeHierarchy supertypeHierarchy = tp.newSupertypeHierarchy(monitor);
						if (monitor.isCanceled()) {
							throw new OperationCanceledException();
						}
						IType[] superTypes = supertypeHierarchy.getAllSupertypes(tp);
						for (IType superType : superTypes) {
							if (!"java.lang.Object".equals(superType.getFullyQualifiedName())) {
								List<IJavaElement> superTypeChildren = Arrays.asList(filter(superType.getChildren())).stream().filter(c -> !visited.contains(c)).toList();
								children.addAll(superTypeChildren);
							}
						}
					}
				}
				if (!children.isEmpty()) {
					//@formatter:off
					symbol.setChildren(children.stream()
							.map(child -> toDocumentSymbol(child, null, monitor, includeInherited, visited))
							.filter(Objects::nonNull)
							.collect(Collectors.toList()));
					//@formatter:off
				}
			}
		} catch (JavaModelException e) {
			Exceptions.sneakyThrow(e);
		}
		return symbol;
	}

	private static IScanner getScanner(IJavaProject project) {
		if (fScanner == null) {
			fScanner = JDTUtils.createScanner(project, true, false, false);
		}
		return fScanner;
	}

	private int getNextToken(IScanner scanner) {
		int token = 0;
		while (token == 0) {
			try {
				token = scanner.getNextToken();
			} catch (InvalidInputException e) {
				// ignore
				// JavaLanguageServerPlugin.logException("Problem with folding range", e);
			}
		}
		return token;
	}

	private List<DocumentSymbol> getHierarchicalOutlineFromDecompiledSource(ITypeRoot unit, IProgressMonitor monitor) {
		ContentProviderManager contentProvider = JavaLanguageServerPlugin.getContentProviderManager();
		DecompilerResult decompileResult;
		try {
			decompileResult = contentProvider.getSourceResult(((IClassFile) unit), new NullProgressMonitor());
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return Collections.emptyList();
		}

		if (monitor != null && monitor.isCanceled()) {
			return Collections.emptyList();
		}

		String contents = decompileResult.getContent();
		if (contents == null || contents.isBlank()) {
			return Collections.emptyList();
		}

		final ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setResolveBindings(false);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(false);
		parser.setIgnoreMethodBodies(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		/**
		 * See the java doc for { @link ASTParser#setSource(char[]) },
		 * the user need specify the compiler options explicitly.
		 */
		Map<String, String> javaOptions = JavaCore.getOptions();
		javaOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.latestSupportedJavaVersion());
		javaOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.latestSupportedJavaVersion());
		javaOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.latestSupportedJavaVersion());
		javaOptions.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		parser.setCompilerOptions(javaOptions);
		parser.setUnitName(unit.getElementName());
		parser.setSource(contents.toCharArray());
		CompilationUnit astUnit = (CompilationUnit) parser.createAST(monitor);
		DocumentSymbolVisitor visitor = new DocumentSymbolVisitor(astUnit);
		astUnit.accept(visitor);
		return visitor.getSymbols();
	}

	private static class DocumentSymbolVisitor extends ASTVisitor{
		private CompilationUnit astUnit = null;
		private List<DocumentSymbol> symbols = new ArrayList<>();
		private Map<ASTNode, DocumentSymbol> typeMappings = new HashMap<>();

		public DocumentSymbolVisitor(CompilationUnit astUnit) {
			this.astUnit = astUnit;
		}

		public List<DocumentSymbol> getSymbols() {
			return symbols;
		}

		@Override
		public boolean visit(PackageDeclaration node) {
			DocumentSymbol symbol = getDocumentSymbol(node.getName().getFullyQualifiedName(), node, node.getName());
			symbols.add(symbol);
			return super.visit(node);
		}

		@Override
		public boolean visit(TypeDeclaration node) {
			addAsTypeDocumentSymbol(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(EnumDeclaration node) {
			addAsTypeDocumentSymbol(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(RecordDeclaration node) {
			DocumentSymbol typeSymbol = addAsTypeDocumentSymbol(node);
			typeSymbol.setChildren(new ArrayList<>());
			List<?> recordComponents = node.recordComponents();
			for (Object recordComponent :  recordComponents) {
				if (recordComponent instanceof SingleVariableDeclaration svd) {
					DocumentSymbol component = getDocumentSymbol(svd.getName().toString(), svd, svd.getName());
					component.setKind(SymbolKind.Field);
					typeSymbol.getChildren().add(component);
				}
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(AnnotationTypeDeclaration node) {
			addAsTypeDocumentSymbol(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(AnnotationTypeMemberDeclaration node) {
			String memberName = node.getName().getIdentifier() + "()";
			String typeName = node.getType().toString();
			DocumentSymbol symbol = getDocumentSymbol(memberName, node, node.getName());
			symbol.setDetail(" : " + typeName);
			addAsChildDocumentSymbol(node, symbol);
			return super.visit(node);
		}

		@Override
		public boolean visit(EnumConstantDeclaration node) {
			DocumentSymbol symbol = getDocumentSymbol(node.getName().toString(), node, node.getName());
			addAsChildDocumentSymbol(node, symbol);
			return super.visit(node);
		}

		@Override
		public boolean visit(FieldDeclaration node) {
			List<?> fragments = node.fragments();
			for (Object fragment : fragments) {
				if (fragment instanceof VariableDeclarationFragment df) {
					DocumentSymbol symbol = getDocumentSymbol(
						df.getName().toString(), node, df.getName());
					addAsChildDocumentSymbol(node, symbol);
				}
			}

			return super.visit(node);
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			StringBuilder name = new StringBuilder(node.getName().getIdentifier());
			name.append("(");
			List<?> parameters = node.parameters();
			if (parameters != null) {
				List<String> params = new ArrayList<>();
				for (Object parameter : parameters) {
					if (parameter instanceof SingleVariableDeclaration vd) {
						String typeName = vd.getType().toString() + (vd.isVarargs() ? "..." : "");
						params.add(typeName);
					} else {
						params.add("Object");
					}
				}
				name.append(String.join(", ", params));
			}
			name.append(")");
			String returnType = null;
			if (node.getReturnType2() != null) {
				returnType = node.getReturnType2().toString();
			} else {
				returnType = node.isConstructor() ? null : "void";
			}
			DocumentSymbol symbol = getDocumentSymbol(name.toString(), node, node.getName());
			symbol.setDetail(returnType == null ? "" : " : " + returnType);
			addAsChildDocumentSymbol(node, symbol);
			return super.visit(node);
		}

		private DocumentSymbol addAsTypeDocumentSymbol(AbstractTypeDeclaration node) {
			DocumentSymbol symbol = getDocumentSymbol(node.getName().getIdentifier(), node, node.getName());
			symbols.add(symbol);
			typeMappings.put(node, symbol);
			return symbol;
		}

		private void addAsChildDocumentSymbol(ASTNode currentNode, DocumentSymbol symbol) {
			ASTNode parent = currentNode.getParent();
			if (parent == null) {
				return;
			}

			DocumentSymbol parentSymbol = typeMappings.get(parent);
			if (parentSymbol == null) {
				return;
			}

			if (parentSymbol.getChildren() == null) {
				parentSymbol.setChildren(new ArrayList<>());
			}

			parentSymbol.getChildren().add(symbol);
		}

		private DocumentSymbol getDocumentSymbol(String name, ASTNode node, ASTNode nameNode) {
			DocumentSymbol symbol = new DocumentSymbol();
			symbol.setName(name);
			symbol.setKind(getKind(node));
			symbol.setRange(getRange(node));
			symbol.setSelectionRange(getRange(nameNode));
			symbol.setDetail("");
			if (node instanceof BodyDeclaration bd
				&& containsModifier(bd.modifiers(), "@Deprecated")) {
				symbol.setTags(List.of(SymbolTag.Deprecated));
			}
			return symbol;
		}

		private Range getRange(ASTNode node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			Position startPosition = new Position(
				astUnit.getLineNumber(start) - 1, // convert 1-based to 0-based
				astUnit.getColumnNumber(start) // zero-based
			);
			Position endPosition = new Position(
				astUnit.getLineNumber(end) - 1, // convert 1-based to 0-based
				astUnit.getColumnNumber(end) // zero-based
			);
			return new Range(startPosition, endPosition);
		}

		private SymbolKind getKind(ASTNode node) {
			switch (node.getNodeType()) {
				case ASTNode.PACKAGE_DECLARATION:
					return SymbolKind.Package;
				case ASTNode.TYPE_DECLARATION:
					return ((TypeDeclaration) node).isInterface() ? SymbolKind.Interface : SymbolKind.Class;
				case ASTNode.ENUM_DECLARATION:
					return SymbolKind.Enum;
				case ASTNode.RECORD_DECLARATION:
					return SymbolKind.Class;
				case ASTNode.ANNOTATION_TYPE_DECLARATION:
					return SymbolKind.Property;
				case ASTNode.FIELD_DECLARATION:
					List<?> modifiers = ((FieldDeclaration) node).modifiers();
					if (containsModifier(modifiers, "static") && containsModifier(modifiers, "final")) {
						return SymbolKind.Constant;
					}
					return SymbolKind.Field;
				case ASTNode.ENUM_CONSTANT_DECLARATION:
					return SymbolKind.EnumMember;
				case ASTNode.METHOD_DECLARATION:
					return ((MethodDeclaration) node).isConstructor() ? SymbolKind.Constructor : SymbolKind.Method;
				case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
					return SymbolKind.Method;
				default:
					return SymbolKind.String;
			}
		}

		private boolean containsModifier(List<?> modifiers, String target) {
			if (modifiers == null || modifiers.isEmpty()) {
				return false;
			}

			for (Object modifier : modifiers) {
				if (Objects.equals(modifier.toString(), target)) {
					return true;
				}
			}

			return false;
		}
	}

	public static class ExtendedDocumentSymbol extends DocumentSymbol {
		/**
		 * The uri of the document of this symbol.
		 */
		@NonNull
		private String uri;

		/**
		 * The uri of the document of this symbol.
		 */
		public void setUri(@NonNull String uri) {
			this.uri = uri;
		}

		/**
		 * The uri of the document of this symbol.
		 */
		@NonNull
		@Pure
		public String getUri() {
			return this.uri;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + Objects.hash(uri);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!super.equals(obj)) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			ExtendedDocumentSymbol other = (ExtendedDocumentSymbol) obj;
			return Objects.equals(uri, other.uri);
		}
	}
}
