package org.jboss.tools.vscode.java;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class LanguageServer implements IApplication {

	boolean exit = false;
	
	@Override
	public Object start(IApplicationContext context) throws Exception {
		
		JavaLanguageServerPlugin.getContext().getBundle().start();
		while(!exit){
			try {
				Thread.currentThread().sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		System.out.println("Stopping language server");
		this.exit=true;
		// TODO Auto-generated method stub
	}

}
