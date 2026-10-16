/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.mbt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level shape of an {@code mbt.json} file.
 *
 * <p>Supports two formats:
 * <ul>
 *   <li><b>namespaces format</b>: {@code "namespaces": { "id": {...}, ... }}</li>
 *   <li><b>targets format</b>: {@code "targets": [ { "id": "...", ... }, ... ]}</li>
 * </ul>
 */
public class MbtInfo {

	// "namespaces" format (map keyed by target ID)
	private Map<String, MbtTargetInfo> namespaces;

	// "targets" format (array with id field in each entry)
	private List<MbtTargetInfo> targets;

	private Collection<MbtDependencyModuleInfo> dependencyModules;

	/**
	 * Returns targets indexed by their ID for fast lookup.
	 * Supports both the "namespaces" (map) and "targets" (array) formats.
	 */
	public Map<String, MbtTargetInfo> getNamespaces() {
		if (namespaces != null && !namespaces.isEmpty()) {
			return namespaces;
		}
		if (targets != null && !targets.isEmpty()) {
			Map<String, MbtTargetInfo> map = new LinkedHashMap<>();
			for (MbtTargetInfo target : targets) {
				if (target.getId() != null) {
					map.put(target.getId(), target);
				}
			}
			return map;
		}
		return Collections.emptyMap();
	}

	public Collection<MbtDependencyModuleInfo> getDependencyModules() {
		return dependencyModules != null ? dependencyModules : Collections.emptyList();
	}

	/**
	 * Promotes dependency modules that have become targets. For each target,
	 * any {@code dependencyModule} ID that matches a known target ID is
	 * moved to {@code dependsOn}. Called after per-module cascade generation
	 * to establish proper source-project references.
	 */
	public void promoteTargets() {
		Map<String, MbtTargetInfo> ns = getNamespaces();
		if (ns.isEmpty()) {
			return;
		}
		Set<String> targetIds = ns.keySet();
		for (MbtTargetInfo target : ns.values()) {
			for (String targetId : targetIds) {
				target.promoteDependencyToTarget(targetId);
			}
		}
		if (dependencyModules != null) {
			dependencyModules.removeIf(dep -> targetIds.contains(dep.getId()));
		}
	}

	/**
	 * Merges another {@link MbtInfo} into this instance, adding new targets
	 * and dependency modules. Used for per-module on-demand generation where
	 * each module's MBT info is generated independently and accumulated.
	 */
	public void merge(MbtInfo other) {
		if (other == null) {
			return;
		}
		Map<String, MbtTargetInfo> otherNamespaces = other.getNamespaces();
		if (!otherNamespaces.isEmpty()) {
			if (this.namespaces == null) {
				this.namespaces = new LinkedHashMap<>();
			}
			this.namespaces.putAll(otherNamespaces);
		}
		Collection<MbtDependencyModuleInfo> otherDeps = other.getDependencyModules();
		if (!otherDeps.isEmpty()) {
			if (this.dependencyModules == null) {
				this.dependencyModules = new ArrayList<>();
			}
			Set<String> existingIds = new HashSet<>();
			for (MbtDependencyModuleInfo dep : this.dependencyModules) {
				existingIds.add(dep.getId());
			}
			for (MbtDependencyModuleInfo dep : otherDeps) {
				if (!existingIds.contains(dep.getId())) {
					this.dependencyModules.add(dep);
				}
			}
		}
	}
}
