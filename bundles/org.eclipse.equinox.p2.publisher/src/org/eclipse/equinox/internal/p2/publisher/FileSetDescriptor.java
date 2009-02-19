/*******************************************************************************
 * Copyright (c) 2008 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.internal.p2.publisher;

import java.util.ArrayList;

public class FileSetDescriptor {
	private final String key;
	private String configSpec = null;
	private String files = ""; //$NON-NLS-1$
	private final ArrayList permissions = new ArrayList();
	private String links = ""; //$NON-NLS-1$

	public FileSetDescriptor(String key, String configSpec) {
		this.key = key;
		this.configSpec = configSpec;
	}

	public void setFiles(String property) {
		files = property;
	}

	// a permission spec is { <perm>, file patterns }
	public void addPermissions(String[] property) {
		permissions.add(property);
	}

	public void setLinks(String property) {
		links = property;
	}

	public String getConfigSpec() {
		return configSpec;
	}

	public String getKey() {
		return key;
	}

	public String getLinks() {
		return links;
	}

	public String[][] getPermissions() {
		return (String[][]) permissions.toArray(new String[permissions.size()][]);
	}

	public String getFiles() {
		return files;
	}

}