/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Microsoft Corporation - extract to a base class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.OpenableElementInfo;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public abstract class BaseDocumentLifeCycleHandler {

	public static final String DOCUMENT_LIFE_CYCLE_JOBS = "DocumentLifeCycleJobs";
	public static final String PUBLISH_DIAGNOSTICS_JOBS = "DocumentLifeCyclePublishDiagnosticsJobs";

	private CoreASTProvider sharedASTProvider;
	private WorkspaceJob validationTimer;
	private WorkspaceJob publishDiagnosticsJob;
	private Set<ICompilationUnit> toReconcile = new HashSet<>();

	public BaseDocumentLifeCycleHandler(boolean delayValidation) {
		this.sharedASTProvider = CoreASTProvider.getInstance();
		if (delayValidation) {
			this.validationTimer = new WorkspaceJob("Validate documents") {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
					return performValidation(monitor);
				}

				/* (non-Javadoc)
				 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
				 */
				@Override
				public boolean belongsTo(Object family) {
					return DOCUMENT_LIFE_CYCLE_JOBS.equals(family);
				}
			};
			this.publishDiagnosticsJob = new WorkspaceJob("Publish Diagnostics") {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
					return publishDiagnostics(monitor);
				}

				/* (non-Javadoc)
				 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
				 */
				@Override
				public boolean belongsTo(Object family) {
					return PUBLISH_DIAGNOSTICS_JOBS.equals(family);
				}
			};
		}
	}

	public abstract BaseDiagnosticsHandler createDiagnosticsHandler(ICompilationUnit unit);

	public abstract boolean isSyntaxMode(ICompilationUnit unit);

	public abstract ICompilationUnit resolveCompilationUnit(String uri);

	protected void triggerValidation(ICompilationUnit cu) throws JavaModelException {
		triggerValidation(cu, 400);
	}

	protected void triggerValidation(ICompilationUnit cu, long delay) throws JavaModelException {
		synchronized (toReconcile) {
			toReconcile.add(cu);
			if (!cu.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			sharedASTProvider.setActiveJavaElement(cu);
		}
		if (validationTimer != null) {
			validationTimer.cancel();
			ISchedulingRule rule = getRule(toReconcile);
			if (publishDiagnosticsJob != null) {
				publishDiagnosticsJob.cancel();
				publishDiagnosticsJob.setRule(rule);
			}
			validationTimer.setRule(rule);
			validationTimer.schedule(delay);
		} else {
			performValidation(new NullProgressMonitor());
		}
	}

	private ISchedulingRule getRule(Set<ICompilationUnit> units) {
		ISchedulingRule result = null;
		IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
		for (ICompilationUnit unit : units) {
			if (unit.getResource() != null) {
				ISchedulingRule rule = ruleFactory.createRule(unit.getResource());
				result = MultiRule.combine(rule, result);
			}
		}
		return result;
	}

	private IStatus performValidation(IProgressMonitor monitor) throws JavaModelException {
		long start = System.currentTimeMillis();

		List<ICompilationUnit> cusToReconcile;
		synchronized (toReconcile) {
			if (toReconcile.isEmpty()) {
				return Status.OK_STATUS;
			}
			cusToReconcile = new ArrayList<>(toReconcile.size());
			cusToReconcile.addAll(toReconcile);
			toReconcile.clear();
		}
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		// first reconcile all units with content changes
		SubMonitor progress = SubMonitor.convert(monitor, cusToReconcile.size() + 1);
		for (ICompilationUnit cu : cusToReconcile) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			cu.makeConsistent(progress);
			//cu.reconcile(ICompilationUnit.NO_AST, false, null, progress.newChild(1));
		}
		JavaLanguageServerPlugin.logInfo("Reconciled " + toReconcile.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		if (publishDiagnosticsJob != null) {
			publishDiagnosticsJob.cancel();
			try {
				publishDiagnosticsJob.join();
			} catch (InterruptedException e) {
				// ignore
			}
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			publishDiagnosticsJob.schedule(400);
		} else {
			return publishDiagnostics(new NullProgressMonitor());
		}
		return Status.OK_STATUS;
	}

	private IStatus publishDiagnostics(IProgressMonitor monitor) throws JavaModelException {
		long start = System.currentTimeMillis();
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		this.sharedASTProvider.disposeAST();
		List<ICompilationUnit> toValidate = Arrays.asList(JavaCore.getWorkingCopies(null));
		if (toValidate.isEmpty()) {
			return Status.OK_STATUS;
		}
		SubMonitor progress = SubMonitor.convert(monitor, toValidate.size() + 1);
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		for (ICompilationUnit rootToValidate : toValidate) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			CompilationUnit astRoot = this.sharedASTProvider.getAST(rootToValidate, CoreASTProvider.WAIT_YES, monitor);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (astRoot != null) {
				// report errors, even if there are no problems in the file: The client need to know that they got fixed.
				ICompilationUnit unit = (ICompilationUnit) astRoot.getTypeRoot();
				publishDiagnostics(unit, progress.newChild(1));
			}
		}
		JavaLanguageServerPlugin.logInfo("Validated " + toValidate.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
		return Status.OK_STATUS;
	}

	private void publishDiagnostics(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		final BaseDiagnosticsHandler handler = createDiagnosticsHandler(unit);
		WorkingCopyOwner wcOwner = new WorkingCopyOwner() {

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				if (!monitor.isCanceled()) {
					ICompilationUnit original = workingCopy.getPrimary();
					IResource resource = original.getResource();
					if (resource instanceof IFile) {
						return new DocumentAdapter(workingCopy, (IFile) resource);
					}
				}
				return DocumentAdapter.Null;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#getProblemRequestor(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}

		};
		int flags = ICompilationUnit.FORCE_PROBLEM_DETECTION | ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY;
		unit.reconcile(ICompilationUnit.NO_AST, flags, wcOwner, monitor);
	}

	public void didClose(DidCloseTextDocumentParams params) {
		ISchedulingRule rule = JDTUtils.getRule(params.getTextDocument().getUri());
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleClosed(params);
				}
			}, rule, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document close ", e);
		}
	}

	public void didOpen(DidOpenTextDocumentParams params) {
		ISchedulingRule rule = JDTUtils.getRule(params.getTextDocument().getUri());
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleOpen(params);
				}
			}, rule, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document open ", e);
		}
	}

	public void didChange(DidChangeTextDocumentParams params) {
		ISchedulingRule rule = JDTUtils.getRule(params.getTextDocument().getUri());
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleChanged(params);
				}
			}, rule, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document change ", e);
		}
	}

	public void didSave(DidSaveTextDocumentParams params) {
		ISchedulingRule rule = JDTUtils.getRule(params.getTextDocument().getUri());
		try {
			JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					handleSaved(params);
				}
			}, rule, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Handle document save ", e);
		}
	}

	public ICompilationUnit handleOpen(DidOpenTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = resolveCompilationUnit(uri);
		if (unit == null || unit.getResource() == null || unit.getResource().isDerived()) {
			return unit;
		}
		try {
			// The open event can happen before the workspace element added event when a new file is added.
			// checks if the underlying resource exists and refreshes to sync the newly created file.
			if (!unit.getResource().isAccessible()) {
				try {
					unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
					if (unit.getResource().exists()) {
						IJavaElement parent = unit.getParent();
						if (parent instanceof PackageFragment) {
							PackageFragment pkg = (PackageFragment) parent;
							OpenableElementInfo elementInfo = (OpenableElementInfo) pkg.getElementInfo();
							elementInfo.addChild(unit);
						}
					} else { // File not exists
						return unit;
					}
				} catch (CoreException e) {
					// ignored
				}
			}

			//			DiagnosticsHandler problemRequestor = new DiagnosticsHandler(connection, unit.getResource(), reportOnlySyntaxErrors);
			unit.becomeWorkingCopy(new NullProgressMonitor());
			IBuffer buffer = unit.getBuffer();
			String newContent = params.getTextDocument().getText();
			if (buffer != null && !buffer.getContents().equals(newContent)) {
				buffer.setContents(newContent);
			}
			triggerValidation(unit);
			// see https://github.com/redhat-developer/vscode-java/issues/274
			checkPackageDeclaration(uri, unit);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error while opening document. URI: " + uri, e);
		}

		return unit;
	}

	public ICompilationUnit handleChanged(DidChangeTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);

		if (unit == null || !unit.isWorkingCopy() || params.getContentChanges().isEmpty() || unit.getResource().isDerived()) {
			return unit;
		}

		try {
			if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			for (TextDocumentContentChangeEvent changeEvent : contentChanges) {

				Range range = changeEvent.getRange();
				int length;

				if (range != null) {
					length = changeEvent.getRangeLength().intValue();
				} else {
					// range is optional and if not given, the whole file content is replaced
					length = unit.getSource().length();
					range = JDTUtils.toRange(unit, 0, length);
				}

				int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), range.getStart().getLine(), range.getStart().getCharacter());

				TextEdit edit = null;
				String text = changeEvent.getText();
				if (length == 0) {
					edit = new InsertEdit(startOffset, text);
				} else if (text.isEmpty()) {
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, text);
				}
				IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
				edit.apply(document, TextEdit.NONE);

			}
			triggerValidation(unit);
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
			JavaLanguageServerPlugin.logException("Error while handling document change. URI: " + uri, e);
		}

		return unit;
	}

	public ICompilationUnit handleClosed(DidCloseTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			return unit;
		}
		try {
			synchronized (toReconcile) {
				toReconcile.remove(unit);
			}
			if (isSyntaxMode(unit) || !unit.exists() || unit.getResource().isDerived()) {
				createDiagnosticsHandler(unit).clearDiagnostics();
			} else if (hasUnsavedChanges(unit)) {
				unit.discardWorkingCopy();
				unit.becomeWorkingCopy(new NullProgressMonitor());
				publishDiagnostics(unit, new NullProgressMonitor());
			}
			if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			unit.discardWorkingCopy();
			if (JDTUtils.isDefaultProject(unit)) {
				File f = new File(unit.getUnderlyingResource().getLocationURI());
				if (!f.exists()) {
					unit.delete(true, null);
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error while handling document close. URI: " + uri, e);
		}

		return unit;
	}

	private boolean hasUnsavedChanges(ICompilationUnit unit) throws CoreException {
		if (!unit.hasUnsavedChanges()) {
			return false;
		}
		unit.getResource().refreshLocal(IResource.DEPTH_ZERO, null);
		return unit.getResource().exists();
	}

	public ICompilationUnit handleSaved(DidSaveTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			JavaLanguageServerPlugin.logError(uri + " does not resolve to a ICompilationUnit");
			return unit;
		}
		// see https://github.com/redhat-developer/vscode-java/issues/274
		unit = checkPackageDeclaration(uri, unit);
		if (unit.isWorkingCopy()) {
			try {
				if (unit.getUnderlyingResource() != null && unit.getUnderlyingResource().exists()) {
					try {
						unit.getUnderlyingResource().refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException("Error while refreshing resource. URI: " + uri, e);
					}
				}
				unit.discardWorkingCopy();
				unit.becomeWorkingCopy(new NullProgressMonitor());
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Error while handling document save. URI: " + uri, e);
			}
		}

		return unit;
	}

	private ICompilationUnit checkPackageDeclaration(String uri, ICompilationUnit unit) {
		if (unit.getResource() != null && unit.getJavaProject() != null && unit.getJavaProject().getProject().getName().equals(ProjectsManager.DEFAULT_PROJECT_NAME)) {
			try {
				CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
				IProblem[] problems = astRoot.getProblems();
				for (IProblem problem : problems) {
					if (problem.getID() == IProblem.PackageIsNotExpectedPackage) {
						IResource file = unit.getResource();
						boolean toRemove = file.isLinked();
						if (toRemove) {
							IPath path = file.getParent().getProjectRelativePath();
							if (path.segmentCount() > 0 && JDTUtils.SRC.equals(path.segments()[0])) {
								String packageNameResource = path.removeFirstSegments(1).toString().replace(JDTUtils.PATH_SEPARATOR, JDTUtils.PERIOD);
								path = file.getLocation();
								if (path != null && path.segmentCount() > 0) {
									path = path.removeLastSegments(1);
									String pathStr = path.toString().replace(JDTUtils.PATH_SEPARATOR, JDTUtils.PERIOD);
									if (pathStr.endsWith(packageNameResource)) {
										toRemove = false;
									}
								}
							}
						}
						if (toRemove) {
							file.delete(true, new NullProgressMonitor());
							if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
								sharedASTProvider.disposeAST();
							}
							unit.discardWorkingCopy();
							unit = JDTUtils.resolveCompilationUnit(uri);
							unit.becomeWorkingCopy(new NullProgressMonitor());
							triggerValidation(unit);
						}
						break;
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return unit;
	}
}
