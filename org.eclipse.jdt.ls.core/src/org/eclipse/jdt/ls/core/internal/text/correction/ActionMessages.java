/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.text.correction;

import org.eclipse.osgi.util.NLS;

public final class ActionMessages extends NLS {
	private static final String BUNDLE_NAME = ActionMessages.class.getName();

	private ActionMessages() {
		// Do not instantiate
	}

	public static String OverrideMethodsAction_label;
	public static String GenerateGetterSetterAction_label;
	public static String GenerateHashCodeEqualsAction_label;

	static {
		NLS.initializeMessages(BUNDLE_NAME, ActionMessages.class);
	}
}
