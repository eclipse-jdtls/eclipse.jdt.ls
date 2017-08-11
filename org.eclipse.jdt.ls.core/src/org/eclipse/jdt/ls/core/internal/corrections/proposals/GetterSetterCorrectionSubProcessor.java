/*******************************************************************************
 * Copyright (c) 2007, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.GetterSetterCorrectionSubProcessor
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation;

public class GetterSetterCorrectionSubProcessor {

	private static class ProposalParameter {
		public final boolean useSuper;
		public final ICompilationUnit compilationUnit;
		public final ASTRewrite astRewrite;
		public final Expression accessNode;
		public final Expression qualifier;
		public final IVariableBinding variableBinding;

		public ProposalParameter(boolean useSuper, ICompilationUnit compilationUnit, ASTRewrite rewrite, Expression accessNode, Expression qualifier, IVariableBinding variableBinding) {
			this.useSuper = useSuper;
			this.compilationUnit = compilationUnit;
			this.astRewrite = rewrite;
			this.accessNode = accessNode;
			this.qualifier = qualifier;
			this.variableBinding = variableBinding;
		}
	}

	public static void addGetterSetterProposal(IInvocationContext context, IProblemLocation location, Collection<CUCorrectionProposal> proposals) {
		addGetterSetterProposal(context, location.getCoveringNode(context.getASTRoot()), proposals);
	}

	private static boolean addGetterSetterProposal(IInvocationContext context, ASTNode coveringNode, Collection<CUCorrectionProposal> proposals) {
		if (!(coveringNode instanceof SimpleName)) {
			return false;
		}
		SimpleName sn = (SimpleName) coveringNode;

		IBinding binding = sn.resolveBinding();
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		IVariableBinding variableBinding = (IVariableBinding) binding;
		if (!variableBinding.isField()) {
			return false;
		}

		if (proposals == null) {
			return true;
		}

		CUCorrectionProposal proposal = getProposal(context.getCompilationUnit(), sn, variableBinding);
		if (proposal != null) {
			proposals.add(proposal);
		}
		return true;
	}

	private static CUCorrectionProposal getProposal(ICompilationUnit cu, SimpleName sn, IVariableBinding variableBinding) {
		Expression accessNode = sn;
		Expression qualifier = null;
		boolean useSuper = false;

		ASTNode parent = sn.getParent();
		switch (parent.getNodeType()) {
			case ASTNode.QUALIFIED_NAME:
				accessNode = (Expression) parent;
				qualifier = ((QualifiedName) parent).getQualifier();
				break;
			case ASTNode.SUPER_FIELD_ACCESS:
				accessNode = (Expression) parent;
				qualifier = ((SuperFieldAccess) parent).getQualifier();
				useSuper = true;
				break;
		}
		ASTRewrite rewrite = ASTRewrite.create(sn.getAST());
		ProposalParameter gspc = new ProposalParameter(useSuper, cu, rewrite, accessNode, qualifier, variableBinding);
		return getProposal(gspc);
	}

	private static CUCorrectionProposal getProposal(ProposalParameter context) {
		IJavaElement element = context.variableBinding.getJavaElement();
		if (element instanceof IField) {
			IField field = (IField) element;
			CompilationUnit cu = SharedASTProvider.getInstance().getAST(field.getTypeRoot(), null);
			try {
				if (isSelfEncapsulateAvailable(field)) {
					return new SelfEncapsulateFieldProposal(getDescription(field), field.getCompilationUnit(), cu.getRoot(), context.variableBinding, field, IProposalRelevance.GETTER_SETTER_UNUSED_PRIVATE_FIELD);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Add getter proposal failure ", e);
			}
		}
		return null;
	}

	private static String getDescription(IField field) {
		return Messages.format(CorrectionMessages.GetterSetterCorrectionSubProcessor_creategetterunsingencapsulatefield_description, BasicElementLabels.getJavaElementName(field.getElementName()));
	}

	private static boolean isSelfEncapsulateAvailable(IField field) throws JavaModelException {
		return isAvailable(field) && !JdtFlags.isEnum(field) && !field.getDeclaringType().isInterface();
	}

	private static boolean isAvailable(IJavaElement javaElement) throws JavaModelException {
		if (javaElement == null) {
			return false;
		}
		if (!javaElement.exists()) {
			return false;
		}
		if (javaElement.isReadOnly()) {
			return false;
		}
		// work around for https://bugs.eclipse.org/bugs/show_bug.cgi?id=48422
		// the Java project is now cheating regarding its children so we shouldn't
		// call isStructureKnown if the project isn't open.
		// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=52474
		if (!(javaElement instanceof IJavaProject) && !(javaElement instanceof ILocalVariable) && !javaElement.isStructureKnown()) {
			return false;
		}
		if (javaElement instanceof IMember && ((IMember) javaElement).isBinary()) {
			return false;
		}
		return true;
	}
}
