package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import com.salesforce.b2eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.b2eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.b2eclipse.importer.BazelProjectImportScanner;
import com.salesforce.b2eclipse.model.BazelPackageInfo;
import com.salesforce.b2eclipse.runtime.impl.EclipseWorkProgressMonitor;

public final class BazelProjectImporter extends AbstractProjectImporter {

    private static final String WORKSPACE_FILE_NAME = "WORKSPACE";

    @Override
    public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
        if (preferencesManager != null && !preferencesManager.getPreferences().isImportBazelEnabled()) {
            return false;
        }
        if (!rootFolder.exists() || !rootFolder.isDirectory()) {
            return false;
        }
		File workspaceFile = new File(rootFolder, WORKSPACE_FILE_NAME);
		if (!workspaceFile.exists()) {
            return false;
        }
        return true;

    }

    @Override
    public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        BazelProjectImportScanner scanner = new BazelProjectImportScanner();

        BazelPackageInfo workspaceRootPackage = scanner.getProjects(rootFolder);

        if (workspaceRootPackage == null) {
            throw new IllegalArgumentException();
        }
        List<BazelPackageInfo> bazelPackagesToImport = workspaceRootPackage.getChildPackageInfos().stream()
                .collect(Collectors.toList());

        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(null);

        BazelEclipseProjectFactory.setImportBazelSRCPath(
                JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getImportBazelSrcPath());
        BazelEclipseProjectFactory.setImportBazelTestPath(
                JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getImportBazelTestPath());

        BazelEclipseProjectFactory.importWorkspace(workspaceRootPackage, bazelPackagesToImport, progressMonitor,
                monitor);
    }

    @Override
    public void reset() {

    }

}
