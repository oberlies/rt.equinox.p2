/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.updatesite;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.p2.publisher.eclipse.*;
import org.eclipse.osgi.util.NLS;
import org.xml.sax.SAXException;

/**
 * @since 1.0
 */
public class UpdateSite {

	private static final String VERSION_SEPARATOR = "_"; //$NON-NLS-1$
	private static final String JAR_EXTENSION = ".jar"; //$NON-NLS-1$
	private static final String FEATURE_DIR = "features/"; //$NON-NLS-1$
	private static final String PLUGIN_DIR = "plugins/"; //$NON-NLS-1$
	private static final String FEATURE_TEMP_FILE = "feature"; //$NON-NLS-1$
	private static final String SITE_FILE = "site.xml"; //$NON-NLS-1$
	private static final String PROTOCOL_FILE = "file"; //$NON-NLS-1$
	private static final int RETRY_COUNT = 2;
	private static final String DOT_XML = ".xml"; //$NON-NLS-1$
	private static final String SITE = "site"; //$NON-NLS-1$
	private String checksum;
	private URI location;
	private SiteModel site;

	/*
	 * Some variables for caching.
	 */
	// map of String (URI.toString()) to UpdateSite
	private static Map siteCache = new HashMap();
	// map of String (featureID_featureVersion) to Feature
	private Map featureCache = new HashMap();

	/*
	 * Return a new URI for the given file which is based from the specified root.
	 */
	public static URI getFileURI(URI root, String fileName) {
		String segment = URIUtil.lastSegment(root);
		if (segment != null && segment.endsWith(fileName))
			return root;
		if (constainsUpdateSiteFileName(segment))
			return root.resolve(fileName);
		return URIUtil.append(root, fileName);
	}

	/*
	 * Return a URI based on the given URI, which points to a site.xml file.
	 */
	private static URI getSiteURI(URI baseLocation) {
		String segment = URIUtil.lastSegment(baseLocation);
		if (constainsUpdateSiteFileName(segment))
			return baseLocation;
		return URIUtil.append(baseLocation, SITE_FILE);
	}

	/**
	 * Be lenient about accepting any location with *site*.xml at the end.
	 */
	private static boolean constainsUpdateSiteFileName(String segment) {
		return segment != null && segment.endsWith(DOT_XML) && segment.indexOf(SITE) != -1;
	}

	/*
	 * Load and return an update site object from the given location.
	 */
	public static synchronized UpdateSite load(URI location, IProgressMonitor monitor) throws ProvisionException {
		if (location == null)
			return null;
		UpdateSite result = (UpdateSite) siteCache.get(location.toString());
		if (result != null)
			return result;
		InputStream input = null;
		File siteFile = loadSiteFile(location, monitor);
		try {
			DefaultSiteParser siteParser = new DefaultSiteParser(location);
			Checksum checksum = new CRC32();
			input = new CheckedInputStream(new BufferedInputStream(new FileInputStream(siteFile)), checksum);
			SiteModel siteModel = siteParser.parse(input);
			String checksumString = Long.toString(checksum.getValue());
			result = new UpdateSite(siteModel, getSiteURI(location), checksumString);
			siteCache.put(location.toString(), result);
			return result;
		} catch (SAXException e) {
			String msg = NLS.bind(Messages.ErrorReadingSite, location);
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_FAILED_READ, msg, e));
		} catch (IOException e) {
			String msg = NLS.bind(Messages.ErrorReadingSite, location);
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_FAILED_READ, msg, e));
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				// ignore
			}
			if (!PROTOCOL_FILE.equals(location.getScheme()))
				siteFile.delete();
		}
	}

	/**
	 * Returns a local file containing the contents of the update site at the given location.
	 */
	private static File loadSiteFile(URI location, IProgressMonitor monitor) throws ProvisionException {
		Throwable failure;
		File siteFile = null;
		IStatus transferResult;
		boolean deleteSiteFile = false;
		try {
			URI actualLocation = getSiteURI(location);
			if (PROTOCOL_FILE.equals(actualLocation.getScheme())) {
				siteFile = URIUtil.toFile(actualLocation);
				if (siteFile.exists())
					transferResult = Status.OK_STATUS;
				else {
					String msg = NLS.bind(Messages.ErrorReadingSite, location);
					transferResult = new Status(IStatus.ERROR, Activator.ID, msg, new FileNotFoundException(siteFile.getAbsolutePath()));
				}
			} else {
				// creating a temp file. In the event of an error we want to delete it.
				deleteSiteFile = true;
				siteFile = File.createTempFile("site", ".xml"); //$NON-NLS-1$//$NON-NLS-2$
				OutputStream destination = new BufferedOutputStream(new FileOutputStream(siteFile));
				transferResult = getTransport().download(actualLocation.toString(), destination, monitor);
			}
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			if (transferResult.isOK()) {
				// successful. If the siteFile is the download of a remote site.xml it will get cleaned up later
				deleteSiteFile = false;
				return siteFile;
			}
			// The transfer failed. Check if the file is not present
			if (0 == getTransport().getLastModified(actualLocation))
				throw new FileNotFoundException(actualLocation.toString());

			failure = transferResult.getException();
		} catch (IOException e) {
			failure = e;
		} finally {
			if (deleteSiteFile && siteFile != null)
				siteFile.delete();
		}
		int code = (failure instanceof FileNotFoundException) ? ProvisionException.REPOSITORY_NOT_FOUND : ProvisionException.REPOSITORY_FAILED_READ;
		String msg = NLS.bind(Messages.ErrorReadingSite, location);
		throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, code, msg, failure));
	}

	/*
	 * Parse the feature.xml specified by the given input stream and return the feature object.
	 * In case of failure, the failure is logged and null is returned
	 */
	private static Feature parseFeature(FeatureParser featureParser, URI featureURI, IProgressMonitor monitor) {
		File featureFile = null;
		if (PROTOCOL_FILE.equals(featureURI.getScheme())) {
			featureFile = URIUtil.toFile(featureURI);
			return featureParser.parse(featureFile);
		}
		try {
			featureFile = File.createTempFile(FEATURE_TEMP_FILE, JAR_EXTENSION);
			IStatus transferResult = null;
			//try the download twice in case of transient network problems
			for (int i = 0; i < RETRY_COUNT; i++) {
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				OutputStream destination = new BufferedOutputStream(new FileOutputStream(featureFile));
				transferResult = getTransport().download(featureURI.toString(), destination, monitor);
				if (transferResult.isOK())
					break;
			}
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			if (!transferResult.isOK()) {
				LogHelper.log(new ProvisionException(transferResult));
				return null;
			}
			return featureParser.parse(featureFile);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.ErrorReadingFeature, featureURI), e));
		} finally {
			if (featureFile != null)
				featureFile.delete();
		}
		return null;
	}

	/*
	 * Throw an exception if the site pointed to by the given URI is not valid.
	 */
	public static void validate(URI url, IProgressMonitor monitor) throws ProvisionException {
		URI siteURI = getSiteURI(url);
		long lastModified = getTransport().getLastModified(siteURI);
		if (lastModified == 0) {
			String msg = NLS.bind(Messages.ErrorReadingSite, url);
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_FAILED_READ, msg, null));
		}
	}

	/*
	 * Constructor for the class.
	 */
	private UpdateSite(SiteModel site, URI location, String checksum) {
		super();
		this.site = site;
		this.location = location;
		this.checksum = checksum;
	}

	/*
	 * Iterate over the archive entries in this site and return the matching URI string for
	 * the given identifier, if there is one.
	 */
	private URI getArchiveURI(URI base, String identifier) {
		URLEntry[] archives = site.getArchives();
		for (int i = 0; archives != null && i < archives.length; i++) {
			URLEntry entry = archives[i];
			if (identifier.equals(entry.getAnnotation()))
				return internalGetURI(base, entry.getURL());
		}
		return null;
	}

	/*
	 * Return the checksum for this site.
	 */
	public String getChecksum() {
		return checksum;
	}

	/*
	 * Return a URI which represents the location of the given feature.
	 */
	public URI getSiteFeatureURI(SiteFeature siteFeature) {
		URL url = siteFeature.getURL();
		try {
			if (url != null)
				return URIUtil.toURI(url);
		} catch (URISyntaxException e) {
			//fall through and resolve the URI ourselves
		}
		URI base = getBaseURI();
		String featureURIString = siteFeature.getURLString();
		return internalGetURI(base, featureURIString);
	}

	/*
	 * Return a URI which represents the location of the given feature.
	 */
	public URI getFeatureURI(String id, String version) {
		SiteFeature[] entries = site.getFeatures();
		for (int i = 0; i < entries.length; i++) {
			if (id.equals(entries[i].getFeatureIdentifier()) && version.equals(entries[i].getFeatureVersion())) {
				return getSiteFeatureURI(entries[i]);
			}
		}

		URI base = getBaseURI();
		URI url = getArchiveURI(base, FEATURE_DIR + id + VERSION_SEPARATOR + version + JAR_EXTENSION);
		if (url != null)
			return url;
		return getFileURI(base, FEATURE_DIR + id + VERSION_SEPARATOR + version + JAR_EXTENSION);
	}

	/*
	 * Return the location of this site.
	 */
	public URI getLocation() {
		return location;
	}

	public String getMirrorsURI() {
		//copy mirror information from update site to p2 repositories
		String mirrors = site.getMirrorsURI();
		if (mirrors == null)
			return null;
		//remove site.xml file reference
		int index = mirrors.indexOf("site.xml"); //$NON-NLS-1$
		if (index != -1)
			mirrors = mirrors.substring(0, index) + mirrors.substring(index + "site.xml".length()); //$NON-NLS-1$
		return mirrors;
	}

	/*
	 * Return a URI which represents the location of the given plug-in.
	 */
	public URI getPluginURI(FeatureEntry plugin) {
		URI base = getBaseURI();
		String path = PLUGIN_DIR + plugin.getId() + VERSION_SEPARATOR + plugin.getVersion() + JAR_EXTENSION;
		URI url = getArchiveURI(base, path);
		if (url != null)
			return url;
		return getFileURI(base, path);
	}

	private URI getBaseURI() {
		URI base = null;
		String siteURIString = site.getLocationURIString();
		if (siteURIString != null) {
			if (!siteURIString.endsWith("/")) //$NON-NLS-1$
				siteURIString += "/"; //$NON-NLS-1$
			base = internalGetURI(location, siteURIString);
		}
		if (base == null)
			base = location;
		return base;
	}

	/*
	 * Return the site model.
	 */
	public SiteModel getSite() {
		return site;
	}

	/*
	 * The trailing parameter can be either null, relative or absolute. If it is null,
	 * then return null. If it is absolute, then create a new url and return it. If it is
	 * relative, then make it relative to the given base url.
	 */
	private URI internalGetURI(URI base, String trailing) {
		if (trailing == null)
			return null;
		return base.resolve(trailing);
	}

	/*
	 * Load and return the features references in this update site.
	 */
	public synchronized Feature[] loadFeatures(IProgressMonitor monitor) throws ProvisionException {
		if (!featureCache.isEmpty())
			return (Feature[]) featureCache.values().toArray(new Feature[featureCache.size()]);
		Feature[] result = loadFeaturesFromDigest(monitor);
		return result == null ? loadFeaturesFromSite(monitor) : result;
	}

	/*
	 * Try and load the feature information from the update site's
	 * digest file, if it exists.
	 */
	private Feature[] loadFeaturesFromDigest(IProgressMonitor monitor) {
		File digestFile = null;
		boolean local = false;
		try {
			URI digestURI = getDigestURI();
			if (PROTOCOL_FILE.equals(digestURI.getScheme())) {
				digestFile = URIUtil.toFile(digestURI);
				if (!digestFile.exists())
					return null;
				local = true;
			} else {
				digestFile = File.createTempFile("digest", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
				BufferedOutputStream destination = new BufferedOutputStream(new FileOutputStream(digestFile));
				IStatus result = getTransport().download(digestURI.toString(), destination, monitor);
				if (result.getSeverity() == IStatus.CANCEL || monitor.isCanceled())
					throw new OperationCanceledException();
				if (!result.isOK())
					return null;
			}
			Feature[] features = new DigestParser().parse(digestFile);
			if (features == null)
				return null;
			Map tmpFeatureCache = new HashMap(features.length);
			for (int i = 0; i < features.length; i++) {
				String key = features[i].getId() + VERSION_SEPARATOR + features[i].getVersion();
				tmpFeatureCache.put(key, features[i]);
			}
			featureCache = tmpFeatureCache;
			return features;
		} catch (FileNotFoundException fnfe) {
			// we do not track FNF exceptions as we will fall back to the 
			// standard feature parsing from the site itself, see bug 225587.
		} catch (URISyntaxException e) {
			String msg = NLS.bind(Messages.InvalidRepositoryLocation, location);
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, ProvisionException.REPOSITORY_INVALID_LOCATION, msg, e));
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.ErrorReadingDigest, location), e));
		} finally {
			if (!local && digestFile != null)
				digestFile.delete();
		}
		return null;
	}

	private URI getDigestURI() throws URISyntaxException {
		URI digestBase = location;
		String digestURIString = site.getDigestURIString();
		if (digestURIString != null) {
			if (!digestURIString.endsWith("/")) //$NON-NLS-1$
				digestURIString += "/"; //$NON-NLS-1$
			digestBase = internalGetURI(location, digestURIString);
		}

		return getFileURI(digestBase, "digest.zip"); //$NON-NLS-1$
	}

	/*
	 * Load and return the features that are referenced by this update site. Note this
	 * requires downloading and parsing the feature manifest locally.
	 */
	private Feature[] loadFeaturesFromSite(IProgressMonitor monitor) throws ProvisionException {
		SiteFeature[] siteFeatures = site.getFeatures();
		FeatureParser featureParser = new FeatureParser();
		Map tmpFeatureCache = new HashMap(siteFeatures.length);

		for (int i = 0; i < siteFeatures.length; i++) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			SiteFeature siteFeature = siteFeatures[i];
			String key = null;
			if (siteFeature.getFeatureIdentifier() != null && siteFeature.getFeatureVersion() != null) {
				key = siteFeature.getFeatureIdentifier() + VERSION_SEPARATOR + siteFeature.getFeatureVersion();
				if (tmpFeatureCache.containsKey(key))
					continue;
			}
			URI featureURI = getSiteFeatureURI(siteFeature);
			Feature feature = parseFeature(featureParser, featureURI, new NullProgressMonitor());
			if (feature == null) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.ErrorReadingFeature, featureURI)));
			} else {
				if (key == null) {
					siteFeature.setFeatureIdentifier(feature.getId());
					siteFeature.setFeatureVersion(feature.getVersion());
					key = siteFeature.getFeatureIdentifier() + VERSION_SEPARATOR + siteFeature.getFeatureVersion();
				}
				tmpFeatureCache.put(key, feature);
				loadIncludedFeatures(feature, featureParser, tmpFeatureCache, monitor);
			}
		}
		featureCache = tmpFeatureCache;
		return (Feature[]) featureCache.values().toArray(new Feature[featureCache.size()]);
	}

	/*
	 * Load the features that are included by the given feature.
	 */
	private void loadIncludedFeatures(Feature feature, FeatureParser featureParser, Map features, IProgressMonitor monitor) throws ProvisionException {
		FeatureEntry[] featureEntries = feature.getEntries();
		for (int i = 0; i < featureEntries.length; i++) {
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			FeatureEntry entry = featureEntries[i];
			if (entry.isRequires() || entry.isPlugin())
				continue;
			String key = entry.getId() + VERSION_SEPARATOR + entry.getVersion();
			if (features.containsKey(key))
				continue;

			URI includedFeatureURI = getFeatureURI(entry.getId(), entry.getVersion());
			Feature includedFeature = parseFeature(featureParser, includedFeatureURI, monitor);
			if (includedFeature == null) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.ID, NLS.bind(Messages.ErrorReadingFeature, includedFeatureURI)));
			} else {
				features.put(key, includedFeature);
				loadIncludedFeatures(includedFeature, featureParser, features, monitor);
			}
		}
	}

	private static ECFTransport getTransport() {
		return ECFTransport.getInstance();
	}
}