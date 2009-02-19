/*******************************************************************************
 * Copyright (c) 2008 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 *   IBM - ongoing development
 ******************************************************************************/
package org.eclipse.equinox.p2.publisher.eclipse;

import java.io.File;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.internal.p2.publisher.eclipse.BrandingIron;
import org.eclipse.equinox.internal.p2.publisher.eclipse.ExecutablesDescriptor;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactDescriptor;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.internal.provisional.p2.core.VersionRange;
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory.InstallableUnitFragmentDescription;
import org.eclipse.equinox.p2.publisher.*;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.osgi.service.environment.Constants;

/**
 * Given the description of an executable, this action publishes optionally 
 * non-destructively brands the executable, publishes the resultant artifacts
 * and publishes the required IUs to identify the branded executable, configure
 * the executable and set it up as the launcher for a profile.
 * <p>
 * This action works on one platform configuration only.
 * <p>
 * This action consults the following types of advice:
 * </ul>
 * <li>{@link IBrandingAdvice}</li>
 * </ul>
 */
public class EquinoxExecutableAction extends AbstractPublisherAction {
	private static String TYPE = "executable"; //$NON-NLS-1$

	protected String configSpec;
	protected String idBase;
	protected Version version;
	protected ExecutablesDescriptor executables;
	protected String flavor;

	protected EquinoxExecutableAction() {
	}

	public EquinoxExecutableAction(ExecutablesDescriptor executables, String configSpec, String idBase, Version version, String flavor) {
		this.executables = executables;
		this.configSpec = configSpec;
		this.idBase = idBase == null ? "org.eclipse" : idBase; //$NON-NLS-1$
		this.version = version;
		this.flavor = flavor;
	}

	public IStatus perform(IPublisherInfo info, IPublisherResult result, IProgressMonitor monitor) {
		ExecutablesDescriptor brandedExecutables = brandExecutables(info, executables);
		try {
			publishExecutableIU(info, brandedExecutables, result);
			publishExecutableCU(info, brandedExecutables, result);
			publishExecutableSetter(brandedExecutables, result);
		} finally {
			if (brandedExecutables.isTemporary())
				FileUtils.deleteAll(brandedExecutables.getLocation());
		}
		return Status.OK_STATUS;
	}

	/**
	 * Publishes the IUs that cause the executable to be actually set as the launcher for 
	 * the profile
	 */
	private void publishExecutableSetter(ExecutablesDescriptor brandedExecutables, IPublisherResult result) {
		InstallableUnitDescription iud = new MetadataFactory.InstallableUnitDescription();
		String executableName = brandedExecutables.getExecutableName();
		String id = getExecutableId() + '.' + executableName;
		iud.setId(id);
		iud.setVersion(version);
		iud.setTouchpointType(PublisherHelper.TOUCHPOINT_OSGI);
		iud.setCapabilities(new IProvidedCapability[] {createSelfCapability(id, version)});

		String filter = createFilterSpec(configSpec);
		if (filter.length() > 0)
			iud.setFilter(filter);
		Map touchpointData = new HashMap();
		touchpointData.put("configure", "setLauncherName(name:" + executableName + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		touchpointData.put("unconfigure", "setLauncherName()"); //$NON-NLS-1$ //$NON-NLS-2$
		iud.addTouchpointData(MetadataFactory.createTouchpointData(touchpointData));
		result.addIU(MetadataFactory.createInstallableUnit(iud), IPublisherResult.ROOT);
	}

	/**
	 * Publishes IUs and CUs for the files that make up the launcher for a given
	 * ws/os/arch combination.
	 */
	protected void publishExecutableIU(IPublisherInfo info, ExecutablesDescriptor execDescriptor, IPublisherResult result) {
		// Create the IU for the executable
		InstallableUnitDescription iu = new MetadataFactory.InstallableUnitDescription();
		String id = getExecutableId();
		iu.setId(id);
		iu.setVersion(version);
		String filter = createFilterSpec(configSpec);
		iu.setFilter(filter);
		iu.setSingleton(true);
		iu.setTouchpointType(PublisherHelper.TOUCHPOINT_NATIVE);
		String namespace = ConfigCUsAction.getAbstractCUCapabilityNamespace(idBase, TYPE, flavor, configSpec);
		String capabilityId = ConfigCUsAction.getAbstractCUCapabilityId(idBase, TYPE, flavor, configSpec);
		IProvidedCapability executableCapability = MetadataFactory.createProvidedCapability(namespace, capabilityId, version);
		IProvidedCapability selfCapability = createSelfCapability(id, version);
		iu.setCapabilities(new IProvidedCapability[] {selfCapability, executableCapability});

		//Create the artifact descriptor.  we have several files so no path on disk
		IArtifactKey key = PublisherHelper.createBinaryArtifactKey(id, version);
		iu.setArtifacts(new IArtifactKey[] {key});
		IArtifactDescriptor descriptor = PublisherHelper.createArtifactDescriptor(key, null);
		publishArtifact(descriptor, execDescriptor.getFiles(), null, info, createRootPrefixComputer(execDescriptor.getLocation()));
		if (execDescriptor.isTemporary())
			FileUtils.deleteAll(execDescriptor.getLocation());

		// setup a requirement between the executable and the launcher fragment that has the shared library
		String[] config = parseConfigSpec(configSpec);
		String ws = config[0];
		String os = config[1];
		String arch = config[2];
		String launcherFragment = EquinoxLauncherCUAction.ORG_ECLIPSE_EQUINOX_LAUNCHER + '.' + ws + '.' + os;
		if (!(Constants.OS_MACOSX.equals(os) && !Constants.ARCH_X86_64.equals(arch)))
			launcherFragment += '.' + arch;
		iu.setRequiredCapabilities(new IRequiredCapability[] {MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, launcherFragment, VersionRange.emptyRange, filter, false, false)});
		result.addIU(MetadataFactory.createInstallableUnit(iu), IPublisherResult.ROOT);
	}

	private String getExecutableId() {
		return createCUIdString(idBase, TYPE, "", configSpec);
	}

	// Create the CU that installs (e.g., unzips) the executable
	private void publishExecutableCU(IPublisherInfo info, ExecutablesDescriptor execDescriptor, IPublisherResult result) {
		InstallableUnitFragmentDescription cu = new InstallableUnitFragmentDescription();
		String id = createCUIdString(idBase, TYPE, flavor, configSpec);
		cu.setId(id);
		cu.setVersion(version);
		cu.setFilter(createFilterSpec(configSpec));
		String executableId = getExecutableId();
		cu.setHost(new IRequiredCapability[] {MetadataFactory.createRequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, executableId, new VersionRange(version, true, version, true), null, false, false)});
		cu.setProperty(IInstallableUnit.PROP_TYPE_FRAGMENT, Boolean.TRUE.toString());
		//TODO bug 218890, would like the fragment to provide the launcher capability as well, but can't right now.
		cu.setCapabilities(new IProvidedCapability[] {PublisherHelper.createSelfCapability(id, version)});
		cu.setTouchpointType(PublisherHelper.TOUCHPOINT_NATIVE);
		String[] config = parseConfigSpec(configSpec);
		String os = config[1];
		Map touchpointData = computeInstallActions(execDescriptor, os);
		cu.addTouchpointData(MetadataFactory.createTouchpointData(touchpointData));
		IInstallableUnit unit = MetadataFactory.createInstallableUnit(cu);
		result.addIU(unit, IPublisherResult.ROOT);
	}

	private Map computeInstallActions(ExecutablesDescriptor execDescriptor, String os) {
		Map touchpointData = new HashMap();
		String configurationData = "unzip(source:@artifact, target:${installFolder});"; //$NON-NLS-1$
		if (Constants.OS_MACOSX.equals(os)) {
			String execName = execDescriptor.getExecutableName();
			configurationData += " chmod(targetDir:${installFolder}/" + execName + ".app/Contents/MacOS/, targetFile:" + execName + ", permissions:755);"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} else if (!Constants.OS_WIN32.equals(os)) {
			// We are on linux/unix.  by default set all of the files to be executable.
			File[] fileList = execDescriptor.getFiles();
			for (int i = 0; i < fileList.length; i++)
				configurationData += " chmod(targetDir:${installFolder}, targetFile:" + fileList[i].getName() + ", permissions:755);"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		touchpointData.put("install", configurationData); //$NON-NLS-1$
		String unConfigurationData = "cleanupzip(source:@artifact, target:${installFolder});"; //$NON-NLS-1$
		touchpointData.put("uninstall", unConfigurationData); //$NON-NLS-1$
		return touchpointData;
	}

	/**
	 * Brands a copy of the given executable descriptor with the information in the 
	 * current product definition.  The files described in the descriptor are also copied
	 * to a temporary location to avoid destructive modification
	 * @param info the publisher info that sets the context for this operation
	 * @param descriptor the executable descriptor to brand.
	 * @return the new descriptor
	 */
	protected ExecutablesDescriptor brandExecutables(IPublisherInfo info, ExecutablesDescriptor descriptor) {
		ExecutablesDescriptor result = new ExecutablesDescriptor(descriptor);
		result.makeTemporaryCopy();
		IBrandingAdvice advice = getBrandingAdvice(info);
		if (advice == null || advice.getIcons() == null)
			partialBrandExecutables(result);
		else
			fullBrandExecutables(result, advice);
		return result;
	}

	private IBrandingAdvice getBrandingAdvice(IPublisherInfo info) {
		// there is expected to only be one branding advice for a given configspec so
		// just return the first one we find.
		Collection advice = info.getAdvice(configSpec, true, null, null, IBrandingAdvice.class);
		for (Iterator i = advice.iterator(); i.hasNext();)
			return (IBrandingAdvice) i.next();
		return null;
	}

	protected void fullBrandExecutables(ExecutablesDescriptor descriptor, IBrandingAdvice advice) {
		BrandingIron iron = new BrandingIron();
		iron.setIcons(advice.getIcons());
		iron.setName(advice.getExecutableName());
		iron.setOS(advice.getOS());
		iron.setRoot(descriptor.getLocation().getAbsolutePath());
		try {
			iron.brand();
			descriptor.setExecutableName(advice.getExecutableName(), true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void partialBrandExecutables(ExecutablesDescriptor descriptor) {
		File[] list = descriptor.getFiles();
		for (int i = 0; i < list.length; i++)
			mungeExecutableFileName(list[i], descriptor);
		descriptor.setExecutableName("eclipse", true); //$NON-NLS-1$
	}

	// TODO This method is a temporary hack to rename the launcher.exe files
	// to eclipse.exe (or "launcher" to "eclipse"). Eventually we will either hand-craft
	// metadata/artifacts for launchers, or alter the delta pack to contain eclipse-branded
	// launchers.
	private void mungeExecutableFileName(File file, ExecutablesDescriptor descriptor) {
		if (file.getName().equals("launcher")) { //$NON-NLS-1$
			File newFile = new File(file.getParentFile(), "eclipse"); //$NON-NLS-1$
			file.renameTo(newFile);
			descriptor.replace(file, newFile);
		} else if (file.getName().equals("launcher.exe")) { //$NON-NLS-1$
			File newFile = new File(file.getParentFile(), "eclipse.exe"); //$NON-NLS-1$
			file.renameTo(newFile);
			descriptor.replace(file, newFile);
		}
	}
}