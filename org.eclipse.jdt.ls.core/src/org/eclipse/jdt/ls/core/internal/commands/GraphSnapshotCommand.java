/*******************************************************************************
 * Copyright (c) 2026 Jeongho Nam and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Jeongho Nam - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Exports one compiler-owned Java-model generation without per-declaration LSP
 * requests. The workspace root rule freezes resource state while the command
 * reconciles resident working copies and walks the Java model once.
 */
public final class GraphSnapshotCommand {

	public static final String COMMAND_ID = "java.graph.snapshot";
	public static final int SCHEMA_VERSION = 1;
	public static final int PROTOCOL_VERSION = 1;
	public static final String PRODUCER = "eclipse-jdtls-graph-snapshot";

	private static final String PLUGIN_ID = "org.eclipse.jdt.ls.core";
	private static final String SHA_256 = "SHA-256";
	private static String lastGeneration;
	private static String lastUniverse;
	private static long sequence;

	private GraphSnapshotCommand() {
	}

	/** Capture and publish only after the complete generation has been built. */
	public static synchronized Map<String, Object> execute(IProgressMonitor monitor) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
		workspace.run(progress -> captured.set(capture(progress)), workspace.getRoot(), IWorkspace.AVOID_UPDATE, monitor);
		Map<String, Object> snapshot = captured.get();
		String universe = (String) snapshot.get("universe");
		String generation = (String) snapshot.get("generation");
		String mode;
		if (lastGeneration == null) {
			mode = "initial";
			sequence = 1;
		} else if (lastGeneration.equals(generation)) {
			mode = "unchanged";
		} else {
			mode = lastUniverse.equals(universe) ? "incremental" : "reload";
			sequence++;
		}
		snapshot.put("mode", mode);
		snapshot.put("sequence", sequence);
		lastUniverse = universe;
		lastGeneration = generation;
		return snapshot;
	}

	private static Map<String, Object> capture(IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		IJavaModel model = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		IJavaProject[] projectArray = model.getJavaProjects();
		Arrays.sort(projectArray, Comparator.comparing(IJavaElement::getElementName));
		Map<String, ICompilationUnit> workingCopies = residentWorkingCopies();
		List<Map<String, Object>> projects = new ArrayList<>();
		List<Map<String, Object>> sources = new ArrayList<>();
		List<Map<String, Object>> nodes = new ArrayList<>();
		List<Map<String, Object>> edges = new ArrayList<>();

		for (IJavaProject project : projectArray) {
			checkCanceled(monitor);
			if (!project.exists() || !project.getProject().isOpen()) {
				continue;
			}
			projects.add(projectMetadata(project, monitor));
			for (ICompilationUnit primary : sourceUnits(project, workingCopies)) {
				checkCanceled(monitor);
				ICompilationUnit unit = workingCopies.getOrDefault(unitKey(primary), primary);
				if (unit.isWorkingCopy()) {
					unit.reconcile(ICompilationUnit.NO_AST, true, null, monitor);
				}
				captureUnit(project, unit, sources, nodes, edges, monitor);
			}
		}

		sources.sort(mapComparator("uri"));
		nodes.sort(mapComparator("symbol", "uri"));
		edges.sort(mapComparator("from", "to", "kind"));
		String universe = digestValue(projects);
		Map<String, Object> generationBody = map();
		generationBody.put("universe", universe);
		generationBody.put("sources", sources);
		generationBody.put("nodes", nodes);
		generationBody.put("edges", edges);

		Map<String, Object> producer = map();
		producer.put("name", PRODUCER);
		producer.put("version", producerVersion());
		producer.put("compilerVersion", projectCompilerVersions(projects));

		Map<String, Object> capabilities = map();
		capabilities.put("atomicGenerations", true);
		capabilities.put("resident", true);
		capabilities.put("sourceDigests", true);
		capabilities.put("diskDigests", true);
		capabilities.put("unsavedBuffers", true);
		capabilities.put("facts", List.of("contains"));

		Map<String, Object> snapshot = map();
		snapshot.put("schemaVersion", SCHEMA_VERSION);
		snapshot.put("protocolVersion", PROTOCOL_VERSION);
		snapshot.put("producer", producer);
		snapshot.put("capabilities", capabilities);
		snapshot.put("universe", universe);
		snapshot.put("generation", digestValue(generationBody));
		snapshot.put("projects", projects);
		snapshot.put("sources", sources);
		snapshot.put("nodes", nodes);
		snapshot.put("edges", edges);
		return snapshot;
	}

	private static Map<String, ICompilationUnit> residentWorkingCopies() {
		Map<String, ICompilationUnit> answer = new TreeMap<>();
		ICompilationUnit[] copies = JavaCore.getWorkingCopies(null);
		if (copies != null) {
			for (ICompilationUnit copy : copies) {
				if (copy != null && copy.exists()) {
					answer.put(unitKey(copy), copy);
				}
			}
		}
		return answer;
	}

	private static List<ICompilationUnit> sourceUnits(IJavaProject project, Map<String, ICompilationUnit> workingCopies) throws JavaModelException {
		Map<String, ICompilationUnit> units = new TreeMap<>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
				continue;
			}
			for (IJavaElement child : root.getChildren()) {
				if (child instanceof IPackageFragment fragment) {
					for (ICompilationUnit unit : fragment.getCompilationUnits()) {
						units.put(unitKey(unit), unit);
					}
				}
			}
		}
		for (ICompilationUnit copy : workingCopies.values()) {
			if (project.equals(copy.getJavaProject())) {
				units.put(unitKey(copy), copy);
			}
		}
		return new ArrayList<>(units.values());
	}

	private static void captureUnit(IJavaProject project, ICompilationUnit unit, List<Map<String, Object>> sources,
			List<Map<String, Object>> nodes, List<Map<String, Object>> edges, IProgressMonitor monitor) throws CoreException {
		unit.open(monitor);
		IBuffer buffer = unit.getBuffer();
		if (buffer == null) {
			return;
		}
		String content = buffer.getContents();
		IResource resource = unit.getResource();
		String uri = sourceUri(unit, resource);
		Charset charset = sourceCharset(resource);
		SourceText source = new SourceText(content);

		Map<String, Object> sourceRow = map();
		sourceRow.put("project", project.getElementName());
		sourceRow.put("uri", uri);
		sourceRow.put("checkerDigest", digest(content.getBytes(charset)));
		sourceRow.put("diskDigest", diskDigest(resource));
		sources.add(sourceRow);

		IType[] types = unit.getAllTypes();
		Arrays.sort(types, Comparator.comparing(IJavaElement::getHandleIdentifier));
		Set<String> emitted = new java.util.HashSet<>();
		for (IType type : types) {
			checkCanceled(monitor);
			String typeSymbol = typeSymbol(project, type);
			if (!emitted.add(typeSymbol)) {
				continue;
			}
			Map<String, Object> typeNode = node(project, type, typeSymbol, typeKind(type), type.getElementName(),
					type.getFullyQualifiedName('.'), "", source, uri);
			if (typeNode == null) {
				continue;
			}
			nodes.add(typeNode);
			IType parent = type.getDeclaringType();
			edges.add(contains(parent == null ? uri : typeSymbol(project, parent), typeSymbol, typeNode));
			captureFields(project, type, typeSymbol, source, uri, emitted, nodes, edges);
			captureMethods(project, type, typeSymbol, source, uri, emitted, nodes, edges);
		}
	}

	private static void captureFields(IJavaProject project, IType type, String owner, SourceText source,
			String uri, Set<String> emitted, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) throws JavaModelException {
		List<IField> fields = new ArrayList<>(Arrays.asList(type.getFields()));
		fields.addAll(Arrays.asList(type.getRecordComponents()));
		fields.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		for (IField field : fields) {
			String signature = canonicalType(type, field.getTypeSignature());
			String symbol = owner + "/field/" + field.getElementName();
			if (!emitted.add(symbol)) {
				continue;
			}
			Map<String, Object> fieldNode = node(project, field, symbol, "field", field.getElementName(),
					type.getFullyQualifiedName('.') + "." + field.getElementName(), signature, source, uri);
			if (fieldNode != null) {
				nodes.add(fieldNode);
				edges.add(contains(owner, symbol, fieldNode));
			}
		}
	}

	private static void captureMethods(IJavaProject project, IType type, String owner, SourceText source,
			String uri, Set<String> emitted, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		Arrays.sort(methods, Comparator.comparing(IJavaElement::getHandleIdentifier));
		for (IMethod method : methods) {
			List<String> parameters = Arrays.stream(method.getParameterTypes()).map(value -> canonicalType(type, value)).toList();
			String returnType = method.isConstructor() ? "" : canonicalType(type, method.getReturnType());
			String signature = "(" + String.join(",", parameters) + ")" + (returnType.isEmpty() ? "" : ":" + returnType);
			String kind = method.isConstructor() ? "constructor" : "method";
			String name = method.isConstructor() ? type.getElementName() : method.getElementName();
			String symbol = owner + "/" + kind + "/" + name + "(" + String.join(",", parameters) + ")";
			if (!emitted.add(symbol)) {
				continue;
			}
			Map<String, Object> methodNode = node(project, method, symbol, kind, name,
					type.getFullyQualifiedName('.') + "." + name, signature, source, uri);
			if (methodNode != null) {
				nodes.add(methodNode);
				edges.add(contains(owner, symbol, methodNode));
			}
		}
	}

	private static Map<String, Object> node(IJavaProject project, IJavaElement element, String symbol,
			String kind, String name, String qualifiedName, String signature, SourceText source, String uri) throws JavaModelException {
		if (!(element instanceof org.eclipse.jdt.core.ISourceReference reference)) {
			return null;
		}
		ISourceRange range = reference.getSourceRange();
		if (range == null || range.getOffset() < 0 || range.getLength() < 0) {
			return null;
		}
		int flags = element instanceof org.eclipse.jdt.core.IMember member ? member.getFlags() : 0;
		Map<String, Object> answer = map();
		answer.put("project", project.getElementName());
		answer.put("symbol", symbol);
		answer.put("nativeKey", element.getHandleIdentifier());
		answer.put("stability", name.isEmpty() || qualifiedName.isEmpty() ? "generation" : "persistent");
		answer.put("uri", uri);
		answer.put("name", name);
		answer.put("qualifiedName", qualifiedName);
		answer.put("kind", kind);
		answer.put("signature", signature);
		answer.put("exported", Flags.isPublic(flags) || Flags.isProtected(flags));
		answer.put("modifiers", modifiers(flags));
		answer.put("evidence", source.evidence(uri, range));
		return answer;
	}

	private static Map<String, Object> contains(String from, String to, Map<String, Object> target) {
		Map<String, Object> edge = map();
		edge.put("from", from);
		edge.put("to", to);
		edge.put("kind", "contains");
		edge.put("evidence", target.get("evidence"));
		return edge;
	}

	private static String typeSymbol(IJavaProject project, IType type) {
		String binary = type.getFullyQualifiedName('$');
		if (binary.isEmpty()) {
			return "java/" + project.getElementName() + "/generation/" + type.getHandleIdentifier();
		}
		return "java/" + project.getElementName() + "/type/" + binary;
	}

	private static String typeKind(IType type) throws JavaModelException {
		int flags = type.getFlags();
		if (Flags.isAnnotation(flags)) return "interface";
		if (Flags.isEnum(flags)) return "enum";
		if (type.isInterface()) return "interface";
		return "class";
	}

	private static String canonicalType(IType context, String rawSignature) {
		String erased = Signature.getTypeErasure(rawSignature);
		int dimensions = Signature.getArrayCount(erased);
		String element = Signature.toString(Signature.getElementType(erased));
		try {
			String[][] resolved = context.resolveType(element);
			if (resolved != null && resolved.length == 1) {
				element = resolved[0][0].isEmpty() ? resolved[0][1] : resolved[0][0] + "." + resolved[0][1];
			}
		} catch (JavaModelException ignored) {
			// The unresolved spelling is still stable and more truthful than a guess.
		}
		return element + "[]".repeat(dimensions);
	}

	private static List<String> modifiers(int flags) {
		List<String> answer = new ArrayList<>();
		if (Flags.isPublic(flags)) answer.add("public");
		if (Flags.isProtected(flags)) answer.add("protected");
		if (Flags.isPrivate(flags)) answer.add("private");
		if (Flags.isStatic(flags)) answer.add("static");
		if (Flags.isAbstract(flags)) answer.add("abstract");
		if (Flags.isFinal(flags)) answer.add("final");
		return answer;
	}

	private static Map<String, Object> projectMetadata(IJavaProject project, IProgressMonitor monitor) throws CoreException {
		Map<String, Object> metadata = map();
		metadata.put("name", project.getElementName());
		metadata.put("location", uriString(project.getProject().getLocationURI()));
		metadata.put("output", project.getOutputLocation().toPortableString());
		Map<String, String> options = new TreeMap<>(project.getOptions(true));
		metadata.put("options", options);
		metadata.put("compilerVersion", options.getOrDefault(JavaCore.COMPILER_COMPLIANCE, "unknown"));

		List<Map<String, Object>> classpath = new ArrayList<>();
		for (IClasspathEntry entry : project.getResolvedClasspath(true)) {
			checkCanceled(monitor);
			Map<String, Object> row = map();
			row.put("path", entry.getPath().toPortableString());
			row.put("entryKind", entry.getEntryKind());
			row.put("contentKind", entry.getContentKind());
			row.put("exported", entry.isExported());
			row.put("output", entry.getOutputLocation() == null ? "" : entry.getOutputLocation().toPortableString());
			row.put("contentDigest", classpathDigest(entry.getPath(), monitor));
			List<String> attributes = Arrays.stream(entry.getExtraAttributes())
					.sorted(Comparator.comparing(IClasspathAttribute::getName).thenComparing(IClasspathAttribute::getValue))
					.map(attribute -> attribute.getName() + "=" + attribute.getValue()).toList();
			row.put("attributes", attributes);
			row.put("inclusions", Arrays.stream(entry.getInclusionPatterns()).map(value -> value.toPortableString()).sorted().toList());
			row.put("exclusions", Arrays.stream(entry.getExclusionPatterns()).map(value -> value.toPortableString()).sorted().toList());
			classpath.add(row);
		}
		metadata.put("classpath", classpath);
		return metadata;
	}

	private static String classpathDigest(org.eclipse.core.runtime.IPath entryPath, IProgressMonitor monitor) throws CoreException {
		IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(entryPath);
		Path path = resource != null && resource.getLocation() != null ? resource.getLocation().toFile().toPath() : entryPath.toFile().toPath();
		if (!Files.exists(path)) {
			return digest(("missing:" + entryPath.toPortableString()).getBytes(StandardCharsets.UTF_8));
		}
		try {
			if (Files.isRegularFile(path)) return digest(path, monitor);
			MessageDigest hash = newDigest();
			try (Stream<Path> walk = Files.walk(path)) {
				List<Path> files = walk.filter(Files::isRegularFile).sorted(Comparator.comparing(value -> path.relativize(value).toString())).toList();
				for (Path file : files) {
					checkCanceled(monitor);
					update(hash, path.relativize(file).toString().replace('\\', '/'));
					update(hash, digest(file, monitor));
				}
			}
			return HexFormat.of().formatHex(hash.digest());
		} catch (IOException exception) {
			throw failure("Unable to digest classpath entry " + path, exception);
		}
	}

	private static String diskDigest(IResource resource) throws CoreException {
		if (!(resource instanceof IFile file) || file.getLocation() == null || !file.exists()) return "";
		try {
			return digest(file.getLocation().toFile().toPath(), null);
		} catch (IOException exception) {
			throw failure("Unable to digest source " + file.getFullPath(), exception);
		}
	}

	private static String digest(Path path, IProgressMonitor monitor) throws IOException {
		MessageDigest hash = newDigest();
		byte[] buffer = new byte[64 * 1024];
		try (InputStream input = Files.newInputStream(path)) {
			int read;
			while ((read = input.read(buffer)) != -1) {
				if (monitor != null) checkCanceled(monitor);
				hash.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(hash.digest());
	}

	private static Charset sourceCharset(IResource resource) throws CoreException {
		return resource instanceof IFile file ? Charset.forName(file.getCharset(true)) : StandardCharsets.UTF_8;
	}

	private static String sourceUri(ICompilationUnit unit, IResource resource) {
		URI location = resource == null ? null : resource.getLocationURI();
		return location == null ? unit.getPath().toFile().toURI().toString() : location.toString();
	}

	private static String unitKey(ICompilationUnit unit) {
		return unit.getPrimary().getPath().toPortableString();
	}

	private static String projectCompilerVersions(List<Map<String, Object>> projects) {
		return projects.stream().map(project -> (String) project.get("compilerVersion")).distinct().sorted().reduce((left, right) -> left + "; " + right).orElse("unknown");
	}

	private static String producerVersion() {
		Bundle bundle = FrameworkUtil.getBundle(GraphSnapshotCommand.class);
		return bundle == null ? "unknown" : bundle.getVersion().toString();
	}

	private static Comparator<Map<String, Object>> mapComparator(String... keys) {
		return (left, right) -> {
			for (String key : keys) {
				int compared = String.valueOf(left.get(key)).compareTo(String.valueOf(right.get(key)));
				if (compared != 0) return compared;
			}
			return 0;
		};
	}

	private static String digestValue(Object value) {
		MessageDigest hash = newDigest();
		hashValue(hash, value);
		return HexFormat.of().formatHex(hash.digest());
	}

	private static void hashValue(MessageDigest hash, Object value) {
		if (value == null) {
			update(hash, "null");
		} else if (value instanceof Map<?, ?> values) {
			update(hash, "map");
			values.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey()))).forEach(entry -> {
				update(hash, String.valueOf(entry.getKey()));
				hashValue(hash, entry.getValue());
			});
		} else if (value instanceof Collection<?> values) {
			update(hash, "list");
			values.forEach(item -> hashValue(hash, item));
		} else {
			update(hash, value.getClass().getName());
			update(hash, String.valueOf(value));
		}
	}

	private static void update(MessageDigest hash, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		hash.update(new byte[] { (byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16), (byte) (bytes.length >>> 8), (byte) bytes.length });
		hash.update(bytes);
	}

	private static String digest(byte[] bytes) {
		return HexFormat.of().formatHex(newDigest().digest(bytes));
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance(SHA_256);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(SHA_256 + " is unavailable", exception);
		}
	}

	private static CoreException failure(String message, Exception cause) {
		return new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, message, cause));
	}

	private static String uriString(URI uri) {
		return uri == null ? "" : uri.toString();
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();
	}

	private static Map<String, Object> map() {
		return new LinkedHashMap<>();
	}

	private static final class SourceText {
		private final int[] starts;

		SourceText(String content) {
			List<Integer> lines = new ArrayList<>();
			lines.add(0);
			for (int index = 0; index < content.length(); index++) {
				if (content.charAt(index) == '\n') lines.add(index + 1);
			}
			this.starts = lines.stream().mapToInt(Integer::intValue).toArray();
		}

		Map<String, Object> evidence(String uri, ISourceRange range) {
			int start = Math.max(0, range.getOffset());
			int end = Math.max(start, start + range.getLength());
			int startLine = line(start);
			int endLine = line(end);
			Map<String, Object> answer = map();
			answer.put("uri", uri);
			answer.put("startLine", startLine + 1);
			answer.put("startColumn", start - starts[startLine] + 1);
			answer.put("endLine", endLine + 1);
			answer.put("endColumn", end - starts[endLine] + 1);
			return answer;
		}

		private int line(int offset) {
			int found = Arrays.binarySearch(starts, offset);
			return found >= 0 ? found : Math.max(0, -found - 2);
		}
	}
}
