/*******************************************************************************
 * Copyright (c) 2019, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.FastJavaPartitioner
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.partition;

import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.ITypedRegion;

public class FastJavaPartitioner extends FastPartitioner {


	private boolean fIsTextBlockSupported= false;

	public FastJavaPartitioner(IPartitionTokenScanner scanner, String[] legalContentTypes) {
		super(scanner, legalContentTypes);
	}

	@Override
	protected void initialize() {
		super.initialize();
		fIsTextBlockSupported= isTextBlockSupported();
	}

	public void resetPositionCache() {
		clearPositionCache();
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent e) {
		super.documentAboutToBeChanged(e);
		if (hasTextBlockSupportedValueChanged()) {
			clearManagingPositionCategory();
			connect(fDocument, false);
		}
	}

	@Override
	public ITypedRegion[] computePartitioning(int offset, int length, boolean includeZeroLengthPartitions) {
		if (hasTextBlockSupportedValueChanged()) {
			clearManagingPositionCategory();
			connect(fDocument, false);
		}
		return super.computePartitioning(offset, length, includeZeroLengthPartitions);
	}

	public void cleanAndReConnectDocumentIfNecessary() {
		if (hasTextBlockSupportedValueChanged()) {
			clearManagingPositionCategory();
			connect(fDocument, false);
		}
	}

	public boolean hasTextBlockSupportedValueChanged() {
		boolean textBlockSupportedValueChanged= false;
		boolean textBlockSupported= isTextBlockSupported();
		if (textBlockSupported != fIsTextBlockSupported) {
			textBlockSupportedValueChanged= true;
		}
		return textBlockSupportedValueChanged;
	}

	private boolean isTextBlockSupported() {
		boolean isTextBlockSupported= false;
		if (fScanner instanceof FastJavaPartitionScanner) {
			isTextBlockSupported= ((FastJavaPartitionScanner) fScanner).isTextBlockSupported();
		} else {
			isTextBlockSupported= false;
		}
		return isTextBlockSupported;
	}

	private void clearManagingPositionCategory() {
		String[] categories= getManagingPositionCategories();
		for (String category : categories) {
			try {
				fDocument.removePositionCategory(category);
			} catch (BadPositionCategoryException e) {
				// do nothing
			}
		}
		clearPositionCache();
	}
}
