/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.internal.p2.artifact.optimizers.jbdiff;

import java.net.URL;
import java.util.Map;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.optimizers.Activator;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.p2.artifact.repository.IArtifactRepositoryManager;

/**
 * The optimizer <code>Application</code> for JBDiff based optimizations. 
 */
public class Application implements IApplication {

	private URL artifactRepositoryLocation;
	private int width = 1;
	private int depth = 1;
	private boolean nosar;

	public Object start(IApplicationContext context) throws Exception {
		Map args = context.getArguments();
		initializeFromArguments((String[]) args.get("application.args")); //$NON-NLS-1$
		IArtifactRepository repository = setupRepository(artifactRepositoryLocation);
		new Optimizer(repository, width, depth, nosar).run();
		return null;
	}

	private IArtifactRepository setupRepository(URL location) {
		IArtifactRepositoryManager manager = (IArtifactRepositoryManager) ServiceHelper.getService(Activator.getContext(), IArtifactRepositoryManager.class.getName());
		if (manager == null)
			// TODO log here
			return null;
		return manager.loadRepository(location, null);
	}

	public void stop() {
		// nothing to do yet
	}

	public void initializeFromArguments(String[] args) throws Exception {
		if (args == null)
			return;
		for (int i = 0; i < args.length; i++) {
			// check for args without parameters (i.e., a flag arg)
			//			if (args[i].equals("-pack"))
			//				pack = true;

			if (args[i].equals("-nosar")) //$NON-NLS-1$
				nosar = true;

			// check for args with parameters. If we are at the last argument or 
			// if the next one has a '-' as the first character, then we can't have 
			// an arg with a param so continue.
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			if (args[i - 1].equalsIgnoreCase("-artifactRepository") || args[i - 1].equalsIgnoreCase("-ar")) //$NON-NLS-1$ //$NON-NLS-2$
				artifactRepositoryLocation = new URL(arg);

			if (args[i - 1].equalsIgnoreCase("-depth")) //$NON-NLS-1$
				depth = Integer.parseInt(arg);

			if (args[i - 1].equalsIgnoreCase("-width")) //$NON-NLS-1$
				width = Integer.parseInt(arg);

		}
	}
}
