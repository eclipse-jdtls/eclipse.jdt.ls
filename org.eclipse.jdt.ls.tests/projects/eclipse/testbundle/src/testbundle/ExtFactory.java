package testbundle;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;

public class ExtFactory implements IExecutableExtensionFactory {

	@Override
	public Object create() throws CoreException {
		return Ext.getInstance();
	}
}