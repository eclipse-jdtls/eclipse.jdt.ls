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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.OpenableElementInfo;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.MovingAverage;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalUtils;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public abstract class BaseDocumentLifeCycleHandler {

	public static final String DOCUMENT_LIFE_CYCLE_JOBS = "DocumentLifeCycleJobs";
	public static final String PUBLISH_DIAGNOSTICS_JOBS = "DocumentLifeCyclePublishDiagnosticsJobs";
	public static final String RENAME_FILE_TO_TYPE = "renameFileToType";

	/**
	 * The max & init value of adaptive debounce time for document lifecycle job.
	 */
	private static final long DOCUMENT_LIFECYCLE_MAX_DEBOUNCE = 400; /*ms*/

	/**
	 * The min & init value of adaptive debounce time for publish diagnostic job.
	 */
	private static final long PUBLISH_DIAGNOSTICS_MIN_DEBOUNCE = 400; /*ms*/

	/**
	 * The max value of adaptive debounce time for publish diagnostic job.
	 */
	private static final long PUBLISH_DIAGNOSTICS_MAX_DEBOUNCE = 2000; /*ms*/

	private CoreASTProvider sharedASTProvider;
	private Job validationTimer;
	private Job publishDiagnosticsJob;
	private Set<ICompilationUnit> toReconcile = new HashSet<>();
	private Set<ICompilationUnit> toValidate = ConcurrentHashMap.newKeySet();
	private Map<String, Integer> documentVersions = new HashMap<>();
	private Map<String, Integer> lastSyncedDocumentLengths = new ConcurrentHashMap<>();
	private MovingAverage movingAverageForValidation = new MovingAverage(DOCUMENT_LIFECYCLE_MAX_DEBOUNCE);
	private MovingAverage movingAverageForDiagnostics = new MovingAverage(PUBLISH_DIAGNOSTICS_MIN_DEBOUNCE);
	protected final PreferenceManager preferenceManager;
	private Object reconcileLock = new Object();

	public BaseDocumentLifeCycleHandler(PreferenceManager preferenceManager, boolean delayValidation) {
		this.preferenceManager = preferenceManager;
		this.sharedASTProvider = CoreASTProvider.getInstance();
		if (delayValidation) {
			this.validationTimer = new Job("Validate documents") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						long startTime = System.nanoTime();
						IStatus status = performValidation(monitor);
						if (status.getSeverity() != IStatus.CANCEL) {
							long elapsedTime = System.nanoTime() - startTime;
							movingAverageForValidation.update(elapsedTime / 1_000_000);
						}
						return status;
					} catch (JavaModelException e) {
						return e.getStatus();
					}
				}

				/* (non-Javadoc)
				 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
				 */
				@Override
				public boolean belongsTo(Object family) {
					return DOCUMENT_LIFE_CYCLE_JOBS.equals(family);
				}
			};
			this.publishDiagnosticsJob = new PublishDiagnosticJob();
		}
	}

	public abstract BaseDiagnosticsHandler createDiagnosticsHandler(ICompilationUnit unit);

	public abstract boolean isSyntaxMode(ICompilationUnit unit);

	public abstract ICompilationUnit resolveCompilationUnit(String uri);

	protected void triggerValidation(ICompilationUnit cu) throws JavaModelException {
		triggerValidation(cu, getDocumentLifecycleDelay());
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
			if (publishDiagnosticsJob != null) {
				publishDiagnosticsJob.cancel();
				publishDiagnosticsJob = new PublishDiagnosticJob();
			}
			validationTimer.schedule(delay);
		} else {
			performValidation(new NullProgressMonitor());
		}
	}

	private long getDocumentLifecycleDelay() {
		return Math.min(DOCUMENT_LIFECYCLE_MAX_DEBOUNCE, Math.round(1.5 * movingAverageForValidation.value));
	}

	/**
	 * @return the delay time of the publish diagnostics job. The value ranges in
	 * ({@link #PUBLISH_DIAGNOSTICS_MIN_DEBOUNCE}, {@link #PUBLISH_DIAGNOSTICS_MAX_DEBOUNCE}) ms.
	 */
	private long getPublishDiagnosticsDelay() {
		return Math.min(
			Math.max(PUBLISH_DIAGNOSTICS_MIN_DEBOUNCE, Math.round(1.5 * movingAverageForDiagnostics.value)),
			PUBLISH_DIAGNOSTICS_MAX_DEBOUNCE
		);
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
		synchronized(reconcileLock) {
			for (ICompilationUnit cu : cusToReconcile) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				cu.makeConsistent(progress);
				toValidate.add(cu);
				//cu.reconcile(ICompilationUnit.NO_AST, false, null, progress.newChild(1));
			}
		}

		JavaLanguageServerPlugin.logInfo("Reconciled " + cusToReconcile.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
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
			publishDiagnosticsJob.schedule(getPublishDiagnosticsDelay());
		} else {
			return publishDiagnostics(new NullProgressMonitor());
		}
		return Status.OK_STATUS;
	}

	public IStatus validateDocument(String uri, boolean debounce, IProgressMonitor monitor) throws JavaModelException {
		ICompilationUnit unit = resolveCompilationUnit(uri);
		if (unit == null || unit.getResource() == null || unit.getResource().isDerived()) {
			return Status.OK_STATUS;
		}

		toValidate.add(unit);
		if (!unit.equals(sharedASTProvider.getActiveJavaElement())) {
			sharedASTProvider.disposeAST();
		}
		sharedASTProvider.setActiveJavaElement(unit);
		if (debounce && publishDiagnosticsJob != null) {
			publishDiagnosticsJob.cancel();
			publishDiagnosticsJob.setRule(null);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			publishDiagnosticsJob.schedule(getPublishDiagnosticsDelay());
			return Status.OK_STATUS;
		}

		return publishDiagnostics(monitor);
	}
	public IStatus publishDiagnostics(IProgressMonitor monitor) throws JavaModelException {
		long start = System.currentTimeMillis();
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		Set<ICompilationUnit> validateCopy = new LinkedHashSet<>(toValidate);
		// LinkedHashSet ensures explicitly requested CUs to validate are processed first
		// as they're likely to be the one user is editing at the moment.
		if (preferenceManager.getPreferences().isValidateAllOpenBuffersOnChanges()) {
			toValidate.addAll(Arrays.asList(JavaCore.getWorkingCopies(null)));
		}
		if (validateCopy.isEmpty()) {
			return Status.OK_STATUS;
		}
		SubMonitor progress = SubMonitor.convert(monitor, validateCopy.size() + 1);
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		for (ICompilationUnit rootToValidate : validateCopy) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			publishDiagnostics(rootToValidate, progress.newChild(1));
			toValidate.remove(rootToValidate);
		}
		JavaLanguageServerPlugin.logInfo("Validated " + validateCopy.size() + ". Took " + (System.currentTimeMillis() - start) + " ms");
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
					if (resource instanceof IFile file) {
						return new DocumentAdapter(workingCopy, file);
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
		synchronized(reconcileLock) {
			unit.reconcile(ICompilationUnit.NO_AST, flags, wcOwner, monitor);
		}
	}

	public void didClose(DidCloseTextDocumentParams params) {
		documentVersions.remove(params.getTextDocument().getUri());
		lastSyncedDocumentLengths.remove(params.getTextDocument().getUri());
		handleClosed(params);
	}

	public void didOpen(DidOpenTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		documentVersions.put(uri, params.getTextDocument().getVersion());
		lastSyncedDocumentLengths.remove(params.getTextDocument().getUri());
		IFile resource = JDTUtils.findFile(uri);
		if (resource != null) { // Open a managed file from the existing projects.
			handleOpen(params);
		} else { // Open an unmanaged file, use a workspace runnable to mount it to default project or invisible project.
			try {
				ResourcesPlugin.getWorkspace().run((IWorkspaceRunnable) monitor -> handleOpen(params), null, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Handle document open ", e);
			}
		}
	}

	public void didChange(DidChangeTextDocumentParams params) {
		documentVersions.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion());
		handleChanged(params);
	}

	public void didSave(DidSaveTextDocumentParams params) {
		String documentUri = params.getTextDocument().getUri();
		lastSyncedDocumentLengths.remove(documentUri);
		IFile file = JDTUtils.findFile(params.getTextDocument().getUri());
		if (file != null && !Objects.equals(ProjectsManager.getDefaultProject(), file.getProject())) {
			// no need for a workspace runnable, change is trivial
			handleSaved(params);
		} else {
			// some refactorings may be applied by the way, wrap those in a WorkspaceRunnable
			try {
				JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
				ResourcesPlugin.getWorkspace().run((IWorkspaceRunnable) monitor -> handleSaved(params), null, IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Handle document save ", e);
			}
		}
	}

	public static void handleFileRenameForTypeDeclaration(String documentUri) {
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(documentUri);
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		if (astRoot == null) {
			return;
		}
		IProblem[] problems = astRoot.getProblems();
		Optional<IProblem> desiredProblem = Arrays.stream(problems).filter(p -> p.getID() == IProblem.PublicClassMustMatchFileName).findFirst();
		long publicTopLevelTypeCount = 1;
		try {
			publicTopLevelTypeCount = Stream.of(cu.getTypes()).filter(t -> {
				try {
					return JdtFlags.isPublic(t);
				} catch (JavaModelException e) {
					return false;
				}
			}).count();
		} catch (JavaModelException e) {
			// continue
		}
		if (desiredProblem.isPresent() && publicTopLevelTypeCount == 1) {
			IProblem renameProblem = desiredProblem.get();
			String newName = renameProblem.getArguments()[1];
			String oldName = cu.getElementName();
			int index = oldName.lastIndexOf(".");
			String extension = index > 0 ? oldName.substring(index) : ".java";
			String newUri = documentUri.replace(oldName, newName + extension);
			WorkspaceEdit edit = new WorkspaceEdit(List.of(Either.forRight(new RenameFile(documentUri, newUri))));
			edit.setChanges(Collections.emptyMap());
			final boolean applyNow = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceApplyEditSupported();
			if (applyNow) {
				JavaLanguageServerPlugin.getInstance().getClientConnection().applyWorkspaceEdit(edit);
			}
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
					refreshLocalResource(unit.getResource(), IResource.DEPTH_ZERO, new NullProgressMonitor());
					if (unit.getResource().exists()) {
						IJavaElement parent = unit.getParent();
						if (parent instanceof PackageFragment pkg) {
							OpenableElementInfo elementInfo = (OpenableElementInfo) pkg.getElementInfo();
							elementInfo.addChild(unit);
						}
					}
				} catch (CoreException e) {
					// ignored
				}
			}

			// Update the static imports of current file as the favorite static members.
			CompletionProposalUtils.addStaticImportsAsFavoriteImports(unit);
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
			inferInvisibleProjectSourceRoot(unit);
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
				// We call clearReconciliation here in an attempt to prevent getAST calls on other threads
				// from caching outdated AST after we just called disposeAST. See also:
				// https://github.com/eclipse/eclipse.jdt.ls/issues/1918
				// https://github.com/eclipse/eclipse.jdt.ls/pull/2714#discussion_r1234817900
				sharedASTProvider.clearReconciliation();
				sharedASTProvider.disposeAST();
				sharedASTProvider.clearReconciliation();
				CodeActionHandler.codeActionStore.clear();
			}

			if (!preferenceManager.getClientPreferences().skipTextEventPropagation()) {
				int currentBufferLength = unit.getBuffer().getLength();
				if (lastSyncedDocumentLengths.containsKey(uri) && lastSyncedDocumentLengths.get(uri) != currentBufferLength) {
					/**
					 * The didChange handler is the only owner that has the responsibility
					 * of synchronizing the client changes with the buffer. If the last
					 * synchronized document length in the didChange handler does not
					 * match the current buffer length, this indicates that the document
					 * buffer has been modified by an unexpected program and has become
					 * inconsistent with the client document.
					 */
					JavaLanguageServerPlugin.logError("Document on language server is out-of-sync: " + unit.getElementName());
				}
				List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
				for (TextDocumentContentChangeEvent changeEvent : contentChanges) {

					Range range = changeEvent.getRange();
					int length;
					IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
					final int startOffset;
					if (range != null) {
						Position start = range.getStart();
						startOffset = JsonRpcHelpers.toOffset(document, start.getLine(), start.getCharacter());
						length = DiagnosticsHelper.getLength(unit, range);
					} else {
						// range is optional and if not given, the whole file content is replaced
						length = unit.getSource().length();
						startOffset = 0;
					}

					TextEdit edit = null;
					String text = changeEvent.getText();
					if (length == 0) {
						edit = new InsertEdit(startOffset, text);
					} else if (text.isEmpty()) {
						edit = new DeleteEdit(startOffset, length);
					} else {
						edit = new ReplaceEdit(startOffset, length, text);
					}
					edit.apply(document, TextEdit.NONE);
				}
				lastSyncedDocumentLengths.put(uri, unit.getBuffer().getLength());
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
			toValidate.remove(unit);
			if (isSyntaxMode(unit) || !unit.exists() || unit.getResource().isDerived()) {
				createDiagnosticsHandler(unit).clearDiagnostics();
			} else if (hasUnsavedChanges(unit)) {
				unit.discardWorkingCopy();
				unit.becomeWorkingCopy(new NullProgressMonitor());
				publishDiagnostics(unit, new NullProgressMonitor());
			}
			sharedASTProvider.disposeAST();
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
		refreshLocalResource(unit.getResource(), IResource.DEPTH_ZERO, new NullProgressMonitor());
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
						refreshLocalResource(unit.getUnderlyingResource(), IResource.DEPTH_ZERO, new NullProgressMonitor());
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
				if (astRoot == null) {
					return unit;
				}
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

	/**
	 * Infer the source root when the input compilation unit belongs to an
	 * invisible project. See {@link BaseDocumentLifeCycleHandler#needInferSourceRoot()}
	 * for when the infer action will happen.
	 * @param unit compilation unit
	 */
	private void inferInvisibleProjectSourceRoot(ICompilationUnit unit) {
		IJavaProject javaProject = unit.getJavaProject();
		if (javaProject == null) {
			return;
		}

		IProject project = javaProject.getProject();
		if (ProjectUtils.isUnmanagedFolder(project)) {
			PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
			List<String> sourcePaths = preferencesManager.getPreferences().getInvisibleProjectSourcePaths();

			// user already set the source paths manually, we don't infer it anymore.
			if (sourcePaths != null) {
				return;
			}

			boolean needToInfer = needInferSourceRoot(javaProject, unit);
			if (!needToInfer) {
				return;
			}

			IPath unitPath = unit.getResource().getLocation();
			InvisibleProjectImporter.inferSourceRoot(javaProject, unitPath);
		}
	}

	/**
	 * Checks if it's necessary to infer a source root based on the compilation unit.
	 * Returns true when:
	 * <ul>
     *   <li>The compilation unit is not on the classpath, but belongs to the Java project.</li>
     *   <li>The compilation unit is on the classpath, and all the compilation units of its belonging
	 *       package fragment have {@value IProblem#PackageIsNotExpectedPackage} errors.</li>
     * </ul>
	 * @param javaProject Java project.
	 * @param unit compilation unit.
	 */
	private boolean needInferSourceRoot(IJavaProject javaProject, ICompilationUnit unit) {
		if (javaProject.isOnClasspath(unit)) {
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
			if (astRoot == null) {
				return false;
			}
			IProblem[] problems = astRoot.getProblems();
			boolean isPackageNotMatch = Arrays.stream(problems)
					.anyMatch(p -> p.getID() == IProblem.PackageIsNotExpectedPackage);
			if (!isPackageNotMatch) {
				return false;
			}

			IJavaElement parent = unit.getParent();
			if (parent == null || !(parent instanceof IPackageFragment)) {
				return false;
			}
			try {
				ICompilationUnit[] children = ((IPackageFragment) parent).getCompilationUnits();
				for (ICompilationUnit child : children) {
					IResource resource = child.getResource();
					if (resource == null) {
						continue;
					}

					IMarker[] markers = resource.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_ZERO);
					boolean hasPackageNotMatchError = Arrays.stream(markers).anyMatch(m -> {
						return m.getAttribute("id", 0) == IProblem.PackageIsNotExpectedPackage;
					});

					// only infer source root when all the compilation units have the package not match error.
					if (!hasPackageNotMatchError) {
						return false;
					}
				}
				return true;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		} else {
			IProject project = javaProject.getProject();
			IPath projectRealFolder = ProjectUtils.getProjectRealFolder(project);
			IPath unitPath = unit.getResource().getLocation();
			return projectRealFolder.isPrefixOf(unitPath);
		}

		return false;
	}

	/**
	 * Copied from org.eclipse.core.internal.resources.Resource.refreshLocal(int, IProgressMonitor),
	 * but set the scheduling rule to null while refreshing a file resource.
	 */
	private void refreshLocalResource(IResource resource, int depth, IProgressMonitor monitor) throws CoreException {
		if (resource instanceof org.eclipse.core.internal.resources.File file) {
			if (!file.getLocalManager().fastIsSynchronized(file)) {
				String message = NLS.bind(org.eclipse.core.internal.utils.Messages.resources_refreshing, file.getFullPath());
				SubMonitor progress = SubMonitor.convert(monitor, 100).checkCanceled();
				progress.subTask(message);
				boolean build = false;
				SubMonitor split = progress.split(1);
				final ISchedulingRule rule = null;
				final Workspace workspace = (Workspace) file.getWorkspace();
				try {
					workspace.prepareOperation(rule, split);
					if (!file.getProject().isAccessible()) {
						return;
					}
					if (!file.exists() && file.isFiltered()) {
						return;
					}
					workspace.beginOperation(true);
					build = file.getLocalManager().refresh(file, IResource.DEPTH_ZERO, true, monitor);
				} catch (OperationCanceledException e) {
					throw e;
				} finally {
					monitor.done();
					workspace.endOperation(rule, build);
				}
			}
		} else { // falls back to the default implementation for non-files
			resource.refreshLocal(depth, monitor);
		}
	}

	/**
	 * @author mistria
	 *
	 */
	private final class PublishDiagnosticJob extends Job {
		/**
		 * @param rule
		 */
		private PublishDiagnosticJob() {
			super("Publish Diagnostics");
		}

		@Override
		public IStatus run(IProgressMonitor monitor) {
			try {
				long startTime = System.nanoTime();
				IStatus status = publishDiagnostics(monitor);
				if (status.getSeverity() != IStatus.CANCEL) {
					long elapsedTime = System.nanoTime() - startTime;
					movingAverageForDiagnostics.update(elapsedTime / 1_000_000);
				}
				return status;
			} catch (JavaModelException e) {
				return e.getStatus();
			}
		}

		/* (non-Javadoc)
		 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
		 */
		@Override
		public boolean belongsTo(Object family) {
			return PUBLISH_DIAGNOSTICS_JOBS.equals(family);
		}
	}

	/**
	 * Can be passed to requests that are sensitive to document changes
	 * in order to monitor the version and cancel the request if necessary.
	 */
	public class DocumentMonitor {

		private final String uri;
		private final Integer initialVersion;

		public DocumentMonitor(String uri) {
			this.uri = uri;
			this.initialVersion = documentVersions.get(uri);
		}

		/**
		 * @return {@code true} if the document has changed since the creation
		 * of this monitor, {@code false} otherwise.
		 */
		public boolean hasChanged() {
			Integer currentVersion = documentVersions.get(uri);
			// If the initial and current version is null, it would indicate that
			// the document is not open. In such cases, the LSP spec still says:
			// "a server's ability to fulfill requests is independent of whether
			// a text document is open or closed". In order to service such
			// requests, we have to assume that a closed document has not changed.
			// See also: https://github.com/eclipse/eclipse.jdt.ls/discussions/2706
			return !Objects.equals(initialVersion, currentVersion);
		}

		/**
		 * If the document {@link #hasChanged()}, throws a {@link ResponseErrorException}
		 * with the {@code ContentModified} error code.
		 *
		 * @see https://microsoft.github.io/language-server-protocol/specifications/specification-3-16/#implementationConsiderations
		 */
		public void checkChanged() {
			if (hasChanged()) {
				throw new ResponseErrorException(new ResponseError(
					-32801, // ContentModified, see https://microsoft.github.io/language-server-protocol/specifications/specification-3-16/#responseMessage
					"Document changed, request invalid",
					null
				));
			}
		}

	}

}
