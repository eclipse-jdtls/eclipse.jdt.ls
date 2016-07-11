/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression.Operator;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;


public class OccurrencesFinder extends ASTVisitor implements IOccurrencesFinder {

	public static final String ID= "OccurrencesFinder"; //$NON-NLS-1$

	public static final String IS_WRITEACCESS= "writeAccess"; //$NON-NLS-1$
	public static final String IS_VARIABLE= "variable"; //$NON-NLS-1$

	private CompilationUnit fRoot;
	private Name fSelectedNode;
	private IBinding fTarget;

	private List<OccurrenceLocation> fResult;
	private Set<Name> fWriteUsages;

	private boolean fTargetIsStaticMethodImport;

	public OccurrencesFinder() {
		super(true);
	}

	@Override
	public String initialize(CompilationUnit root, int offset, int length) {
		return initialize(root, NodeFinder.perform(root, offset, length));
	}

	@Override
	public String initialize(CompilationUnit root, ASTNode node) {
		if (!(node instanceof Name))
			return "No element";
		fRoot= root;
		fSelectedNode= (Name)node;
		fTarget= fSelectedNode.resolveBinding();
		if (fTarget == null)
			return "No binding";
		fTarget= getBindingDeclaration(fTarget);

		fTargetIsStaticMethodImport= isStaticImport(fSelectedNode.getParent());
		return null;
	}

	private void performSearch() {
		if (fResult == null) {
			fResult= new ArrayList<>();
			fWriteUsages= new HashSet<>();
			if (fRoot != null) {
				fRoot.accept(this);
			}
		}
	}

	@Override
	public OccurrenceLocation[] getOccurrences() {
		performSearch();
		if (fResult.isEmpty())
			return null;
		return fResult.toArray(new OccurrenceLocation[fResult.size()]);
	}

	@Override
	public CompilationUnit getASTRoot() {
		return fRoot;
	}

	@Override
	public String getElementName() {
		if (fSelectedNode != null) {
			return ASTNodes.asString(fSelectedNode);
		}
		return null;
	}

	@Override
	public boolean visit(QualifiedName node) {
		final IBinding binding= node.resolveBinding();
		if (binding instanceof IVariableBinding && ((IVariableBinding)binding).isField()) {
			SimpleName name= node.getName();
			return !addUsage(name, name.resolveBinding());
		}
		if (binding instanceof IMethodBinding) {
			if (isStaticImport(node)) {
				SimpleName name= node.getName();
				return !addPossibleStaticImport(name, (IMethodBinding) binding);
			}
		}
		return !addUsage(node, binding);
	}

	private static boolean isStaticImport(ASTNode node) {
		if (!(node instanceof QualifiedName))
			return false;

		ASTNode parent= ((QualifiedName)node).getParent();
		return parent instanceof ImportDeclaration && ((ImportDeclaration) parent).isStatic();
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (fTargetIsStaticMethodImport) {
			return !addPossibleStaticImport(node.getName(), node.resolveMethodBinding());
		}
		return true;
	}

	@Override
	public boolean visit(SimpleName node) {
		addUsage(node, node.resolveBinding());
		return true;
	}

	/*
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.ConstructorInvocation)
	 */
	@Override
	public boolean visit(ClassInstanceCreation node) {
		// match with the constructor and the type.

		Type type= node.getType();
		if (type instanceof ParameterizedType) {
			type= ((ParameterizedType) type).getType();
		}
		if (type instanceof SimpleType) {
			Name name= ((SimpleType) type).getName();
			if (name instanceof QualifiedName)
				name= ((QualifiedName)name).getName();
			addUsage(name, node.resolveConstructorBinding());
		} else if (type instanceof NameQualifiedType) {
			Name name= ((NameQualifiedType) type).getName();
			addUsage(name, node.resolveConstructorBinding());
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(Assignment node) {
		SimpleName name= getSimpleName(node.getLeftHandSide());
		if (name != null)
			addWrite(name, name.resolveBinding());
		return true;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		addWrite(node.getName(), node.resolveBinding());
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (node.getParent() instanceof FieldDeclaration || node.getInitializer() != null)
			addWrite(node.getName(), node.resolveBinding());
		return true;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		PrefixExpression.Operator operator= node.getOperator();
		if (operator == Operator.INCREMENT || operator == Operator.DECREMENT) {
			SimpleName name= getSimpleName(node.getOperand());
			if (name != null)
				addWrite(name, name.resolveBinding());
		}
		return true;
	}

	@Override
	public boolean visit(PostfixExpression node) {
		SimpleName name= getSimpleName(node.getOperand());
		if (name != null)
			addWrite(name, name.resolveBinding());
		return true;
	}

	private boolean addWrite(Name node, IBinding binding) {
		if (binding != null && Bindings.equals(getBindingDeclaration(binding), fTarget)) {
			fWriteUsages.add(node);
			return true;
		}
		return false;
	}

	private boolean addUsage(Name node, IBinding binding) {
		if (binding != null && Bindings.equals(getBindingDeclaration(binding), fTarget)) {
			int flag= 0;
			if (fTarget instanceof IVariableBinding) {
				boolean isWrite= fWriteUsages.remove(node);
				flag= isWrite ? F_WRITE_OCCURRENCE : F_READ_OCCURRENCE;
			}
			fResult.add(new OccurrenceLocation(node.getStartPosition(), node.getLength(), flag));
			return true;
		}
		return false;
	}

	@Override
	public int getSearchKind() {
		return K_OCCURRENCE;
	}



	private boolean addPossibleStaticImport(Name node, IMethodBinding binding) {
		if (binding == null || node == null || !(fTarget instanceof IMethodBinding) || !Modifier.isStatic(binding.getModifiers()))
			return false;

		IMethodBinding targetMethodBinding= (IMethodBinding)fTarget;
		if ((fTargetIsStaticMethodImport || Modifier.isStatic(targetMethodBinding.getModifiers())) && (targetMethodBinding.getDeclaringClass().getTypeDeclaration() == binding.getDeclaringClass().getTypeDeclaration())) {
			if (node.getFullyQualifiedName().equals(targetMethodBinding.getName())) {
				fResult.add(new OccurrenceLocation(node.getStartPosition(), node.getLength(), 0));
				return true;
			}
		}
		return false;
	}

	private SimpleName getSimpleName(Expression expression) {
		if (expression instanceof SimpleName)
			return ((SimpleName)expression);
		else if (expression instanceof QualifiedName)
			return (((QualifiedName) expression).getName());
		else if (expression instanceof FieldAccess)
			return ((FieldAccess)expression).getName();
		return null;
	}

	private IBinding getBindingDeclaration(IBinding binding) {
		switch (binding.getKind()) {
			case IBinding.TYPE :
				return ((ITypeBinding)binding).getTypeDeclaration();
			case IBinding.METHOD :
				return ((IMethodBinding)binding).getMethodDeclaration();
			case IBinding.VARIABLE :
				return ((IVariableBinding)binding).getVariableDeclaration();
			default:
				return binding;
		}
	}

	@Override
	public String getID() {
		return ID;
	}

}
