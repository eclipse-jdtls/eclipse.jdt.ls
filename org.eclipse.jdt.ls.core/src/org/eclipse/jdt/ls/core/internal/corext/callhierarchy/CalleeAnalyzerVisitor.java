/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.callhierarchy;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;

class CalleeAnalyzerVisitor extends HierarchicalASTVisitor {
    private final CallSearchResultCollector fSearchResults;
    private final IMember fMember;
    private final CompilationUnit fCompilationUnit;
    private final IProgressMonitor fProgressMonitor;
    private int fMethodEndPosition;
    private int fMethodStartPosition;

    CalleeAnalyzerVisitor(IMember member, CompilationUnit compilationUnit, IProgressMonitor progressMonitor) {
        fSearchResults = new CallSearchResultCollector();
        this.fMember = member;
        this.fCompilationUnit= compilationUnit;
        this.fProgressMonitor = progressMonitor;

        try {
            ISourceRange sourceRange = member.getSourceRange();
            this.fMethodStartPosition = sourceRange.getOffset();
            this.fMethodEndPosition = fMethodStartPosition + sourceRange.getLength();
        } catch (JavaModelException jme) {
            JavaPlugin.log(jme);
        }
    }

    /**
     * @return a map from handle identifier ({@link String}) to {@link MethodCall}
     */
    public Map<String, MethodCall> getCallees() {
        return fSearchResults.getCallers();
    }

    @Override
	public boolean visit(ClassInstanceCreation node) {
        progressMonitorWorked(1);
        if (!isFurtherTraversalNecessary(node)) {
            return false;
        }

        if (isNodeWithinMethod(node)) {
            addMethodCall(node.resolveConstructorBinding(), node);
        }

        return true;
    }

    /**
     * Find all constructor invocations (<code>this(...)</code>) from the called method.
     * Since we only traverse into the AST on the wanted method declaration, this method
     * should not hit on more constructor invocations than those in the wanted method.
     *
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.ConstructorInvocation)
     */
    @Override
	public boolean visit(ConstructorInvocation node) {
        progressMonitorWorked(1);
        if (!isFurtherTraversalNecessary(node)) {
            return false;
        }

        if (isNodeWithinMethod(node)) {
            addMethodCall(node.resolveConstructorBinding(), node);
        }

        return true;
    }

    /**
     * @see HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.AbstractTypeDeclaration)
     */
    @Override
	public boolean visit(AbstractTypeDeclaration node) {
    	progressMonitorWorked(1);
    	if (!isFurtherTraversalNecessary(node)) {
    		return false;
    	}

    	if (isNodeWithinMethod(node)) {
    		List<BodyDeclaration> bodyDeclarations= node.bodyDeclarations();
    		for (Iterator<BodyDeclaration> iter= bodyDeclarations.iterator(); iter.hasNext(); ) {
				BodyDeclaration bodyDeclaration= iter.next();
				if (bodyDeclaration instanceof MethodDeclaration) {
					MethodDeclaration child= (MethodDeclaration) bodyDeclaration;
					if (child.isConstructor()) {
						addMethodCall(child.resolveBinding(), child.getName());
					}
				}
			}
    		return false;
    	}

    	return true;
    }

    /**
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodDeclaration)
     */
    @Override
	public boolean visit(MethodDeclaration node) {
        progressMonitorWorked(1);
        return isFurtherTraversalNecessary(node);
    }

    /**
     * Find all method invocations from the called method. Since we only traverse into
     * the AST on the wanted method declaration, this method should not hit on more
     * method invocations than those in the wanted method.
     *
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodInvocation)
     */
    @Override
	public boolean visit(MethodInvocation node) {
        progressMonitorWorked(1);
        if (!isFurtherTraversalNecessary(node)) {
            return false;
        }

        if (isNodeWithinMethod(node)) {
            addMethodCall(node.resolveMethodBinding(), node);
        }

        return true;
    }

    /**
     * Find invocations of the supertype's constructor from the called method
     * (=constructor). Since we only traverse into the AST on the wanted method
     * declaration, this method should not hit on more method invocations than those in
     * the wanted method.
     *
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.SuperConstructorInvocation)
     */
    @Override
	public boolean visit(SuperConstructorInvocation node) {
        progressMonitorWorked(1);
        if (!isFurtherTraversalNecessary(node)) {
            return false;
        }

        if (isNodeWithinMethod(node)) {
            addMethodCall(node.resolveConstructorBinding(), node);
        }

        return true;
    }

    /**
     * Find all method invocations from the called method. Since we only traverse into
     * the AST on the wanted method declaration, this method should not hit on more
     * method invocations than those in the wanted method.
     * @param node node to visit
	 * @return whether children should be visited
     *
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodInvocation)
     */
    @Override
	public boolean visit(SuperMethodInvocation node) {
        progressMonitorWorked(1);
        if (!isFurtherTraversalNecessary(node)) {
            return false;
        }

        if (isNodeWithinMethod(node)) {
            addMethodCall(node.resolveMethodBinding(), node);
        }

        return true;
    }

    /**
     * When an anonymous class declaration is reached, the traversal should not go further since it's not
     * supposed to consider calls inside the anonymous inner class as calls from the outer method.
     *
     * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.AnonymousClassDeclaration)
     */
    @Override
	public boolean visit(AnonymousClassDeclaration node) {
        return isNodeEnclosingMethod(node);
    }


    /**
	 * Adds the specified method binding to the search results.
	 *
	 * @param calledMethodBinding the called method binding
	 * @param node the AST node
	 */
    protected void addMethodCall(IMethodBinding calledMethodBinding, ASTNode node) {
        try {
            if (calledMethodBinding != null) {
                fProgressMonitor.worked(1);

                ITypeBinding calledTypeBinding = calledMethodBinding.getDeclaringClass();
                IType calledType = null;

                if (!calledTypeBinding.isAnonymous()) {
                    calledType = (IType) calledTypeBinding.getJavaElement();
                } else {
                    if (!"java.lang.Object".equals(calledTypeBinding.getSuperclass().getQualifiedName())) { //$NON-NLS-1$
                        calledType= (IType) calledTypeBinding.getSuperclass().getJavaElement();
                    } else {
                        calledType = (IType) calledTypeBinding.getInterfaces()[0].getJavaElement();
                    }
                }

                IMethod calledMethod = findIncludingSupertypes(calledMethodBinding,
                        calledType, fProgressMonitor);

                IMember referencedMember= null;
                if (calledMethod == null) {
                    if (calledMethodBinding.isConstructor() && calledMethodBinding.getParameterTypes().length == 0) {
                        referencedMember= calledType;
                    }
                } else {
                    if (calledType.isInterface()) {
                        calledMethod = findImplementingMethods(calledMethod);
                    }

                    if (!isIgnoredBySearchScope(calledMethod)) {
                        referencedMember= calledMethod;
                    }
                }
                final int position= node.getStartPosition();
				final int number= fCompilationUnit.getLineNumber(position);
				fSearchResults.addMember(fMember, referencedMember, position, position + node.getLength(), number < 1 ? 1 : number);
            }
        } catch (JavaModelException jme) {
            JavaPlugin.log(jme);
        }
    }

    private static IMethod findIncludingSupertypes(IMethodBinding method, IType type, IProgressMonitor pm) throws JavaModelException {
		IMethod inThisType= Bindings.findMethod(method, type);
		if (inThisType != null) {
			return inThisType;
		}
		IType[] superTypes= JavaModelUtil.getAllSuperTypes(type, pm);
		for (int i= 0; i < superTypes.length; i++) {
			IMethod m= Bindings.findMethod(method, superTypes[i]);
			if (m != null) {
				return m;
			}
		}
		return null;
	}

    private boolean isIgnoredBySearchScope(IMethod enclosingElement) {
        if (enclosingElement != null) {
            return !getSearchScope().encloses(enclosingElement);
        } else {
            return false;
        }
    }

    private IJavaSearchScope getSearchScope() {
        return CallHierarchy.getDefault().getSearchScope();
    }

    private boolean isNodeWithinMethod(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();

        if (nodeStartPosition < fMethodStartPosition) {
            return false;
        }

        if (nodeEndPosition > fMethodEndPosition) {
            return false;
        }

        return true;
    }

    private boolean isNodeEnclosingMethod(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();

        if (nodeStartPosition < fMethodStartPosition && nodeEndPosition > fMethodEndPosition) {
            // Is the method completely enclosed by the node?
            return true;
        }
        return false;
    }

    private boolean isFurtherTraversalNecessary(ASTNode node) {
        return isNodeWithinMethod(node) || isNodeEnclosingMethod(node);
    }

    private IMethod findImplementingMethods(IMethod calledMethod) {
        Collection<IJavaElement> implementingMethods = CallHierarchy.getDefault()
                                                        .getImplementingMethods(calledMethod);

        if ((implementingMethods.size() == 0) || (implementingMethods.size() > 1)) {
            return calledMethod;
        } else {
            return (IMethod) implementingMethods.iterator().next();
        }
    }

    private void progressMonitorWorked(int work) {
        if (fProgressMonitor != null) {
            fProgressMonitor.worked(work);
            if (fProgressMonitor.isCanceled()) {
                throw new OperationCanceledException();
            }
        }
    }
}
