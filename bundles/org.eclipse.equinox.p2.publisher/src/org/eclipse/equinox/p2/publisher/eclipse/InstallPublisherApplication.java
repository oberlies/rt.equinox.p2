/*******************************************************************************
 * Copyright (c) 2008 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.publisher.eclipse;

import java.net.URISyntaxException;
import java.util.ArrayList;
import org.eclipse.equinox.internal.p2.publisher.VersionedName;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.p2.publisher.*;

public class InstallPublisherApplication extends AbstractPublisherApplication {

	protected String id;
	protected Version version = new Version("1.0.0"); //$NON-NLS-1$
	protected String name;
	protected String executableName;
	protected String flavor;
	protected VersionedName[] topLevel;
	protected boolean start;
	protected String[] rootExclusions;

	public InstallPublisherApplication() {
	}

	protected void processFlag(String arg, PublisherInfo info) {
		super.processFlag(arg, info);

		if (arg.equalsIgnoreCase("-startAll")) //$NON-NLS-1$
			start = true;
	}

	protected void processParameter(String arg, String parameter, PublisherInfo info) throws URISyntaxException {
		super.processParameter(arg, parameter, info);

		if (arg.equalsIgnoreCase("-id")) //$NON-NLS-1$
			id = parameter;

		if (arg.equalsIgnoreCase("-version")) //$NON-NLS-1$
			version = new Version(parameter);

		if (arg.equalsIgnoreCase("-name")) //$NON-NLS-1$
			name = parameter;

		if (arg.equalsIgnoreCase("-executable")) //$NON-NLS-1$
			executableName = parameter;

		if (arg.equalsIgnoreCase("-flavor")) //$NON-NLS-1$
			flavor = parameter;

		if (arg.equalsIgnoreCase("-top")) //$NON-NLS-1$
			topLevel = createVersionedNameList(parameter);

		if (arg.equalsIgnoreCase("-rootExclusions")) //$NON-NLS-1$
			rootExclusions = AbstractPublisherAction.getArrayFromString(parameter, ",");
	}

	private VersionedName[] createVersionedNameList(String parameter) {
		String[] list = AbstractPublisherAction.getArrayFromString(parameter, ","); //$NON-NLS-1$
		VersionedName[] result = new VersionedName[list.length];
		for (int i = 0; i < result.length; i++)
			result[i] = VersionedName.parse(list[i]);
		return result;
	}

	protected IPublisherAction[] createActions() {
		ArrayList result = new ArrayList();
		result.add(createEclipseInstallAction());
		return (IPublisherAction[]) result.toArray(new IPublisherAction[result.size()]);
	}

	private IPublisherAction createEclipseInstallAction() {
		String[] exclusions = getBaseExclusions();
		if (rootExclusions != null) {
			String[] result = new String[exclusions.length + rootExclusions.length];
			System.arraycopy(exclusions, 0, result, 0, exclusions.length);
			System.arraycopy(rootExclusions, 0, result, exclusions.length, rootExclusions.length);
			exclusions = result;
		}
		return new EclipseInstallAction(source, id, version, name, executableName, flavor, topLevel, exclusions, start);
	}

	protected String[] getBaseExclusions() {
		return new String[] {"plugins", "features", "configuration", "p2", "artifacts.xml"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}
}