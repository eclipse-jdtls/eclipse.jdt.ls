/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.net.URI;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.DocumentChange;

public class ExternalFileChange extends DocumentChange {

	private URI file;

	public ExternalFileChange(String name, IDocument document, URI file) {
		super(name, document);
		this.file = file;
	}

	public URI getURI() {
		return file;
	}

}
