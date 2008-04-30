/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata.generator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.artifact.repository.ArtifactRepositoryManager;
import org.eclipse.equinox.internal.p2.core.ProvisioningEventBus;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.p2.metadata.repository.MetadataRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus;
import org.eclipse.equinox.internal.provisional.p2.core.repository.IRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.generator.EclipseInstallGeneratorInfoProvider;
import org.eclipse.equinox.internal.provisional.p2.metadata.generator.Generator;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepositoryManager;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.ServiceRegistration;

public class EclipseGeneratorApplication implements IApplication {

	// The mapping rules for in-place generation need to construct paths into the structure
	// of an eclipse installation; in the future the default artifact mapping declared in
	// SimpleArtifactRepository may change, for example, to not have a 'bundles' directory
	// instead of a 'plugins' directory, so a separate constant is defined and used here.
	static final private String[][] INPLACE_MAPPING_RULES = { {"(& (classifier=osgi.bundle) (format=packed)", "${repoUrl}/features/${id}_${version}.jar.pack.gz"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=org.eclipse.update.feature))", "${repoUrl}/features/${id}_${version}.jar"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=osgi.bundle))", "${repoUrl}/plugins/${id}_${version}.jar"}, //$NON-NLS-1$//$NON-NLS-2$
			{"(& (classifier=binary))", "${repoUrl}/binary/${id}_${version}"}}; //$NON-NLS-1$//$NON-NLS-2$

	static final public String PUBLISH_PACK_FILES_AS_SIBLINGS = "publishPackFilesAsSiblings"; //$NON-NLS-1$

	private ArtifactRepositoryManager defaultArtifactManager;
	private ServiceRegistration registrationDefaultArtifactManager;
	private MetadataRepositoryManager defaultMetadataManager;
	private ServiceRegistration registrationDefaultMetadataManager;
	private IProvisioningEventBus bus;
	private ServiceRegistration registrationBus;
	private Generator.GeneratorResult incrementalResult = null;
	private boolean generateRootIU = true;
	private String metadataLocation;
	private String metadataRepoName;
	private String artifactLocation;
	private String artifactRepoName;
	private String operation;
	private String argument;
	private String features;
	private String bundles;
	private String base;
	//whether repository xml files should be compressed
	private String compress = "false"; //$NON-NLS-1$

	private File getExecutableName(String base, EclipseInstallGeneratorInfoProvider provider) {
		File location = provider.getExecutableLocation();
		if (location == null)
			return new File(base, EclipseInstallGeneratorInfoProvider.getDefaultExecutableName(null));
		if (location.isAbsolute())
			return location;
		return new File(base, location.getPath());
	}

	private void initialize(EclipseInstallGeneratorInfoProvider provider) throws ProvisionException {
		if ("-source".equalsIgnoreCase(operation)) //$NON-NLS-1$
			provider.initialize(new File(argument));
		else if ("-inplace".equalsIgnoreCase(operation)) { //$NON-NLS-1$
			provider.initialize(new File(argument));
			initializeForInplace(provider);
		} else if ("-config".equalsIgnoreCase(operation)) { //$NON-NLS-1$
			provider.initialize(new File(argument), new File(argument, "configuration"), getExecutableName(argument, provider), null, null); //$NON-NLS-1$
		} else if ("-updateSite".equalsIgnoreCase(operation)) { //$NON-NLS-1$
			provider.setAddDefaultIUs(false);
			provider.initialize(new File(argument), null, null, new File[] {new File(argument, "plugins")}, new File(argument, "features")); //$NON-NLS-1$ //$NON-NLS-2$
			initializeForInplace(provider);
		} else {
			if (base != null && bundles != null && features != null)
				provider.initialize(new File(base), null, null, new File[] {new File(bundles)}, new File(features));
		}
		initializeRepositories(provider);
	}

	private void initializeArtifactRepository(EclipseInstallGeneratorInfoProvider provider) throws ProvisionException {
		IArtifactRepositoryManager manager = (IArtifactRepositoryManager) ServiceHelper.getService(Activator.context, IArtifactRepositoryManager.class.getName());
		URL location;
		try {
			location = new URL(artifactLocation);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(NLS.bind(Messages.exception_artifactRepoLocationURL, artifactLocation));
		}

		String repositoryName = artifactRepoName != null ? artifactRepoName : artifactLocation + " - artifacts"; //$NON-NLS-1$
		Map properties = new HashMap(1);
		properties.put(IRepository.PROP_COMPRESSED, compress);
		if (provider.reuseExistingPack200Files())
			properties.put(PUBLISH_PACK_FILES_AS_SIBLINGS, Boolean.TRUE.toString());
		IArtifactRepository result = null;
		try {
			result = manager.createRepository(location, repositoryName, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);
			provider.setArtifactRepository(result);
			// TODO is this needed?
			if (artifactRepoName != null)
				result.setName(artifactRepoName);
			return;
		} catch (ProvisionException e) {
			//fall through a load existing repo
		}

		IArtifactRepository repository = manager.loadRepository(location, null);
		if (!repository.isModifiable())
			throw new IllegalArgumentException(NLS.bind(Messages.exception_artifactRepoNotWritable, location));
		provider.setArtifactRepository(repository);
		if (provider.reuseExistingPack200Files())
			repository.setProperty(PUBLISH_PACK_FILES_AS_SIBLINGS, "true"); //$NON-NLS-1$
		if (!provider.append())
			repository.removeAll();
		return;
	}

	public void initializeForInplace(EclipseInstallGeneratorInfoProvider provider) {
		File location = provider.getBaseLocation();
		if (location == null)
			location = provider.getBundleLocations()[0];
		try {
			metadataLocation = location.toURL().toExternalForm();
			artifactLocation = location.toURL().toExternalForm();
		} catch (MalformedURLException e) {
			// ought not happen...
		}
		provider.setPublishArtifactRepository(true);
		provider.setPublishArtifacts(false);
		provider.setMappingRules(INPLACE_MAPPING_RULES);
	}

	private void initializeMetadataRepository(EclipseInstallGeneratorInfoProvider provider) throws ProvisionException {
		URL location;
		try {
			location = new URL(metadataLocation);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(NLS.bind(Messages.exception_metadataRepoLocationURL, artifactLocation));
		}

		// 	First try to create a simple repo, this will fail if one already exists
		//  We try creating a repo first instead of just loading what is there because we don't want a repo based
		//  on a site.xml if there is one there.

		String repositoryName = metadataRepoName == null ? metadataLocation + " - metadata" : metadataRepoName; //$NON-NLS-1$
		Map properties = new HashMap(1);
		properties.put(IRepository.PROP_COMPRESSED, compress);

		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) ServiceHelper.getService(Activator.context, IMetadataRepositoryManager.class.getName());
		try {
			IMetadataRepository result = manager.createRepository(location, repositoryName, IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);
			manager.addRepository(result.getLocation());
			// TODO is this needed?
			if (metadataRepoName != null)
				result.setName(metadataRepoName);
			provider.setMetadataRepository(result);
			return;
		} catch (ProvisionException e) {
			//fall through and load the existing repo
		}

		IMetadataRepository repository = manager.loadRepository(location, null);
		if (repository != null) {
			// don't set the compress flag here because we don't want to change the format
			// of an already existing repository
			if (!repository.isModifiable())
				throw new IllegalArgumentException(NLS.bind(Messages.exception_metadataRepoNotWritable, location));
			provider.setMetadataRepository(repository);
			if (!provider.append())
				repository.removeAll();
			return;
		}
	}

	private void initializeRepositories(EclipseInstallGeneratorInfoProvider provider) throws ProvisionException {
		initializeArtifactRepository(provider);
		initializeMetadataRepository(provider);
	}

	public void setCompress(String value) {
		if (Boolean.valueOf(value).booleanValue())
			compress = "true"; //$NON-NLS-1$
	}

	public void processCommandLineArguments(String[] args, EclipseInstallGeneratorInfoProvider provider) throws Exception {
		if (args == null)
			return;
		for (int i = 0; i < args.length; i++) {
			// check for args without parameters (i.e., a flag arg)

			if (args[i].equalsIgnoreCase("-publishArtifacts") || args[i].equalsIgnoreCase("-pa")) //$NON-NLS-1$ //$NON-NLS-2$
				provider.setPublishArtifacts(true);

			if (args[i].equalsIgnoreCase("-publishArtifactRepository") || args[i].equalsIgnoreCase("-par")) //$NON-NLS-1$ //$NON-NLS-2$
				provider.setPublishArtifactRepository(true);

			if (args[i].equalsIgnoreCase("-append")) //$NON-NLS-1$
				provider.setAppend(true);

			if (args[i].equalsIgnoreCase("-noDefaultIUs")) //$NON-NLS-1$
				provider.setAddDefaultIUs(false);

			if (args[i].equalsIgnoreCase("-compress")) //$NON-NLS-1$
				compress = "true"; //$NON-NLS-1$

			if (args[i].equalsIgnoreCase("-reusePack200Files")) //$NON-NLS-1$
				provider.reuseExistingPack200Files(true);

			// check for args with parameters. If we are at the last argument or if the next one
			// has a '-' as the first character, then we can't have an arg with a parm so continue.
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			if (args[i - 1].equalsIgnoreCase("-source")) { //$NON-NLS-1$
				operation = args[i - 1];
				argument = arg;
			}

			if (args[i - 1].equalsIgnoreCase("-inplace")) { //$NON-NLS-1$
				operation = args[i - 1];
				argument = arg;
			}

			if (args[i - 1].equalsIgnoreCase("-config")) { //$NON-NLS-1$
				operation = args[i - 1];
				argument = arg;
			}
			if (args[i - 1].equalsIgnoreCase("-updateSite")) { //$NON-NLS-1$
				operation = args[i - 1];
				argument = arg;
			}

			if (args[i - 1].equalsIgnoreCase("-exe")) //$NON-NLS-1$
				provider.setExecutableLocation(arg);

			if (args[i - 1].equalsIgnoreCase("-launcherConfig")) //$NON-NLS-1$
				provider.setLauncherConfig(arg);

			if (args[i - 1].equalsIgnoreCase("-metadataRepository") || args[i - 1].equalsIgnoreCase("-mr")) //$NON-NLS-1$ //$NON-NLS-2$
				metadataLocation = arg;

			if (args[i - 1].equalsIgnoreCase("-metadataRepositoryName")) //$NON-NLS-1$
				metadataRepoName = arg;

			if (args[i - 1].equalsIgnoreCase("-artifactRepository") | args[i - 1].equalsIgnoreCase("-ar")) //$NON-NLS-1$ //$NON-NLS-2$
				artifactLocation = arg;

			if (args[i - 1].equalsIgnoreCase("-artifactRepositoryName")) //$NON-NLS-1$
				artifactRepoName = arg;

			if (args[i - 1].equalsIgnoreCase("-flavor")) //$NON-NLS-1$
				provider.setFlavor(arg);

			if (args[i - 1].equalsIgnoreCase("-productFile")) //$NON-NLS-1$
				provider.setProductFile(arg);

			if (args[i - 1].equalsIgnoreCase("-features")) //$NON-NLS-1$
				features = arg;

			if (args[i - 1].equalsIgnoreCase("-bundles")) //$NON-NLS-1$
				bundles = arg;

			if (args[i - 1].equalsIgnoreCase("-base")) //$NON-NLS-1$
				base = arg;

			if (args[i - 1].equalsIgnoreCase("-root")) //$NON-NLS-1$
				provider.setRootId(arg);

			if (args[i - 1].equalsIgnoreCase("-rootVersion")) //$NON-NLS-1$
				provider.setRootVersion(arg);

			if (args[i - 1].equalsIgnoreCase("-p2.os")) //$NON-NLS-1$
				provider.setOS(arg);

			if (args[i - 1].equalsIgnoreCase("-site")) //$NON-NLS-1$
				provider.setSiteLocation(new URL(arg));
		}
	}

	private void registerDefaultArtifactRepoManager() {
		if (ServiceHelper.getService(Activator.getContext(), IArtifactRepositoryManager.class.getName()) == null) {
			defaultArtifactManager = new ArtifactRepositoryManager();
			registrationDefaultArtifactManager = Activator.getContext().registerService(IArtifactRepositoryManager.class.getName(), defaultArtifactManager, null);
		}
	}

	private void registerDefaultMetadataRepoManager() {
		if (ServiceHelper.getService(Activator.getContext(), IMetadataRepositoryManager.class.getName()) == null) {
			defaultMetadataManager = new MetadataRepositoryManager();
			registrationDefaultMetadataManager = Activator.getContext().registerService(IMetadataRepositoryManager.class.getName(), defaultMetadataManager, null);
		}
	}

	private void registerEventBus() {
		if (ServiceHelper.getService(Activator.getContext(), IProvisioningEventBus.SERVICE_NAME) == null) {
			bus = new ProvisioningEventBus();
			registrationBus = Activator.getContext().registerService(IProvisioningEventBus.SERVICE_NAME, bus, null);
		}
	}

	public Object run(String args[]) throws Exception {
		EclipseInstallGeneratorInfoProvider provider = new EclipseInstallGeneratorInfoProvider();
		processCommandLineArguments(args, provider);
		Object result = run(provider);
		if (result != IApplication.EXIT_OK)
			for (int i = 0; i < args.length; i++)
				System.out.println(args[i]);
		return result;
	}

	public Object run(EclipseInstallGeneratorInfoProvider provider) throws Exception {
		registerEventBus();
		registerDefaultMetadataRepoManager();
		registerDefaultArtifactRepoManager();
		initialize(provider);

		if (provider.getBaseLocation() == null && provider.getProductFile() == null && !generateRootIU) {
			System.out.println(Messages.exception_baseLocationNotSpecified);
			return new Integer(-1);
		}
		System.out.println(NLS.bind(Messages.message_generatingMetadata, provider.getBaseLocation()));

		long before = System.currentTimeMillis();

		Generator generator = new Generator(provider);

		if (incrementalResult != null)
			generator.setIncrementalResult(incrementalResult);
		generator.setGenerateRootIU(generateRootIU);
		IStatus result = generator.generate();

		incrementalResult = null;
		long after = System.currentTimeMillis();
		if (result.isOK()) {
			System.out.println(NLS.bind(Messages.message_generationCompleted, String.valueOf((after - before) / 1000)));
			return IApplication.EXIT_OK;
		}
		System.out.println(result);
		return new Integer(1);
	}

	public Object start(IApplicationContext context) throws Exception {
		return run((String[]) context.getArguments().get("application.args")); //$NON-NLS-1$
	}

	public void stop() {
		if (registrationDefaultMetadataManager != null) {
			registrationDefaultMetadataManager.unregister();
			registrationDefaultMetadataManager = null;
		}
		if (registrationDefaultArtifactManager != null) {
			registrationDefaultArtifactManager.unregister();
			registrationDefaultArtifactManager = null;
		}
		if (registrationBus != null) {
			registrationBus.unregister();
			registrationBus = null;
		}
	}

	public void setBase(String base) {
		this.base = base;
	}

	public void setArtifactLocation(String location) {
		this.artifactLocation = location;
	}

	public void setBundles(String bundles) {
		this.bundles = bundles;
	}

	public void setOperation(String operation, String argument) {
		this.operation = operation;
		this.argument = argument;
	}

	public void setFeatures(String features) {
		this.features = features;
	}

	public void setMetadataLocation(String location) {
		this.metadataLocation = location;
	}

	public void setIncrementalResult(Generator.GeneratorResult ius) {
		this.incrementalResult = ius;
	}

	public void setGeneratorRootIU(boolean b) {
		this.generateRootIU = b;
	}
}
