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

package org.eclipse.jdt.ls.debug.internal.adapter;

import java.util.HashMap;

public class IdCollection<T> {
    private int startId;
    private int nextId;
    private HashMap<Integer, T> idMap;

    public IdCollection() {
        this(1);
    }

    /**
     * Constructs a new id generator with the given startId as the start id number.
     * @param startId
     *              the start id number
     */
    public IdCollection(int startId) {
        this.startId = startId;
        this.nextId = startId;
        this.idMap = new HashMap<>();
    }

    public void reset() {
        this.nextId = this.startId;
        this.idMap.clear();
    }

    /**
     * Creates a id number for the given value.
     */
    public int create(T value) {
        int id = this.nextId++;
        this.idMap.put(id, value);
        return id;

    }

    public T get(int id) {
        return this.idMap.get(id);
    }

    public T remove(int id) {
        return this.idMap.remove(id);
    }
}
