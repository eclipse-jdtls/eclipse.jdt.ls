/*******************************************************************************
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.reorg.MonitoringCreateTargetQueries
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg;

/**
 * Creates a new monitoring create target queries.
 *
 * @since 3.3
 */
public final class MonitoringCreateTargetQueries implements ICreateTargetQueries {

	private final ICreateTargetQueries fDelegate;

	private final CreateTargetExecutionLog fLog;

	/**
	 * Creates a new monitoring create target queries.
	 *
	 * @param delegate
	 *            the delegate
	 * @param log
	 *            the creation log
	 */
	public MonitoringCreateTargetQueries(ICreateTargetQueries delegate, CreateTargetExecutionLog log) {
		fDelegate= delegate;
		fLog= log;
	}

	@Override
	public ICreateTargetQuery createNewPackageQuery() {
		return new ICreateTargetQuery() {

			@Override
			public Object getCreatedTarget(Object selection) {
				final Object target= fDelegate.createNewPackageQuery().getCreatedTarget(selection);
				fLog.markAsCreated(selection, target);
				return target;
			}

			@Override
			public String getNewButtonLabel() {
				return fDelegate.createNewPackageQuery().getNewButtonLabel();
			}
		};
	}

	/**
	 * Returns the create target execution log.
	 *
	 * @return the create target execution log
	 */
	public CreateTargetExecutionLog getCreateTargetExecutionLog() {
		return fLog;
	}

	/**
	 * Returns the delegate queries.
	 *
	 * @return the delegate queries
	 */
	public ICreateTargetQueries getDelegate() {
		return fDelegate;
	}
}
