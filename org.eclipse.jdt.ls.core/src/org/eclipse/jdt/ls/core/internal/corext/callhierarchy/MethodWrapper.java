/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 206949: [call hierarchy] filter field accesses (only write or only read)
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.callhierarchy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.IJavaSearchConstants;

/**
 * This class represents the general parts of a method call (either to or from a
 * method).
 *
 */
public abstract class MethodWrapper extends PlatformObject {
    private Map<String, MethodCall> fElements = null;

    /*
     * A cache of previously found methods. This cache should be searched
     * before adding a "new" method object reference to the list of elements.
     * This way previously found methods won't be searched again.
     */
    private Map<String, Map<String, MethodCall>> fMethodCache;
    private final MethodCall fMethodCall;
    private final MethodWrapper fParent;
    private int fLevel;
	/**
	 * One of {@link IJavaSearchConstants#REFERENCES}, {@link IJavaSearchConstants#READ_ACCESSES},
	 * or {@link IJavaSearchConstants#WRITE_ACCESSES}, or 0 if not set. Only used for root wrappers.
	 */
    private int fFieldSearchMode;

    public MethodWrapper(MethodWrapper parent, MethodCall methodCall) {
        Assert.isNotNull(methodCall);

        if (parent == null) {
            setMethodCache(new HashMap<String, Map<String, MethodCall>>());
            fLevel = 1;
        } else {
            setMethodCache(parent.getMethodCache());
            fLevel = parent.getLevel() + 1;
        }

        this.fMethodCall = methodCall;
        this.fParent = parent;
    }

    @SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter == IJavaElement.class) {
	        return (T) getMember();
	    } else {
	    	return null;
	    }
	}

    public MethodWrapper[] getCalls(IProgressMonitor progressMonitor) {
        if (fElements == null) {
            doFindChildren(progressMonitor);
        }

        MethodWrapper[] result = new MethodWrapper[fElements.size()];
        int i = 0;

        for (Iterator<String> iter = fElements.keySet().iterator(); iter.hasNext();) {
            MethodCall methodCall = getMethodCallFromMap(fElements, iter.next());
            result[i++] = createMethodWrapper(methodCall);
        }

        return result;
    }

    public int getLevel() {
        return fLevel;
    }

    public IMember getMember() {
        return getMethodCall().getMember();
    }

    public MethodCall getMethodCall() {
        return fMethodCall;
    }

    public String getName() {
        if (getMethodCall() != null) {
            return getMethodCall().getMember().getElementName();
        } else {
            return ""; //$NON-NLS-1$
        }
    }

    public MethodWrapper getParent() {
        return fParent;
    }

    public int getFieldSearchMode() {
    	if (fFieldSearchMode != 0) {
			return fFieldSearchMode;
		}
    	MethodWrapper parent= getParent();
    	while (parent != null) {
			if (parent.fFieldSearchMode != 0) {
				return parent.fFieldSearchMode;
			} else {
				parent= parent.getParent();
			}
		}
    	return IJavaSearchConstants.REFERENCES;
	}

    public void setFieldSearchMode(int fieldSearchMode) {
		fFieldSearchMode= fieldSearchMode;
	}

    @Override
	public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }

        if (oth == null) {
            return false;
        }

        if (oth.getClass() != getClass()) {
            return false;
        }

        MethodWrapper other = (MethodWrapper) oth;

        if (this.fParent == null) {
            if (other.fParent != null) {
                return false;
            }
        } else {
            if (!this.fParent.equals(other.fParent)) {
                return false;
            }
        }

        if (this.getMethodCall() == null) {
            if (other.getMethodCall() != null) {
                return false;
            }
        } else {
            if (!this.getMethodCall().equals(other.getMethodCall())) {
                return false;
            }
        }

        return true;
    }

    @Override
	public int hashCode() {
        final int PRIME = 1000003;
        int result = 0;

        if (fParent != null) {
            result = (PRIME * result) + fParent.hashCode();
        }

        if (getMethodCall() != null) {
            result = (PRIME * result) + getMethodCall().getMember().hashCode();
        }

        return result;
    }

    private void setMethodCache(Map<String, Map<String, MethodCall>> methodCache) {
        fMethodCache = methodCache;
    }

    protected abstract String getTaskName();

    private void addCallToCache(MethodCall methodCall) {
        Map<String, MethodCall> cachedCalls = lookupMethod(this.getMethodCall());
        cachedCalls.put(methodCall.getKey(), methodCall);
    }

	/**
	 * Creates a method wrapper for the child of the receiver.
	 *
	 * @param methodCall the method call
	 * @return the method wrapper
	 */
    protected abstract MethodWrapper createMethodWrapper(MethodCall methodCall);

    private void doFindChildren(IProgressMonitor progressMonitor) {
        Map<String, MethodCall> existingResults = lookupMethod(getMethodCall());

        if (existingResults != null && !existingResults.isEmpty()) {
            fElements = new HashMap<>();
            fElements.putAll(existingResults);
        } else {
            initCalls();

            if (progressMonitor != null) {
                progressMonitor.beginTask(getTaskName(), 100);
            }

            try {
                performSearch(progressMonitor);
            } catch (OperationCanceledException e){
            	fElements= null;
            	throw e;
            } finally {
                if (progressMonitor != null) {
                    progressMonitor.done();
                }
            }
        }
    }

    /**
     * Determines if the method represents a recursion call (i.e. whether the
     * method call is already in the cache.)
     *
     * @return True if the call is part of a recursion
     */
    public boolean isRecursive() {
		if (fParent instanceof RealCallers) {
			return false;
		}
        MethodWrapper current = getParent();

        while (current != null) {
            if (getMember().getHandleIdentifier().equals(current.getMember()
                                                                        .getHandleIdentifier())) {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }

	/**
	 * @return whether this member can have children
	 */
	public abstract boolean canHaveChildren();

    /**
     * This method finds the children of the current IMember (either callers or
     * callees, depending on the concrete subclass).
     * @param progressMonitor a progress monitor
     *
     * @return a map from handle identifier ({@link String}) to {@link MethodCall}
     */
    protected abstract Map<String, MethodCall> findChildren(IProgressMonitor progressMonitor);

    private Map<String, Map<String, MethodCall>> getMethodCache() {
        return fMethodCache;
    }

    private void initCalls() {
        this.fElements = new HashMap<>();

        initCacheForMethod();
    }

    /**
     * Looks up a previously created search result in the "global" cache.
     * @param methodCall the method call
     * @return the List of previously found search results
     */
    private Map<String, MethodCall> lookupMethod(MethodCall methodCall) {
        return getMethodCache().get(methodCall.getKey());
    }

    private void performSearch(IProgressMonitor progressMonitor) {
        fElements = findChildren(progressMonitor);

        for (Iterator<String> iter = fElements.keySet().iterator(); iter.hasNext();) {
            checkCanceled(progressMonitor);

            MethodCall methodCall = getMethodCallFromMap(fElements, iter.next());
            addCallToCache(methodCall);
        }
    }

    private MethodCall getMethodCallFromMap(Map<String, MethodCall> elements, String key) {
        return elements.get(key);
    }

    private void initCacheForMethod() {
        Map<String, MethodCall> cachedCalls = new HashMap<>();
        getMethodCache().put(this.getMethodCall().getKey(), cachedCalls);
    }

    /**
     * Checks with the progress monitor to see whether the creation of the type hierarchy
     * should be canceled. Should be regularly called
     * so that the user can cancel.
     *
     * @param progressMonitor the progress monitor
     * @exception OperationCanceledException if cancelling the operation has been requested
     * @see IProgressMonitor#isCanceled
     */
    protected void checkCanceled(IProgressMonitor progressMonitor) {
        if (progressMonitor != null && progressMonitor.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    /**
     * Allows a visitor to traverse the call hierarchy. The visiting is stopped when
     * a recursive node is reached.
     *
     * @param visitor the visitor
     * @param progressMonitor the progress monitor
     */
    public void accept(CallHierarchyVisitor visitor, IProgressMonitor progressMonitor) {
        if (getParent() != null && getParent().isRecursive()) {
            return;
        }
        checkCanceled(progressMonitor);

        visitor.preVisit(this);
        if (visitor.visit(this)) {
            MethodWrapper[] methodWrappers= getCalls(progressMonitor);
            for (int i= 0; i < methodWrappers.length; i++) {
                methodWrappers[i].accept(visitor, progressMonitor);
            }
        }
        visitor.postVisit(this);

        if (progressMonitor != null) {
            progressMonitor.worked(1);
        }
    }

	/**
	 * Removes the given method call from the cache.
	 *
	 * @since 3.6
	 */
	public void removeFromCache() {
		fElements= null;
		fMethodCache.remove(getMethodCall().getKey());
	}
}
