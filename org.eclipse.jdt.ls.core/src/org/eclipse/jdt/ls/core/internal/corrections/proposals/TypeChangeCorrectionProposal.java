/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/proposals/TypeChangeCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.core.manipulation.BindingLabelProviderCore;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.DimensionRewrite;
import org.eclipse.jdt.internal.corext.dom.TypeAnnotationRewrite;
import org.eclipse.jdt.internal.corext.fix.TypeParametersFixCore.InsertTypeArgumentsVisitor;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.lsp4j.CodeActionKind;


public class TypeChangeCorrectionProposal extends ASTRewriteCorrectionProposal {

	private final IBinding fBinding;
	private final CompilationUnit fAstRoot;
	private final ITypeBinding fNewType;
	private final ITypeBinding[] fTypeProposals;
	private final TypeLocation fTypeLocation;
	private final boolean fIsNewTypeVar;
	private static String VAR_TYPE= "var"; //$NON-NLS-1$

	public TypeChangeCorrectionProposal(ICompilationUnit targetCU, IBinding binding, CompilationUnit astRoot, ITypeBinding newType, boolean offerSuperTypeProposals, int relevance) {
		this(targetCU, binding, astRoot, newType, false, offerSuperTypeProposals, relevance);
	}

	//This needs to be used to convert a given type to var type.
	public TypeChangeCorrectionProposal(ICompilationUnit targetCU, IBinding binding, CompilationUnit astRoot, ITypeBinding oldType, int relevance) {
		this(targetCU, binding, astRoot, oldType, true, false, relevance);
	}

	private TypeChangeCorrectionProposal(ICompilationUnit targetCU, IBinding binding, CompilationUnit astRoot, ITypeBinding newType, boolean isNewTypeVar, boolean offerSuperTypeProposals,
			int relevance) {
		super("", CodeActionKind.QuickFix, targetCU, null, relevance); //$NON-NLS-1$

		Assert.isTrue(binding != null && (binding.getKind() == IBinding.METHOD || binding.getKind() == IBinding.VARIABLE) && Bindings.isDeclarationBinding(binding));

		fBinding= binding; // must be generic method or (generic) variable
		fAstRoot= astRoot;
		fIsNewTypeVar= isNewTypeVar;

		if (offerSuperTypeProposals) {
			fTypeProposals= ASTResolving.getRelaxingTypes(astRoot.getAST(), newType);
			sortTypes(fTypeProposals);
			fNewType= fTypeProposals[0];
		} else {
			if (!fIsNewTypeVar) {
				fNewType= newType;
			} else {
				fNewType= null;
			}
			fTypeProposals= null;
		}

		String typeName;
		if (isNewTypeVar) {
			typeName= VAR_TYPE;
		} else {
			typeName= BindingLabelProviderCore.getBindingLabel(fNewType, JavaElementLabels.ALL_DEFAULT);
		}
		if (binding.getKind() == IBinding.VARIABLE) {
			IVariableBinding varBinding= (IVariableBinding) binding;
			String[] args= { BasicElementLabels.getJavaElementName(varBinding.getName()),  BasicElementLabels.getJavaElementName(typeName)};
			if (varBinding.isField()) {
				fTypeLocation= TypeLocation.FIELD;
				setDisplayName(Messages.format(CorrectionMessages.TypeChangeCompletionProposal_field_name, args));
			} else if (astRoot.findDeclaringNode(binding) instanceof SingleVariableDeclaration) {
				fTypeLocation= TypeLocation.PARAMETER;
				setDisplayName(Messages.format(CorrectionMessages.TypeChangeCompletionProposal_param_name, args));
			} else {
				fTypeLocation= TypeLocation.LOCAL_VARIABLE;
				setDisplayName(Messages.format(CorrectionMessages.TypeChangeCompletionProposal_variable_name, args));
			}
		} else {
			String[] args= { binding.getName(), typeName };
			fTypeLocation= TypeLocation.RETURN_TYPE;
			setDisplayName(Messages.format(CorrectionMessages.TypeChangeCompletionProposal_method_name, args));
		}
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		ASTNode boundNode= fAstRoot.findDeclaringNode(fBinding);
		ASTNode declNode= null;
		CompilationUnit newRoot= fAstRoot;
		if (boundNode != null) {
			declNode= boundNode; // is same CU
		} else {
			newRoot= ASTResolving.createQuickFixAST(getCompilationUnit(), null);
			declNode= newRoot.findDeclaringNode(fBinding.getKey());
		}
		if (declNode != null) {
			AST ast= declNode.getAST();
			ASTRewrite rewrite= ASTRewrite.create(ast);
			ImportRewrite imports= createImportRewrite(newRoot);

			ImportRewriteContext context= new ContextSensitiveImportRewriteContext(newRoot, declNode.getStartPosition(), imports);
			ImportRemover remover = new ImportRemover(getCompilationUnit().getJavaProject(), newRoot);
			Type type;
			if (fIsNewTypeVar) {
				type = ast.newSimpleType(ast.newName(VAR_TYPE));
			} else {
				type = imports.addImport(fNewType, ast, context, fTypeLocation);
			}

			if (declNode instanceof MethodDeclaration methodDecl) {
				Type origReturnType= methodDecl.getReturnType2();
				rewrite.set(methodDecl, MethodDeclaration.RETURN_TYPE2_PROPERTY, type, null);
				DimensionRewrite.removeAllChildren(methodDecl, MethodDeclaration.EXTRA_DIMENSIONS2_PROPERTY, rewrite, null);
				TypeAnnotationRewrite.removePureTypeAnnotations(methodDecl, MethodDeclaration.MODIFIERS2_PROPERTY, rewrite, null);
				// add javadoc tag
				Javadoc javadoc= methodDecl.getJavadoc();
				if (javadoc != null && origReturnType != null && origReturnType.isPrimitiveType()
						&& ((PrimitiveType) origReturnType).getPrimitiveTypeCode() == PrimitiveType.VOID) {

					TagElement returnTag= JavadocTagsSubProcessor.findTag(javadoc, TagElement.TAG_RETURN, null);
					if (returnTag == null) {
						returnTag= ast.newTagElement();
						returnTag.setTagName(TagElement.TAG_RETURN);
						TextElement commentStart= ast.newTextElement();
						returnTag.fragments().add(commentStart);

						ListRewrite tagsRewriter= rewrite.getListRewrite(javadoc, Javadoc.TAGS_PROPERTY);
						JavadocTagsSubProcessor.insertTag(tagsRewriter, returnTag, null);
					}
				}

			} else if (declNode instanceof AnnotationTypeMemberDeclaration methodDecl) {
				rewrite.set(methodDecl, AnnotationTypeMemberDeclaration.TYPE_PROPERTY, type, null);
			} else if (declNode instanceof VariableDeclarationFragment) {
				ASTNode parent= declNode.getParent();
				if (parent instanceof FieldDeclaration fieldDecl) {
					if (fieldDecl.fragments().size() > 1 && fieldDecl.getParent() instanceof AbstractTypeDeclaration typeDecl) { // split
						VariableDeclarationFragment placeholder= (VariableDeclarationFragment) rewrite.createMoveTarget(declNode);
						FieldDeclaration newField= ast.newFieldDeclaration(placeholder);
						newField.setType(type);

						ListRewrite listRewrite= rewrite.getListRewrite(typeDecl, typeDecl.getBodyDeclarationsProperty());
						if (fieldDecl.fragments().indexOf(declNode) == 0) { // if it as the first in the list-> insert before
							listRewrite.insertBefore(newField, parent, null);
						} else {
							listRewrite.insertAfter(newField, parent, null);
						}
					} else {
						rewrite.set(fieldDecl, FieldDeclaration.TYPE_PROPERTY, type, null);
						DimensionRewrite.removeAllChildren(declNode, VariableDeclarationFragment.EXTRA_DIMENSIONS2_PROPERTY, rewrite, null);
						TypeAnnotationRewrite.removePureTypeAnnotations(fieldDecl, FieldDeclaration.MODIFIERS2_PROPERTY, rewrite, null);
					}
				} else if (parent instanceof VariableDeclarationStatement varDecl) {
					if (varDecl.fragments().size() > 1 && (varDecl.getParent() instanceof Block)) { // split
						VariableDeclarationFragment placeholder= (VariableDeclarationFragment) rewrite.createMoveTarget(declNode);
						VariableDeclarationStatement newStat= ast.newVariableDeclarationStatement(placeholder);
						newStat.setType(type);

						ListRewrite listRewrite= rewrite.getListRewrite(varDecl.getParent(), Block.STATEMENTS_PROPERTY);
						if (varDecl.fragments().indexOf(declNode) == 0) { // if it as the first in the list-> insert before
							listRewrite.insertBefore(newStat, parent, null);
						} else {
							listRewrite.insertAfter(newStat, parent, null);
						}
						if (fIsNewTypeVar) {
							handledInferredParameterizedType(newStat, declNode, ast, rewrite, imports, context);
						}
					} else {
						Type oldType = (Type) rewrite.get(varDecl, VariableDeclarationStatement.TYPE_PROPERTY);
						rewrite.set(varDecl, VariableDeclarationStatement.TYPE_PROPERTY, type, null);
						DimensionRewrite.removeAllChildren(declNode, VariableDeclarationFragment.EXTRA_DIMENSIONS2_PROPERTY, rewrite, null);
						if (fIsNewTypeVar) {
							handledInferredParameterizedType(parent, declNode, ast, rewrite, imports, context);
							TypeAnnotationRewrite.removePureTypeAnnotations(parent, VariableDeclarationStatement.MODIFIERS2_PROPERTY, rewrite, null);
							if (oldType != null) {
								remover.registerRemovedNode(oldType);
							}
						}
					}
				} else if (parent instanceof VariableDeclarationExpression varDecl) {
					Type oldType = (Type) rewrite.get(varDecl, VariableDeclarationExpression.TYPE_PROPERTY);
					rewrite.set(varDecl, VariableDeclarationExpression.TYPE_PROPERTY, type, null);
					DimensionRewrite.removeAllChildren(declNode, VariableDeclarationFragment.EXTRA_DIMENSIONS2_PROPERTY, rewrite, null);
					if (fIsNewTypeVar) {
						handledInferredParameterizedType(parent, declNode, ast, rewrite, imports, context);
						TypeAnnotationRewrite.removePureTypeAnnotations(parent, VariableDeclarationExpression.MODIFIERS2_PROPERTY, rewrite, null);
						if (oldType != null) {
							remover.registerRemovedNode(oldType);
						}
					}
				}
			} else if (declNode instanceof SingleVariableDeclaration variableDeclaration) {
				Type oldType = (Type) rewrite.get(variableDeclaration, SingleVariableDeclaration.TYPE_PROPERTY);
				rewrite.set(variableDeclaration, SingleVariableDeclaration.TYPE_PROPERTY, type, null);
				DimensionRewrite.removeAllChildren(declNode, SingleVariableDeclaration.EXTRA_DIMENSIONS2_PROPERTY, rewrite, null);
				TypeAnnotationRewrite.removePureTypeAnnotations(declNode, SingleVariableDeclaration.MODIFIERS2_PROPERTY, rewrite, null);
				if (fIsNewTypeVar) {
					if (oldType != null) {
						remover.registerRemovedNode(oldType);
					}
				}
			}

			return rewrite;
		}
		return null;
	}

	private void sortTypes(ITypeBinding[] typeProposals) {
		ITypeBinding oldType;
		if (fBinding instanceof IMethodBinding methodBinding) {
			oldType = methodBinding.getReturnType();
		} else {
			oldType= ((IVariableBinding) fBinding).getType();
		}
		if (! oldType.isParameterizedType()) {
			return;
		}

		final ITypeBinding oldTypeDeclaration= oldType.getTypeDeclaration();
		Arrays.sort(typeProposals, new Comparator<ITypeBinding>() {
			@Override
			public int compare(ITypeBinding o1, ITypeBinding o2) {
				return rank(o2) - rank(o1);
			}

			private int rank(ITypeBinding type) {
				if (type.getTypeDeclaration().equals(oldTypeDeclaration)) {
					return 1;
				}
				return 0;
			}
		});
	}

	private void handledInferredParameterizedType(ASTNode node, ASTNode declaringNode, AST ast, ASTRewrite rewrite, ImportRewrite importRewrite, ImportRewriteContext context) {
		if (ast == null || rewrite == null || importRewrite == null || context == null) {
			return;
		}
		ASTNode processNode = null;
		List<VariableDeclarationFragment> fragments = null;
		if (node instanceof VariableDeclarationStatement variableDecl) {
			fragments = variableDecl.fragments();
		} else if (node instanceof VariableDeclarationExpression variableDecl) {
			fragments = variableDecl.fragments();
		}
		if (fragments != null && fragments.size() == 1) {
			VariableDeclarationFragment varFrag = fragments.get(0);
			processNode = varFrag.getInitializer();
			if (processNode == null && declaringNode instanceof VariableDeclarationFragment variableDeclFragment) {
				processNode = variableDeclFragment.getInitializer();
			}
		}
		ParameterizedType createdType = null;
		if (processNode instanceof ClassInstanceCreation creation) {
			Type type = creation.getType();
			if (type instanceof ParameterizedType parameterizedType) {
				createdType = parameterizedType;
			}
		}
		if (createdType == null) {
			return;
		}

		final ArrayList<ASTNode> changedNodes = new ArrayList<>();
		node.accept(new InsertTypeArgumentsVisitor(changedNodes));
		if (changedNodes.isEmpty()) {
			return;
		}

		ITypeBinding binding = createdType.resolveBinding();
		if (binding != null) {
			ITypeBinding[] typeArguments = binding.getTypeArguments();
			ListRewrite argumentsRewrite = rewrite.getListRewrite(createdType, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
			for (int i = 0; i < typeArguments.length; i++) {
				ITypeBinding typeArgument = typeArguments[i];
				Type argumentNode = importRewrite.addImport(typeArgument, ast, context, TypeLocation.TYPE_ARGUMENT);
				argumentsRewrite.insertLast(argumentNode, null);
			}
		}
	}

}
