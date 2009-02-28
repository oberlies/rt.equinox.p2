/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.publisher.actions;

import java.util.Properties;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactDescriptor;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.publisher.IPublisherAdvice;

public interface IPropertyAdvice extends IPublisherAdvice {

	/**
	 * Returns the set of extra properties to be associated with the IU
	 */
	public Properties getInstallableUnitProperties(InstallableUnitDescription iu);

	/**
	 * Returns the set of extra properties to be associated with the artifact descriptor
	 * being published
	 */
	public Properties getArtifactProperties(IInstallableUnit iu, IArtifactDescriptor descriptor);
}