/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;

/**
 * The context used to resolve the signature help.
 */
public class SignatureHelpContext {
	/**
	 * {@link #completionOffset()}
	 */
	private int completionOffset = -1;

	/**
	 * {@link #secondaryCompletionOffset()}
	 */
	private int secondaryCompletionOffset = -1;

	/**
	 * {@link #argumentRanges()}
	 */
	private List<int[]> argumentRanges = new ArrayList<>();

	/**
	 * {@link #methodName()}
	 */
	private String methodName;

	/**
	 * {@link #declaringTypeNames()}
	 */
	private List<String> declaringTypeNames;

	/**
	 * {@link #arguments()}
	 */
	private List<Expression> arguments;

	/**
	 * {@link #parameterTypes()}
	 */
	private String[] parameterTypes;

	/**
	 * {@link #parameterTypesFromBinding()}
	 */
	private String[] parameterTypesFromBinding;

	/**
	 * {@link #targetNode()}
	 */
	private ASTNode targetNode;

	/**
	 * Resolve the context.
	 * @param triggerOffset the offset where signature help is triggered
	 * @param unit compilation unit
	 * @param monitor progress monitor
	 * @throws JavaModelException
	 */
	public void resolve(int triggerOffset, ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		CompilationUnit root = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		if (root == null || monitor.isCanceled()) {
			return;
		}
		findTargetNode(root, unit, triggerOffset);
		resolveMethodName(this.targetNode);
		resolveDeclaringTypeName(this.targetNode);
		this.arguments = resolveArguments(this.targetNode);
		resolveParameterTypes(this.targetNode);
		guessCompletionOffset(this.targetNode, unit);
		guessArgumentRanges(unit, this.completionOffset);
	}

	/**
	 * Find the target method-like AST node.
	 * @param root
	 * @param unit
	 * @param triggerOffset
	 * @return an method-like AST node, or <code>null</code> if no such node is found.
	 * @throws JavaModelException
	 */
	private void findTargetNode(ASTNode root, ICompilationUnit unit, int triggerOffset) throws JavaModelException {
		if (root == null) {
			return;
		}

		String source = unit.getSource();
		if (source == null) {
			return;
		}

		int searchOffset = triggerOffset;
		while (searchOffset > 0) {
			char cur = source.charAt(searchOffset);
			char prev = source.charAt(searchOffset - 1);
			// Keep decrease the offset until we meet a character, or ';' (make sure not going to
			// the previous method expression).
			if (Character.isWhitespace(cur) && prev != ';') {
				searchOffset--;
			// for case like: 'foo(bar, |)', the parsed AST node for foo() will only have one
			// argument. If we don't decrease the offset, the AST node found will be a Block.
			} else if (cur == ')' && (Character.isWhitespace(prev) || prev == ',')) {
				searchOffset--;
			} else {
				break;
			}
		}
		searchOffset = searchOffset <= 0 ? triggerOffset : searchOffset;

		ASTNode node = findMethodLikeNode(root, searchOffset);
		if (node == null) {
			return;
		}

		this.targetNode = findEnclosingMethodNode(node, source, searchOffset);
	}

	/**
	 * Traverse up until a method-like AST node is found.
	 * @param root
	 * @param offset
	 */
	private ASTNode findMethodLikeNode(ASTNode root, int offset) {
		ASTNode node = NodeFinder.perform(root, offset, 0);
		while (node != null && !(node instanceof Block)) {
			if (isMethodLikeNode(node)) {
				return node;
			}
			node = node.getParent();
		}

		return null;
	}

	/**
	 * Check whether the given node is one of:
	 *     {@link ClassInstanceCreation},
	 *     {@link ConstructorInvocation},
	 *     {@link SuperConstructorInvocation},
	 *     {@link MethodInvocation},
	 *     {@link SuperMethodInvocation},
	 *     {@link MethodRef}.
	 *
	 * @param node
	 */
	private boolean isMethodLikeNode(ASTNode node) {
		return node instanceof MethodInvocation || node instanceof ClassInstanceCreation ||
				node instanceof SuperConstructorInvocation || node instanceof ConstructorInvocation ||
				node instanceof SuperMethodInvocation || node instanceof MethodRef;
	}

	/**
	 * Find the method-like node which encloses the offset.
	 * When there are multiple valid node enclosing the offset, the inner-most one
	 * will return only when the offset is in its arguments. For example:
	 *
	 * <pre>
	 *     System.out.println(new String(""|))
	 * </pre>
	 *
	 * In the above case, the inner class instance creation node will return.
	 *
	 *  <pre>
	 *     System.out.println(new |String(""))
	 * </pre>
	 *
	 * In the above case, the outer method invocation node will return.
	 *
	 * @param node method-like AST node
	 * @param source source code
	 * @param offset offset
	 */
	private ASTNode findEnclosingMethodNode(ASTNode node, String source, int offset) {
		if (isInArgumentList(node, source, offset)) {
			return node;
		}

		for (ASTNode parent = node.getParent(); parent != null && !(parent instanceof Block);
				parent = parent.getParent()) {
			if (!isMethodLikeNode(parent)) {
				continue;
			}

			if (isInArgumentList(parent, source, offset)) {
				return parent;
			}
		}

		return node;
	}

	/**
	 * Check whether the offset is enclosed in the input node arguments.
	 * @param node method-like AST node
	 * @param source source code
	 * @param offset offset
	 */
	private boolean isInArgumentList(ASTNode node, String source, int offset) {
		int[] argumentRange = findArgumentRange(node, source);
		if (argumentRange == null || argumentRange.length < 2) {
			return false;
		}

		return argumentRange[0] <= offset && argumentRange[1] >= offset;
	}

	/**
	 * Find the argument range of the method-like node. The range will not include the
	 * start and end brackets.
	 * @param node
	 * @param source
	 * @return an int array with length 2, where the first element denotes the start offset
	 *         and the second element denotes the end offset.
	 */
	private int[] findArgumentRange(ASTNode node, String source) {
		List<Expression> arguments = resolveArguments(node);
		if (arguments == null) {
			return null;
		}

		if (arguments.size() > 0) {
			Expression firstArg = arguments.get(0);
			Expression lastArg = arguments.get(arguments.size() - 1);
			return new int[]{ firstArg.getStartPosition(), lastArg.getStartPosition() + lastArg.getLength() };
		} else {
			ASTNode simpleNameNode = null;
			if (node instanceof MethodInvocation methodInvocation) {
				simpleNameNode = methodInvocation.getName();
			} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
				simpleNameNode = classInstanceCreation.getType();
			} else if (node instanceof SuperMethodInvocation superInvocation) {
				simpleNameNode = superInvocation.getName();
			} else if (node instanceof MethodRef methodRef) {
				simpleNameNode = methodRef.getName();
			} else if (node instanceof ConstructorInvocation) {
				simpleNameNode = node;
			} else if (node instanceof SuperConstructorInvocation) {
				simpleNameNode = node;
			}

			if (simpleNameNode == null) {
				return null;
			}
			int i = simpleNameNode.getStartPosition() + simpleNameNode.getLength();
			while (i < node.getStartPosition() + node.getLength()) {
				if (source.charAt(i) == '(') {
					return new int[]{ i + 1, node.getStartPosition() + node.getLength() - 1 };
				}
				i++;
			}
			return null;
		}
	}

	/**
	 * Get the method name string of the input method-like node.
	 * @param node
	 */
	private void resolveMethodName(ASTNode node) {
		if (node == null) {
			return;
		}

		if (node instanceof MethodInvocation methodInvocation) {
			this.methodName = methodInvocation.getName().getIdentifier();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			ITypeBinding binding = classInstanceCreation.getType().resolveBinding();
			if (binding != null) {
				this.methodName = binding.getErasure().getName();
			}
		} else if (node instanceof SuperMethodInvocation superInvocation) {
			this.methodName = superInvocation.getName().getIdentifier();
		} else if (node instanceof MethodRef methodRef) {
			this.methodName = methodRef.getName().getIdentifier();
		} else if (node instanceof ConstructorInvocation constructorInvocation) {
			IMethodBinding binding = constructorInvocation.resolveConstructorBinding();
			if (binding != null) {
				this.methodName = binding.getDeclaringClass().getName();
			}
		} else if (node instanceof SuperConstructorInvocation superConstructorInvocation) {
			IMethodBinding binding = superConstructorInvocation.resolveConstructorBinding();
			if (binding != null) {
				this.methodName = binding.getDeclaringClass().getName();
			}
		}
	}

	/**
	 * Get the declaring type names of the method-like node. Following names will be added:
	 *   <ul>
	 *     <li> The declaring type</li>
	 *     <li> All the super types</li>
	 *     <li> All the interfaces</li>
	 *   </ul>
	 *
	 * @param node
	 */
	private void resolveDeclaringTypeName(ASTNode node) {
		if (node == null) {
			return;
		}

		IMethodBinding methodBinding = null;
		if (node instanceof MethodInvocation methodInvocation) {
			methodBinding = methodInvocation.resolveMethodBinding();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			methodBinding = classInstanceCreation.resolveConstructorBinding();
		} else if (node instanceof SuperMethodInvocation superMethodInvocation) {
			methodBinding = superMethodInvocation.resolveMethodBinding();
		} else if (node instanceof SuperConstructorInvocation superConstructorInvocation) {
			methodBinding = superConstructorInvocation.resolveConstructorBinding();
		} else if (node instanceof ConstructorInvocation constructorInvocation) {
			methodBinding = constructorInvocation.resolveConstructorBinding();
		}

		if (methodBinding != null) {
			ITypeBinding declaringType = methodBinding.getDeclaringClass();
			List<String> typeNames = new ArrayList<>();
			for (ITypeBinding mInterface : declaringType.getInterfaces()) {
				String unqualifiedName = mInterface.getErasure().getName().replace(";", "");
				typeNames.add(unqualifiedName);
			}
			while (declaringType != null) {
				String unqualifiedName = declaringType.getErasure().getName().replace(";", "");
				typeNames.add(unqualifiedName);
				declaringType = declaringType.getSuperclass();
			}
			this.declaringTypeNames = typeNames;
		}
	}

	/**
	 * Get the argument list of the input method-like node.
	 * @param node
	 */
	private List<Expression> resolveArguments(ASTNode node) {
		if (node == null) {
			return null;
		}

		if (node instanceof MethodInvocation methodInvocation) {
			return methodInvocation.arguments();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			return classInstanceCreation.arguments();
		} else if (node instanceof SuperMethodInvocation superMethodInvocation) {
			return superMethodInvocation.arguments();
		} else if (node instanceof MethodRef methodRef) {
			return methodRef.parameters();
		} else if (node instanceof SuperConstructorInvocation superConstructorInvocation) {
			return superConstructorInvocation.arguments();
		} else if (node instanceof ConstructorInvocation constructorInvocation) {
			return constructorInvocation.arguments();
		}

		return null;
	}

	/**
	 * The signatures of the argument types resolved from the method binding.
	 * @param node
	 */
	private void resolveParameterTypes(ASTNode node) {
		if (node == null) {
			return;
		}

		IBinding binding = null;
		if (node instanceof MethodInvocation methodInvocation) {
			binding = methodInvocation.resolveMethodBinding();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			binding = classInstanceCreation.resolveConstructorBinding();
		} else if (node instanceof SuperMethodInvocation superMethodInvocation) {
			binding = superMethodInvocation.resolveMethodBinding();
		} else if (node instanceof MethodRef methodRef) {
			binding = methodRef.resolveBinding();
		} else if (node instanceof SuperConstructorInvocation superConstructorInvocation) {
			binding = superConstructorInvocation.resolveConstructorBinding();
		} else if (node instanceof ConstructorInvocation constructorInvocation) {
			binding = constructorInvocation.resolveConstructorBinding();
		}

		if (binding == null) {
			return;
		}

		if (binding instanceof IMethodBinding methodBinding) {
			if (methodBinding.isDefaultConstructor()) {
				// default constructor won't have IJavaElement
				this.parameterTypes = new String[0];
				return;
			}

			this.parameterTypesFromBinding = Arrays.stream(methodBinding.getParameterTypes()).map(type -> {
				String unqualifiedName = type.getErasure().getName();
				return unqualifiedName.replace(";", "");
			}).toArray(String[]::new);
		}

		IMethod method = (IMethod) binding.getJavaElement();
		if (method != null) {
			this.parameterTypes = Arrays.stream(method.getParameterTypes()).map(signature -> {
				return SignatureHelpUtils.getSimpleTypeName(signature);
			}).toArray(String[]::new);
		}
	}

	/**
	 * Guess the offset to trigger the completion.
	 * @param node
	 * @param unit
	 * @throws JavaModelException
	 */
	private void guessCompletionOffset(ASTNode node, ICompilationUnit unit) throws JavaModelException {
		IBuffer buffer = unit.getBuffer();
		if (node == null || buffer == null) {
			this.completionOffset = -1;
			return;
		}

		int startPosition = node.getStartPosition();
		int endPosition = startPosition + node.getLength();
		startPosition += getOptionalExpressionLength(node);
		for (int i = startPosition; i < endPosition; i++) {
			if (buffer.getChar(i) == '(') {
				// sometimes completion results from completion engine is not stable/predictable, so
				// we have a secondary choice here (outside bracket and inside bracket).
				if (node instanceof MethodInvocation || node instanceof SuperMethodInvocation || node instanceof MethodRef) {
					this.completionOffset = i;
					this.secondaryCompletionOffset = i + 1;
					return;
				} else if (node instanceof ClassInstanceCreation || node instanceof ConstructorInvocation ||
						node instanceof SuperConstructorInvocation) {
					this.completionOffset = i + 1;
					return;
				}
			}
		}
	}

	/**
	 * The optional expression length. It's used to remove the prior method node when
	 * it's a invocation chian. For example:
	 *
	 * <pre>
	 *     assertThat(foo).isEqualTo(|);
	 * </pre>
	 *
	 * In this case <code>assertThat(foo)</code> need to be excluded to get an accurate
	 * argument range.
	 * @param node
	 */
	private int getOptionalExpressionLength(ASTNode node) {
		Expression optionalExpression = null;
		if (node instanceof MethodInvocation methodInvocation) {
			optionalExpression = methodInvocation.getExpression();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			optionalExpression = classInstanceCreation.getExpression();
		} else if (node instanceof SuperConstructorInvocation superConstructorInvocation) {
			optionalExpression = superConstructorInvocation.getExpression();
		}
		if (optionalExpression == null) {
			return 0;
		}

		return optionalExpression.getLength();
	}

	/**
	 * Guess the argument ranges according to the actual code.
	 * @param unit
	 * @param completionOffset
	 * @throws JavaModelException
	 */
	private void guessArgumentRanges(ICompilationUnit unit, int completionOffset) throws JavaModelException {
		IBuffer buffer = unit.getBuffer();
		if (buffer == null || completionOffset == -1) {
			return;
		}

		int argumentStartPosition = completionOffset;
		// Make sure we will start inside argument left bracket.
		if (buffer.getChar(argumentStartPosition) == '(') {
			argumentStartPosition++;
		}

		String argumentLiterals = buffer.getText(argumentStartPosition, buffer.getLength() - argumentStartPosition);
		List<int[]> list = new ArrayList<>();
		int[] argumentRange = new int[]{argumentStartPosition, argumentStartPosition};
		Stack<Character> stack = new Stack<>();
		boolean hasArgument = false;
		for (int i = 0; i < argumentLiterals.length(); i++) {
			char c = argumentLiterals.charAt(i);
			if (!hasArgument && isArgumentChar(c)) {
				hasArgument = true;
			}
			switch (c) {
				case ',':
					if (stack.isEmpty()) {
						argumentRange[1] = argumentStartPosition + i;
						list.add(argumentRange);
						argumentRange = new int[]{argumentStartPosition + i + 1, argumentStartPosition + i + 1};
					}
					break;
				case '\'':
					i++;
					while (i < argumentLiterals.length()) {
						c = argumentLiterals.charAt(i);
						if (c == '\'') {
							break;
						} else if (c == '\\') {
							i += 2;
						} else {
							i++;
						}
					}
					break;
				case '"':
					String textBlockStart = argumentLiterals.substring(i, i + 3);
					// ignore all the characters in text block
					if (textBlockStart.equals("\"\"\"")) {
						int endIndex = argumentLiterals.indexOf("\"\"\"", i + 3);
						// in case we have \""" inside a text block
						while (endIndex > 0 && argumentLiterals.charAt(endIndex - 1) == '\\') {
							endIndex = argumentLiterals.indexOf("\"\"\"", endIndex + 3);
						}
						if (endIndex > 0) {
							i = endIndex + 2;
						} else {
							i = argumentLiterals.length() - 1;
						}
					} else {
						i++;
						while (i < argumentLiterals.length()) {
							c = argumentLiterals.charAt(i);
							if (c == '"') {
								break;
							} else if (c == '\\') {
								i += 2;
							} else {
								i++;
							}
						}
					}
					break;
				case '(':
					stack.add(c);
					break;
				case ')':
					if (!stack.isEmpty() && stack.peek() == '(') {
						stack.pop();
					} else {
						if (hasArgument) {
							argumentRange[1] = argumentStartPosition + i;
							list.add(argumentRange);
						}
						this.argumentRanges = list;
						return;
					}
					break;
				case '[':
					stack.add(c);
					break;
				case ']':
					if (!stack.isEmpty() && stack.peek() == '[') {
						stack.pop();
					} else {
						if (hasArgument) {
							argumentRange[1] = argumentStartPosition + i;
							list.add(argumentRange);
						}
						this.argumentRanges = list;
						return;
					}
					break;
				case '{':
					stack.add(c);
					break;
				case '}':
					if (!stack.isEmpty() && stack.peek() == '{') {
						stack.pop();
					} else {
						if (hasArgument) {
							argumentRange[1] = argumentStartPosition + i;
							list.add(argumentRange);
						}
						this.argumentRanges = list;
						return;
					}
					break;
				case '<':
					i++;
					while (i < argumentLiterals.length()) {
						c = argumentLiterals.charAt(i);
						if (c == '>') {
							break;
						}
						i++;
					}
					break;
				default:
					break;
			}
		}
		if (hasArgument) {
			argumentRange[1] = argumentStartPosition + argumentLiterals.length() - 1;
			list.add(argumentRange);
		}
		this.argumentRanges = list;
	}

	private boolean isArgumentChar(char c) {
		return !Character.isWhitespace(c) &&
			c != ')' && c != ']' && c != '}' & c != ';';
	}

	/**
	 * The offset used to trigger the completion.
	 */
	public int completionOffset() {
		return completionOffset;
	}

	/**
	 * Since there is no concrete rules about triggering completion at which
	 * place could get the signature help. The secondary completion offset will be
	 * be used if the {@link #completionOffset()} does not work.
	 */
	public int secondaryCompletionOffset() {
		return secondaryCompletionOffset;
	}

	/**
	 * The ranges of each arguments from the code.
	 * Note: this is parsed from user's actual code, not the ranges of the AST arguments.
	 */
	public List<int[]> argumentRanges() {
		return argumentRanges;
	}

	/**
	 * The method name used to collect the signature from the completion result
	 */
	public String methodName() {
		return methodName;
	}

	/**
	 * The declaring type name of the method invocation. It's used to filter methods from
	 * different types but with same names that provided by the completion engine.
	 */
	public List<String> declaringTypeNames() {
		return declaringTypeNames;
	}

	/**
	 * The argument nodes parsed from AST.
	 */
	public List<Expression> arguments() {
		return arguments;
	}

	/**
	 * The parameter type names from {@link IMethod}.
	 */
	public String[] parameterTypes() {
		return parameterTypes;
	}

	/**
	 * The parameter type names resolved from {@link IMethodBinding}.
	 * Since the completion engine will return different results when it comes to
	 * generic types. For example:
	 * <pre>
	 *     Arrays.asList(foo);
	 * </pre>
	 * The signature from completion engine is <code>(T... a)</code>.
	 * While for:
	 * <pre>
	 *     HashMap.put("", "");
	 * </pre>
	 * The signature from completion engine is <code>(String key, String value)</code>.
	 *
	 * So two versions of parameter types are saved here to make sure we won't miss a match.
	 */
	public String[] parameterTypesFromBinding() {
		return parameterTypesFromBinding;
	}

	/**
	 * The method-like AST node. It might be one of the following type:
	 *     {@link ClassInstanceCreation},
	 *     {@link ConstructorInvocation},
	 *     {@link SuperConstructorInvocation},
	 *     {@link MethodInvocation},
	 *     {@link SuperMethodInvocation},
	 *     {@link MethodRef}.
	 * <code>null</code> means no valid node could be found from AST.
	 */
	public ASTNode targetNode() {
		return targetNode;
	}
}
