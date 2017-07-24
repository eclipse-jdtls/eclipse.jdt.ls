/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.debug;

import java.util.List;

import com.sun.jdi.request.EventRequest;

import io.reactivex.disposables.Disposable;

public interface IDebugResource extends AutoCloseable {
    List<EventRequest> requests();

    List<Disposable> subscriptions();
}
