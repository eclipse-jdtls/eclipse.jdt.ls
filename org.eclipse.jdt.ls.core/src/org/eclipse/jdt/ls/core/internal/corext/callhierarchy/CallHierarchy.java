/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.callhierarchy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.StringMatcher;

public class CallHierarchy {
    private static final String PREF_USE_IMPLEMENTORS= "PREF_USE_IMPLEMENTORS"; //$NON-NLS-1$
    private static final String PREF_USE_FILTERS = "PREF_USE_FILTERS"; //$NON-NLS-1$
    private static final String PREF_FILTERS_LIST = "PREF_FILTERS_LIST"; //$NON-NLS-1$

    private static final String DEFAULT_IGNORE_FILTERS = "java.*,javax.*"; //$NON-NLS-1$
    private static CallHierarchy fgInstance;
    private IJavaSearchScope fSearchScope;
    private StringMatcher[] fFilters;

    public static CallHierarchy getDefault() {
        if (fgInstance == null) {
            fgInstance = new CallHierarchy();
        }

        return fgInstance;
    }

    public boolean isSearchUsingImplementorsEnabled() {
		return true;
    }

    public Collection<IJavaElement> getImplementingMethods(IMethod method) {
        if (isSearchUsingImplementorsEnabled()) {
            IJavaElement[] result = Implementors.getInstance().searchForImplementors(new IJavaElement[] {
                        method
                    }, new NullProgressMonitor());

            if ((result != null) && (result.length > 0)) {
                return Arrays.asList(result);
            }
        }

        return new ArrayList<>(0);
    }

    public Collection<IJavaElement> getInterfaceMethods(IMethod method) {
        if (isSearchUsingImplementorsEnabled()) {
            IJavaElement[] result = Implementors.getInstance().searchForInterfaces(new IJavaElement[] {
                        method
                    }, new NullProgressMonitor());

            if ((result != null) && (result.length > 0)) {
                return Arrays.asList(result);
            }
        }

        return new ArrayList<>(0);
    }

    public MethodWrapper[] getCallerRoots(IMember[] members) {
    	return getRoots(members, true);
    }

    public MethodWrapper[] getCalleeRoots(IMember[] members) {
    	return getRoots(members, false);
    }

	private MethodWrapper[] getRoots(IMember[] members, boolean callers) {
		ArrayList<MethodWrapper> roots= new ArrayList<>();
    	for (int i= 0; i < members.length; i++) {
			IMember member= members[i];
			if (member instanceof IType) {
				IType type= (IType) member;
				try {
					if (! type.isAnonymous()) {
						IMethod[] constructors= JavaElementUtil.getAllConstructors(type);
						if (constructors.length == 0) {
							addRoot(member, roots, callers); // IType is a stand-in for the non-existing default constructor
						} else {
							for (int j= 0; j < constructors.length; j++) {
								IMethod constructor= constructors[j];
								addRoot(constructor, roots, callers);
							}
						}
					} else {
						addRoot(member, roots, callers);
					}
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.log(e);
				}
			} else {
				addRoot(member, roots, callers);
			}
		}
    	return roots.toArray(new MethodWrapper[roots.size()]);
	}

	private void addRoot(IMember member, ArrayList<MethodWrapper> roots, boolean callers) {
		MethodCall methodCall= new MethodCall(member);
		MethodWrapper root;
		if (callers) {
			root= new CallerMethodWrapper(null, methodCall);
		} else {
			root= new CalleeMethodWrapper(null, methodCall);
		}
		roots.add(root);
	}

    public static CallLocation getCallLocation(Object element) {
        CallLocation callLocation = null;

        if (element instanceof MethodWrapper) {
            MethodWrapper methodWrapper = (MethodWrapper) element;
            MethodCall methodCall = methodWrapper.getMethodCall();

            if (methodCall != null) {
                callLocation = methodCall.getFirstCallLocation();
            }
        } else if (element instanceof CallLocation) {
            callLocation = (CallLocation) element;
        }

        return callLocation;
    }

    public IJavaSearchScope getSearchScope() {
        if (fSearchScope == null) {
            fSearchScope= SearchEngine.createWorkspaceScope();
        }

        return fSearchScope;
    }

    public void setSearchScope(IJavaSearchScope searchScope) {
        this.fSearchScope = searchScope;
    }

	/**
	 * Checks whether the fully qualified name is ignored by the set filters.
	 *
	 * @param fullyQualifiedName the fully qualified name
	 *
	 * @return <code>true</code> if the fully qualified name is ignored
	 */
    public boolean isIgnored(String fullyQualifiedName) {
        if ((getIgnoreFilters() != null) && (getIgnoreFilters().length > 0)) {
            for (int i = 0; i < getIgnoreFilters().length; i++) {
                String fullyQualifiedName1 = fullyQualifiedName;

                if (getIgnoreFilters()[i].match(fullyQualifiedName1)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns filters for packages which should not be included in the search results.
     *
     * @return StringMatcher[]
     */
    private StringMatcher[] getIgnoreFilters() {
        if (fFilters == null) {
			fFilters = parseList(DEFAULT_IGNORE_FILTERS);
        }

        return fFilters;
    }

    public static boolean arePossibleInputElements(List<?> elements) {
		if (elements.size() < 1) {
			return false;
		}
		for (Iterator<?> iter= elements.iterator(); iter.hasNext();) {
			if (! isPossibleInputElement(iter.next())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Parses the comma separated string into an array of {@link StringMatcher} objects.
	 *
	 * @param listString the string to parse
	 * @return an array of {@link StringMatcher} objects
	 */
    private static StringMatcher[] parseList(String listString) {
        List<StringMatcher> list = new ArrayList<>(10);
        StringTokenizer tokenizer = new StringTokenizer(listString, ","); //$NON-NLS-1$

        while (tokenizer.hasMoreTokens()) {
            String textFilter = tokenizer.nextToken().trim();
            list.add(new StringMatcher(textFilter, false, false));
        }

        return list.toArray(new StringMatcher[list.size()]);
    }

    static CompilationUnit getCompilationUnitNode(IMember member, boolean resolveBindings) {
    	ITypeRoot typeRoot= member.getTypeRoot();
        try {
	    	if (typeRoot.exists() && typeRoot.getBuffer() != null) {
				ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				parser.setSource(typeRoot);
				parser.setResolveBindings(resolveBindings);
				return (CompilationUnit) parser.createAST(null);
	    	}
        } catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e);
        }
        return null;
    }

    public static boolean isPossibleInputElement(Object element){
    	if (! (element instanceof IMember)) {
			return false;
		}

		if (element instanceof IModuleDescription) {
			return false;
		}

    	if (element instanceof IType) {
			IType type= (IType) element;
			try {
				return type.isClass() || type.isEnum();
			} catch (JavaModelException e) {
				return false;
			}
		}

    	return true;
    }
}
