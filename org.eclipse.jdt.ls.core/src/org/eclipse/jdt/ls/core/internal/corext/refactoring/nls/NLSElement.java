/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.nls;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.Region;

public class NLSElement {

	public static final String TAG_PREFIX= "//$NON-NLS-"; //$NON-NLS-1$
	public static final int TAG_PREFIX_LENGTH= TAG_PREFIX.length();
	public static final String TAG_POSTFIX= "$"; //$NON-NLS-1$
	public static final int TAG_POSTFIX_LENGTH= TAG_POSTFIX.length();

	/** The original string denoted by the position */
	private String fValue;
	/** The position of the original string */
	private Region fPosition;

	/** Position of the // $NON_NLS_*$ tag */
	private Region fTagPosition;

	/** Index of the Element in an NLSLine */
	private int fIndex;
	private boolean fIsEclipseNLS;
	private AccessorClassReference fAccessorClassReference;


	public NLSElement(String value, int start, int length, int index, boolean isEclipseNLS) {
		fValue= value;
		fIndex= index;
		Assert.isNotNull(fValue);
		fPosition= new Region(start, length);
		fIsEclipseNLS= isEclipseNLS;
	}

	/**
	 * Returns the position of the string to be NLSed.
	 * @return Returns the position of the string to be NLSed
	 */
	public Region getPosition() {
		return fPosition;
	}

	/**
	 * Returns the actual string value.
	 * @return the actual string value
	 */
	public String getValue() {
		return fValue;
	}

	/**
	 * Sets the actual string value.
	 *
	 * @param value the value
	 */
	public void setValue(String value) {
		fValue= value;
	}

	/**
	 * Sets the tag position if one is associated with the NLS element.
	 *
	 * @param start the start offset
	 * @param length the length
	 */
	public void setTagPosition(int start, int length) {
		fTagPosition= new Region(start, length);
	}

	/**
	 * Returns the tag position for this element. The method can return <code>null</code>. In this
	 * case no tag has been found for this NLS element.
	 *
	 * @return the new tag region region
	 */
	public Region getTagPosition() {
		return fTagPosition;
	}

	/**
	 * Returns whether this element has an associated $NON-NLS-*$ tag.
	 *
	 * @return <code>true</code> if the NLS element has an associated $NON-NLS-*$ tag
	 */
	public boolean hasTag() {
		return fTagPosition != null && fTagPosition.getLength() > 0;
	}

	public static String createTagText(int index) {
		return TAG_PREFIX + index + TAG_POSTFIX;
	}

	public String getTagText() {
		return TAG_PREFIX + (fIndex + 1) + TAG_POSTFIX;
	}

	@Override
	public String toString() {
		return fPosition + ": " + fValue + "    Tag position: " + //$NON-NLS-2$ //$NON-NLS-1$
				(hasTag() ? fTagPosition.toString() : "no tag found"); //$NON-NLS-1$
	}

	//--------------- Eclipse NLS mechanism support ---------------

	/**
	 * Returns whether the standard resource bundle mechanism or
	 * the Eclipse NLSing mechanism is used.
	 *
	 * @return		<code>true</code> if the standard resource bundle mechanism
	 * 				is used and <code>false</code> NLSing is done the Eclipse way
	 * @since 3.1
	 */
	public boolean isEclipseNLS() {
		return fIsEclipseNLS;
	}

	/**
	 * Sets the accessor class reference for this element.
	 * <p>
	 * Note: this call is only valid when the element is
	 * using the Eclipse NLS mechanism.
	 * </p>
	 *
	 * @param accessorClassRef the accessor class reference
	 * @since 3.1
	 */
	public void setAccessorClassReference(AccessorClassReference accessorClassRef) {
		Assert.isTrue(fIsEclipseNLS);
		fAccessorClassReference= accessorClassRef;
	}

	/**
	 * Returns the accessor class reference for this element.
	 * <p>
	 * Note: this call is only valid when the element is
	 * using the Eclipse NLS mechanism.
	 * </p>
	 *
	 * @return the accessor class reference
	 * @since 3.1
	 */
	public AccessorClassReference getAccessorClassReference() {
		Assert.isTrue(fIsEclipseNLS);
		return fAccessorClassReference;
	}
}

