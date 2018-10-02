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

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Enum;
import static org.eclipse.lsp4j.SymbolKind.Interface;
import static org.eclipse.lsp4j.TypeHierarchyDirection.Children;
import static org.eclipse.lsp4j.TypeHierarchyDirection.Parents;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyDirection;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyParams;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Iterables;

public class TypeHierarchyHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject jarProject;
	private IProject srcProject;

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("eclipse/hello", "maven/salut"));
		jarProject = WorkspaceHelper.getProject("salut");
		srcProject = WorkspaceHelper.getProject("hello");
	}

	@Test
	public void subTypeInJar_type() throws Exception {
		// Line 99 from `WordUtils`
		//     public static String wrap(final String str, final int wrapLength) {
		String uri = getUriFromJarProject("org.apache.commons.lang3.text.WordUtils");
		doAssert(getSubTypes(uri, 98, 19), "String", Class);
	}

	@Test
	public void subTypeInJar_variable() throws Exception {
		// Line 284 from `WordUtils`.
		//         final StringBuilder wrappedLine = new StringBuilder(inputLineLength + 32);
		String uri = getUriFromJarProject("org.apache.commons.lang3.text.WordUtils");
		doAssert(getSubTypes(uri, 283, 29), "WordUtils", Class);
	}

	@Test
	public void subTypeInJar_method() throws Exception {
		// Line 99 from `WordUtils`
		//     public static String wrap(final String str, final int wrapLength) {
		String uri = getUriFromJarProject("org.apache.commons.lang3.text.WordUtils");
		doAssert(getSubTypes(uri, 98, 26), "WordUtils", Class);
	}

	@Test
	public void subTypeInJar_typeArgument() throws Exception {
		// Line 324 from `TypeUtils`
		//            final Map<TypeVariable<?>, Type> typeVarAssigns) {
		String uri = getUriFromJarProject("org.apache.commons.lang3.reflect.TypeUtils");
		doAssert(getSubTypes(uri, 323, 22), "TypeVariable<D extends GenericDeclaration>", Interface);
	}

	@Test
	public void subTypeInJar_noSelectedJavaElement() throws Exception {
		// Line 324 from `TypeUtils`
		//            final Map<TypeVariable<?>, Type> typeVarAssigns) {
		String uri = getUriFromJarProject("org.apache.commons.lang3.reflect.TypeUtils");
		doAssert(getSubTypes(uri, 0, 0), "TypeUtils", Class);
	}

	@Test
	public void superTypeInJar_typeArgument() throws Exception {
		// Line 324 from `TypeUtils`
		//            final Map<TypeVariable<?>, Type> typeVarAssigns) {
		String uri = getUriFromJarProject("org.apache.commons.lang3.reflect.TypeUtils");
		TypeHierarchyItem typeVariableNode = doAssert(getSuperTypes(uri, 323, 22), "TypeVariable<D extends GenericDeclaration>", Interface);
		List<TypeHierarchyItem> superTypes = getSuperTypes(typeVariableNode).getParents();
		assertEquals(2, superTypes.size());
		doAssertContains(superTypes, "AnnotatedElement", Interface);
		doAssertContains(superTypes, "Type", Interface);
	}

	@Test
	public void superTypeInJar_ctor() throws Exception {
		// Line 109 from `CompareToBuilder`
		//    public CompareToBuilder() {
		String uri = getUriFromJarProject("org.apache.commons.lang3.builder.CompareToBuilder");
		TypeHierarchyItem typeVariableNode = doAssert(getSuperTypes(uri, 108, 11), "CompareToBuilder", Class);
		List<TypeHierarchyItem> superTypes = getSuperTypes(typeVariableNode).getParents();
		assertEquals(2, superTypes.size());
		doAssertContains(superTypes, "Object", Class);
		doAssertContains(superTypes, "Builder<T>", Interface);
	}

	@Test
	public void superTypeInJar_interfaceDoesNotExtendObject() throws Exception {
		// Line 79 from `Builder`
		//public interface Builder<T> {
		String uri = getUriFromJarProject("org.apache.commons.lang3.builder.Builder");
		TypeHierarchyItem typeVariableNode = doAssert(getSuperTypes(uri, 78, 17), "Builder<T>", Interface);
		List<TypeHierarchyItem> superTypes = getSuperTypes(typeVariableNode).getParents();
		assertEquals(0, superTypes.size());
	}

	@Test
	public void superTypeInJar_enum() throws Exception {
		// Line 303 from `JavaVersion`
		//    JAVA_1_8(1.8f, "1.8"),
		String uri = getUriFromJarProject("org.apache.commons.lang3.JavaVersion");
		TypeHierarchyItem enumNode = doAssert(getSuperTypes(uri, 302, 4), "JavaVersion", Enum);
		List<TypeHierarchyItem> superTypes = getSuperTypes(enumNode).getParents();
		assertEquals(1, superTypes.size());
		doAssertContains(superTypes, "Enum<E extends Enum<E>>", Class);
		List<TypeHierarchyItem> superSuperTypes = getSuperTypes(superTypes.iterator().next()).getParents();
		assertEquals(3, superSuperTypes.size());
		doAssertContains(superSuperTypes, "Object", Class);
		doAssertContains(superSuperTypes, "Comparable<T>", Interface);
		doAssertContains(superSuperTypes, "Serializable", Interface);
	}

	@Test
	public void subTypeInSrc_typeInMethodSignature() throws Exception {
		// Line 8 from `Baz`
		//	protected File getParent(File file, int depth) {
		String uri = getUriFromSrcProject("org.sample.Baz");
		doAssert(getSubTypes(uri, 7, 28), "File", Class).getChildren();

		List<TypeHierarchyItem> superTypes = doAssert(getSuperTypes(uri, 7, 28), "File", Class).getParents();
		doAssertContains(superTypes, "Object", Class);
		doAssertContains(superTypes, "Comparable<T>", Interface);
		doAssertContains(superTypes, "Serializable", Interface);
	}

	@Test
	public void subTypeInSrc_paramInMethodSignature() throws Exception {
		// Line 8 from `Baz`
		//	protected File getParent(File file, int depth) {
		String uri = getUriFromSrcProject("org.sample.Baz");
		List<TypeHierarchyItem> subTypes = doAssert(getSubTypes(uri, 7, 33), "Baz", Class).getChildren();
		assertEquals(0, subTypes.size());

		List<TypeHierarchyItem> superTypes = doAssert(getSuperTypes(uri, 7, 33), "Baz", Class).getParents();
		doAssertContains(superTypes, "Object", Class);
	}

	@Test
	public void subTypeInSrc_variableType() throws Exception {
		// Line 6 from `Highlight`
		//		String string = "";
		String uri = getUriFromSrcProject("org.sample.Highlight");
		List<TypeHierarchyItem> subTypes = doAssert(getSubTypes(uri, 5, 2), "String", Class).getChildren();
		assertEquals(0, subTypes.size());

		List<TypeHierarchyItem> superTypes = doAssert(getSuperTypes(uri, 5, 2), "String", Class).getParents();
		doAssertContains(superTypes, "Object", Class);
		doAssertContains(superTypes, "Comparable<T>", Interface);
		doAssertContains(superTypes, "CharSequence", Interface);
		doAssertContains(superTypes, "Serializable", Interface);
	}

	@Test
	public void subTypeInSrc_variableName() throws Exception {
		// Line 6 from `Highlight`
		//		String string = "";
		String uri = getUriFromSrcProject("org.sample.Highlight");
		doAssert(getSubTypes(uri, 5, 9), "Highlight", Class).getChildren();
	}

	/**
	 * Asserts the node and returns with the argument if valid. Otherwise, throws an
	 * exception.
	 */
	private TypeHierarchyItem doAssert(TypeHierarchyItem node, String expectedName, SymbolKind expectedKind) {
		assertNotNull(node);
		assertEquals("Unexpected name in: " + node.toString(), expectedName, node.getName());
		assertEquals("Unexpected symbol kind in: " + node.toString(), expectedKind, node.getKind());
		return node;
	}

	private TypeHierarchyItem doAssertContains(Iterable<? extends TypeHierarchyItem> nodes, String expectedName, SymbolKind expectedKind) {
		assertNotNull(nodes);
		//@formatter:off
		return StreamSupport.stream(nodes.spliterator(), false)
			.filter(node -> node.getName().equals(expectedName))
			.filter(node -> node.getKind().equals(expectedKind))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
					new StringBuilder("Cannot find node with name: '")
						.append(expectedName)
						.append("' and kind: '")
						.append(expectedKind)
						.append("' in ")
						.append(Iterables.toString(nodes))
						.append(".")
						.toString()));
		//@formatter:on

	}

	private String getUriFromSrcProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(srcProject, className);
	}

	private String getUriFromJarProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(jarProject, className);
	}

	private TypeHierarchyItem getSubTypes(String uri, int line, int character) {
		return new TypeHierarchyHandler(preferenceManager).typeHierarchy(newParams(uri, line, character, Children, 1), new NullProgressMonitor());
	}

	private TypeHierarchyItem getSuperTypes(TypeHierarchyItem node) {
		String uri = node.getUri();
		Position position = node.getSelectionRange().getStart();
		int line = position.getLine();
		int character = position.getCharacter();
		return new TypeHierarchyHandler(preferenceManager).typeHierarchy(newParams(uri, line, character, Parents, 1), new NullProgressMonitor());
	}

	private TypeHierarchyItem getSuperTypes(String uri, int line, int character) {
		return new TypeHierarchyHandler(preferenceManager).typeHierarchy(newParams(uri, line, character, Parents, 1), new NullProgressMonitor());
	}

	private TypeHierarchyParams newParams(String uri, int line, int character, TypeHierarchyDirection direction, int resolve) {
		TypeHierarchyParams params = new TypeHierarchyParams();
		params.setDirection(direction);
		params.setPosition(new Position(line, character));
		params.setTextDocument(new TextDocumentIdentifier(uri));
		params.setResolve(resolve);
		return params;
	}
}
