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
package org.eclipse.equinox.p2.tests.simpleconfigurator;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.List;
import org.eclipse.equinox.internal.simpleconfigurator.utils.BundleInfo;
import org.eclipse.equinox.internal.simpleconfigurator.utils.SimpleConfiguratorUtils;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class SimpleConfiguratorUtilsTest extends AbstractProvisioningTest {

	public void testParseBundleInfo() throws MalformedURLException {

		File baseFile = getTempFolder();
		URI baseURI = baseFile.toURI();

		String canonicalForm = "javax.servlet,2.4.0.v200806031604,plugins/javax.servlet_2.4.0.v200806031604.jar,4,false";
		BundleInfo canonicalInfo = SimpleConfiguratorUtils.parseBundleInfoLine(canonicalForm, baseURI);
		File canonicalFile = new File(baseFile, "plugins/javax.servlet_2.4.0.v200806031604.jar");

		String line[] = new String[7];
		line[0] = "javax.servlet,2.4.0.v200806031604,file:plugins/javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[1] = "javax.servlet,2.4.0.v200806031604,plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[2] = "javax.servlet,2.4.0.v200806031604,file:plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[3] = "javax.servlet,2.4.0.v200806031604,file:plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[4] = "javax.servlet,2.4.0.v200806031604,file:" + canonicalFile.toString() + ",4,false";
		line[5] = "javax.servlet,2.4.0.v200806031604," + canonicalFile.toURL().toExternalForm() + ",4,false";
		line[6] = "javax.servlet,2.4.0.v200806031604," + canonicalFile.toURI().toString() + ",4,false";

		String relativeBundleLocation = "reference:file:plugins/javax.servlet_2.4.0.v200806031604.jar";
		String absoluteBundleLocation = "reference:" + canonicalFile.toURL().toExternalForm();

		for (int i = 0; i < line.length; i++) {
			BundleInfo info = SimpleConfiguratorUtils.parseBundleInfoLine(line[i], baseURI);
			assertEquals("[" + i + "]", canonicalInfo, info);
			if (info.getLocation().isAbsolute())
				assertEquals("[" + i + "]", absoluteBundleLocation, SimpleConfiguratorUtils.getBundleLocation(info, true));
			else
				assertEquals("[" + i + "]", relativeBundleLocation, SimpleConfiguratorUtils.getBundleLocation(info, true));
		}
	}

	public void testParseUNCBundleInfo() throws MalformedURLException {

		File baseFile = new File("\\\\127.0.0.1\\somefolder\\");
		URI baseURI = baseFile.toURI();

		String canonicalForm = "javax.servlet,2.4.0.v200806031604,plugins/javax.servlet_2.4.0.v200806031604.jar,4,false";
		BundleInfo canonicalInfo = SimpleConfiguratorUtils.parseBundleInfoLine(canonicalForm, baseURI);
		File canonicalFile = new File(baseFile, "plugins/javax.servlet_2.4.0.v200806031604.jar");

		String line[] = new String[4];
		line[0] = "javax.servlet,2.4.0.v200806031604,file:plugins/javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[1] = "javax.servlet,2.4.0.v200806031604,plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[2] = "javax.servlet,2.4.0.v200806031604,file:plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";
		line[3] = "javax.servlet,2.4.0.v200806031604,file:plugins\\javax.servlet_2.4.0.v200806031604.jar,4,false";

		//TODO: we need to fix URI.resolve for UNC paths
		//line[4] = "javax.servlet,2.4.0.v200806031604," + canonicalFile.toURI().toString() + ",4,false";

		String relativeBundleLocation = "reference:file:plugins/javax.servlet_2.4.0.v200806031604.jar";
		String absoluteBundleLocation = "reference:" + canonicalFile.toURL().toExternalForm();

		for (int i = 0; i < line.length; i++) {
			BundleInfo info = SimpleConfiguratorUtils.parseBundleInfoLine(line[i], baseURI);
			assertEquals("[" + i + "]", canonicalInfo, info);
			if (info.getLocation().isAbsolute())
				assertEquals("[" + i + "]", absoluteBundleLocation, SimpleConfiguratorUtils.getBundleLocation(info, true));
			else
				assertEquals("[" + i + "]", relativeBundleLocation, SimpleConfiguratorUtils.getBundleLocation(info, true));
		}
	}

	public void testRead34BundlesInfo() {

		File data = getTestData("1.0", "testData/simpleConfiguratorTest/3.4.bundles.info");
		try {
			URI baseLocation = new URI("file:/c:/tmp/foo");
			List infos = SimpleConfiguratorUtils.readConfiguration(data.toURL(), baseLocation);
			assertEquals("1.1", 2, infos.size());

			BundleInfo a = new BundleInfo("a", "1.0.0", new URI("plugins/a_1.0.0.jar"), 4, false);
			a.setBaseLocation(baseLocation);
			BundleInfo b = new BundleInfo("b", "1.0.0", new URI("plugins/b_1.0.0.jar"), -1, true);
			b.setBaseLocation(baseLocation);

			assertEquals("1.2", a, infos.get(0));
			assertEquals("1.3", b, infos.get(1));

		} catch (URISyntaxException e) {
			fail("1.97", e);
		} catch (MalformedURLException e) {
			fail("1.98", e);
		} catch (IOException e) {
			fail("1.99", e);
		}
	}

}
