/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Code 9 - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.extensionlocation;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.core.helpers.URLUtil;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.internal.p2.update.Site;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.directorywatcher.*;
import org.eclipse.equinox.p2.publisher.eclipse.*;
import org.eclipse.osgi.service.resolver.BundleDescription;

/**
 * @since 1.0
 */
public class SiteListener extends DirectoryChangeListener {

	public static final String SITE_POLICY = "org.eclipse.update.site.policy"; //$NON-NLS-1$
	public static final String SITE_LIST = "org.eclipse.update.site.list"; //$NON-NLS-1$
	private static final String FEATURES = "features"; //$NON-NLS-1$
	private static final String PLUGINS = "plugins"; //$NON-NLS-1$
	private static final String FEATURE_MANIFEST = "feature.xml"; //$NON-NLS-1$
	public static final Object UNINITIALIZED = "uninitialized"; //$NON-NLS-1$
	public static final Object INITIALIZING = "initializing"; //$NON-NLS-1$
	public static final Object INITIALIZED = "initialized"; //$NON-NLS-1$

	private String policy;
	private String[] list;
	private String url;
	private DirectoryChangeListener delegate;
	private String[] managedFiles;
	private String[] toBeRemoved;

	/*
	 * Return true if the given list contains the full path of the given file 
	 * handle. Return false otherwise.
	 */
	private static boolean contains(String[] plugins, File file) {
		String filename = file.getAbsolutePath();
		for (int i = 0; i < plugins.length; i++)
			if (filename.endsWith(new File(plugins[i]).toString()))
				return true;
		return false;
	}

	/**
	 * Given one repo and a base location, ensure cause the other repo to be loaded and then 
	 * poll the base location once updating the repositories accordingly.  This method is used to 
	 * ensure that both the metadata and artifact repos corresponding to one location are 
	 * synchronized in one go.  It is expected that both repos have been previously created
	 * so simply loading them here will work and that all their properties etc have been configured
	 * previously.
	 * @param metadataRepository
	 * @param artifactRepository
	 * @param base
	 */
	public static synchronized void synchronizeRepositories(ExtensionLocationMetadataRepository metadataRepository, ExtensionLocationArtifactRepository artifactRepository, File base) {
		try {
			if (metadataRepository == null) {
				ExtensionLocationMetadataRepositoryFactory factory = new ExtensionLocationMetadataRepositoryFactory();
				metadataRepository = (ExtensionLocationMetadataRepository) factory.load(artifactRepository.getLocation(), null);
			} else if (artifactRepository == null) {
				ExtensionLocationArtifactRepositoryFactory factory = new ExtensionLocationArtifactRepositoryFactory();
				artifactRepository = (ExtensionLocationArtifactRepository) factory.load(metadataRepository.getLocation(), null);
			}
		} catch (ProvisionException e) {
			// TODO need proper error handling here.  What should we do if there is a failure
			// when loading "the other" repo?
			e.printStackTrace();
			return;
		}

		artifactRepository.state(INITIALIZING);
		metadataRepository.state(INITIALIZING);
		File plugins = new File(base, PLUGINS);
		File features = new File(base, FEATURES);
		DirectoryWatcher watcher = new DirectoryWatcher(new File[] {plugins, features});
		//  here we have to sync with the inner repos as the extension location repos are 
		// read-only wrappers.
		DirectoryChangeListener listener = new RepositoryListener(metadataRepository.metadataRepository, artifactRepository.artifactRepository);
		if (metadataRepository.getProperties().get(SiteListener.SITE_POLICY) != null)
			listener = new SiteListener(metadataRepository.getProperties(), metadataRepository.getLocation().toExternalForm(), new BundlePoolFilteredListener(listener));
		watcher.addListener(listener);
		watcher.poll();
		artifactRepository.state(INITIALIZED);
		metadataRepository.state(INITIALIZED);
	}

	/*
	 * Create a new site listener on the given site.
	 */
	public SiteListener(Map properties, String url, DirectoryChangeListener delegate) {
		this.url = url;
		this.delegate = delegate;
		this.policy = (String) properties.get(SITE_POLICY);
		Collection listCollection = new HashSet();
		String listString = (String) properties.get(SITE_LIST);
		if (listString != null)
			for (StringTokenizer tokenizer = new StringTokenizer(listString, ","); tokenizer.hasMoreTokens();) //$NON-NLS-1$
				listCollection.add(tokenizer.nextToken());
		this.list = (String[]) listCollection.toArray(new String[listCollection.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.DirectoryChangeListener#isInterested(java.io.File)
	 */
	public boolean isInterested(File file) {
		// make sure that our delegate and super-class are both interested in 
		// the file before we consider it
		if (!delegate.isInterested(file))
			return false;
		if (Site.POLICY_MANAGED_ONLY.equals(policy)) {
			// we only want plug-ins referenced by features
			return contains(getManagedFiles(), file);
		} else if (Site.POLICY_USER_EXCLUDE.equals(policy)) {
			// ensure the file doesn't refer to a plug-in in our list
			if (contains(list, file))
				return false;
		} else if (Site.POLICY_USER_INCLUDE.equals(policy)) {
			if (isFeature(file))
				return true;
			// we are only interested in plug-ins in the list
			if (!contains(list, file))
				return false;
		} else {
			// shouldn't happen... unknown policy type
			return false;
		}
		// at this point we have either a user-include or user-exclude policy set
		// and we think we are interested in the file. we should first check to
		// see if it is in the list of things to be removed
		return !isToBeRemoved(file);
	}

	private boolean isFeature(File file) {
		String parent = file.getParent();
		return parent != null && parent.endsWith(FEATURES);
	}

	/*
	 * Return a boolean value indicating whether or not the feature pointed to
	 * by the given file is in the update manager's list of features to be
	 * uninstalled in its clean-up phase.
	 */
	private boolean isToBeRemoved(File file) {
		String[] removed = getToBeRemoved();
		if (removed.length == 0)
			return false;
		Feature feature = getFeature(file);
		if (feature == null)
			return false;
		for (int i = 0; i < removed.length; i++) {
			String line = removed[i];
			// the line is a versioned identifier which is id_version
			if (line.equals(feature.getId() + '_' + feature.getVersion()))
				return true;
		}
		return false;
	}

	/*
	 * Parse and return the feature.xml file in the given location. 
	 * Can return null.
	 */
	private Feature getFeature(File location) {
		if (location.isFile())
			return null;
		File manifest = new File(location, FEATURE_MANIFEST);
		if (!manifest.exists())
			return null;
		FeatureParser parser = new FeatureParser();
		return parser.parse(location);
	}

	/*
	 * Return an array describing the list of features are are going
	 * to be removed by the update manager in its clean-up phase.
	 * The strings are in the format of versioned identifiers: id_version
	 */
	private String[] getToBeRemoved() {
		if (toBeRemoved != null)
			return toBeRemoved;
		File configurationLocation = Activator.getConfigurationLocation();
		if (configurationLocation == null) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Unable to compute the configuration location.")); //$NON-NLS-1$
			toBeRemoved = new String[0];
			return toBeRemoved;
		}
		File toBeUninstalledFile = new File(configurationLocation, "org.eclipse.update/toBeUninstalled"); //$NON-NLS-1$
		if (!toBeUninstalledFile.exists()) {
			toBeRemoved = new String[0];
			return toBeRemoved;
		}
		// set it to be empty here in case we don't have a match in the file
		toBeRemoved = new String[0];
		Properties properties = new Properties();
		InputStream input = null;
		try {
			input = new BufferedInputStream(new FileInputStream(toBeUninstalledFile));
			properties.load(input);
		} catch (IOException e) {
			// TODO
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				// ignore
			}
		}
		String urlString = url;
		if (urlString.endsWith(Constants.EXTENSION_LOCATION))
			urlString = urlString.substring(0, urlString.length() - Constants.EXTENSION_LOCATION.length());
		List result = new ArrayList();
		for (Enumeration e = properties.elements(); e.hasMoreElements();) {
			String line = (String) e.nextElement();
			StringTokenizer tokenizer = new StringTokenizer(line, ";"); //$NON-NLS-1$
			String targetSite = tokenizer.nextToken();
			if (!urlString.equals(targetSite))
				continue;
			result.add(tokenizer.nextToken());
		}
		toBeRemoved = (String[]) result.toArray(new String[result.size()]);
		return toBeRemoved;
	}

	/*
	 * Return an array of files which are managed. This includes all of the features
	 * for this site, as well as the locations for all the plug-ins referenced by those
	 * features.
	 */
	private String[] getManagedFiles() {
		if (managedFiles != null)
			return managedFiles;
		List result = new ArrayList();
		File siteLocation;
		try {
			siteLocation = URLUtil.toFile(new URL(url));
		} catch (MalformedURLException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Unable to create a URL from site location: " + url, e)); //$NON-NLS-1$
			return new String[0];
		}
		Map pluginCache = getPlugins(siteLocation);
		Map featureCache = getFeatures(siteLocation);
		for (Iterator iter = featureCache.keySet().iterator(); iter.hasNext();) {
			File featureFile = (File) iter.next();
			// add the feature path
			result.add(featureFile.toString());
			Feature feature = (Feature) featureCache.get(featureFile);
			FeatureEntry[] entries = feature.getEntries();
			for (int inner = 0; inner < entries.length; inner++) {
				FeatureEntry entry = entries[inner];
				// grab the right location from the plug-in cache
				String key = entry.getId() + '/' + entry.getVersion();
				File pluginLocation = (File) pluginCache.get(key);
				if (pluginLocation != null)
					result.add(pluginLocation.toString());
			}
		}
		managedFiles = (String[]) result.toArray(new String[result.size()]);
		return managedFiles;
	}

	/*
	 * Iterate over the feature directory and return a map of 
	 * File to Feature objects (from the generator bundle)
	 */
	private Map getFeatures(File siteLocation) {
		Map result = new HashMap();
		File featureDir = new File(siteLocation, FEATURES);
		File[] children = featureDir.listFiles();
		for (int i = 0; i < children.length; i++) {
			File featureLocation = children[i];
			if (featureLocation.isDirectory() && featureLocation.getParentFile() != null && featureLocation.getParentFile().getName().equals("features") && new File(featureLocation, "feature.xml").exists()) {//$NON-NLS-1$ //$NON-NLS-2$
				FeatureParser parser = new FeatureParser();
				Feature entry = parser.parse(featureLocation);
				if (entry != null)
					result.put(featureLocation, entry);
			}
		}
		return result;
	}

	/*
	 * Iterate over the plugins directory and return a map of
	 * plug-in id/version to File locations.
	 */
	private Map getPlugins(File siteLocation) {
		File[] plugins = new File(siteLocation, PLUGINS).listFiles();
		Map result = new HashMap();
		for (int i = 0; plugins != null && i < plugins.length; i++) {
			File bundleLocation = plugins[i];
			if (bundleLocation.isDirectory() || bundleLocation.getName().endsWith(".jar")) { //$NON-NLS-1$
				BundleDescription description = BundlesAction.createBundleDescription(bundleLocation);
				if (description != null) {
					String id = description.getSymbolicName();
					String version = description.getVersion().toString();
					result.put(id + '/' + version, bundleLocation);
				}
			}
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#added(java.io.File)
	 */
	public boolean added(File file) {
		return delegate.added(file);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#changed(java.io.File)
	 */
	public boolean changed(File file) {
		return delegate.changed(file);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#getSeenFile(java.io.File)
	 */
	public Long getSeenFile(File file) {
		return delegate.getSeenFile(file);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#removed(java.io.File)
	 */
	public boolean removed(File file) {
		return delegate.removed(file);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#startPoll()
	 */
	public void startPoll() {
		delegate.startPoll();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.directorywatcher.RepositoryListener#stopPoll()
	 */
	public void stopPoll() {
		delegate.stopPoll();
	}
}
