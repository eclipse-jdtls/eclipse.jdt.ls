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

package org.eclipse.jdt.ls.debug.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An utility object pool class with the following  ability: 
 * 1. store an object to get an object id.
 * 2. remove an object.
 * 3. remove objects which has specified owner.
 * 4. remove all objects.
 *
 * <p>It is thread-safe, the duplicate object will not be stored, an object can be referenced by multiple owners, it is
 * removed only when user explicitly calls removeObjectById or all the owners has been removed.</p>
 *
 * @param <O> the owner class type
 * @param <V> the object type
 */
public class RecyclableObjectPool<O, V> {
    private final IdCollection<V> objectCollection = new IdCollection<>();
    private final Map<V, Set<O>> referenceMap = new HashMap<>();
    private final Map<V, Integer> objectIdMap = new HashMap<>();
    
    /**
     * Add an object into this pool, if the object is already added, the original id will be used, it will also create a 
     * reference link from the object to its owner. 
     * 
     * @param owner the owner of this object
     * @param object the object
     * @return the inner id of this object
     */
    public int addObject(O owner, V object) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner cannot be null.");
        }
        if (object == null) {
            throw new IllegalArgumentException("Null object cannot be added.");
        }
        synchronized (this) {
            if (!referenceMap.containsKey(object)) {
                // the object is new
                Set<O> owners = new HashSet<>(1);
                owners.add(owner);
                referenceMap.put(object, owners);
                int id = objectCollection.create(object);
                objectIdMap.put(object, id);
                return id;
            } else {
                // the object is already in this pool
                referenceMap.get(object).add(owner);
                return objectIdMap.get(object);
            }
        }
    }

    /**
     * Get the object by object id.
     * 
     * @param id the object id.
     * @return the object, null if the object cannot be found.
     */
    public V getObjectById(int id) {
        synchronized (this) {
            return objectCollection.get(id);
        }
    }

    /**
     * Remove the object by object id.
     * 
     * @param id the object id.
     * @return true if the object is removed successfully, false if the object cannot be found.
     */
    public boolean removeObjectById(int id) {
        synchronized (this) {
            V object = this.objectCollection.remove(id);
            if (object == null)  {
                return false;
            }
            referenceMap.remove(object);
            objectIdMap.remove(object);
            return true;
        }
    }

    /**
     * Remove a group of objects with the owner, the objects which only refers this owner will be removed.
     *  
     * @param owner the owner.
     * @return true if any object is removed.
     */
    public boolean removeObjectsByOwner(O owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner cannot be null.");
        }
        synchronized (this) {
            List<V> recycling = new ArrayList<>();
            referenceMap.forEach((key, value) -> {
                if (value.remove(owner)) {
                    if (value.isEmpty()) {
                        recycling.add(key);
                    }
                }
            });
            for (V recycled : recycling) {
                this.objectCollection.remove(objectIdMap.remove(recycled));
                referenceMap.remove(recycled);
            }
            return !recycling.isEmpty();
        }
    }

    /**
     * Removes all the objects.
     */
    public void removeAllObjects() {
        synchronized (this) {
            this.objectCollection.reset();
            this.referenceMap.clear();
            this.objectIdMap.clear();
        }
    }
}
