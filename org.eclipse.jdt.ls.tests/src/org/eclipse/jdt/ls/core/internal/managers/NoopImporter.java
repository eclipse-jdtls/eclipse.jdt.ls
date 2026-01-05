package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;

public class NoopImporter implements IProjectImporter {

	@Override
	public void initialize(File rootFolder) {
	}

	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		return false;
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {

	}

	@Override
	public void reset() {

	}

}
