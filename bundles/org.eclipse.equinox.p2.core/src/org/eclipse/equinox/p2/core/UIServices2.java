/*******************************************************************************
 * Copyright (c) 2011 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.core;

import java.security.cert.Certificate;

/**
 * Service used for prompting for user information from within lower level code.
 * Implementations of this service shall be registered under the type {@link UIServices}.
 * 
 * This class extends the {@link UIServices} class by adding methods that allow to suppress 
 * UI prompts. This option is needed to avoid that background jobs loading p2 repositories trigger 
 * UI prompts. The original {@link UIServices} methods require that a UI prompt to be shown 
 * whenever they terminate normally. 
 * 
 * @since 2.2
 */
public abstract class UIServices2 extends UIServices {

	/**
	 * Returns <code>true</code> if instance will open an UI prompt when calling the methods which 
	 * allow optional prompting.
	 * 
	 * @see #getUsernamePasswordIfPromptEnabled(String)
	 * @see #getUsernamePasswordIfPromptEnabled(String, AuthenticationInfo)
	 * @see #getTrustInfoIfPromptEnabled(Certificate[][], String[])
	 */
	public abstract boolean promptEnabled();

	/**
	 * Opens a UI prompt for authentication details if prompting is enabled.
	 * 
	 * @param location - the location requiring login details, may be <code>null</code>.
	 * @return The authentication result, or <code>null</code> if the prompt was cancelled by the user or prompting is disabled.
	 * @see #promptEnabled()
	 */
	public final AuthenticationInfo getUsernamePasswordIfPromptEnabled(String location) {
		if (!promptEnabled()) {
			return null;
		}
		return getUsernamePassword(location);
	}

	/**
	 * Opens a UI prompt for authentication details when cached or remembered details
	 * where not accepted, and prompting is enabled.
	 * 
	 * @param location  the location requiring login details
	 * @param previousInfo - the previously used authentication details - may not be null.
	 * @return The authentication result, or <code>null</code> if the prompt was cancelled by the user or prompting is disabled.
	 * @see #promptEnabled()
	 */
	public final AuthenticationInfo getUsernamePasswordIfPromptEnabled(String location, AuthenticationInfo previousInfo) {
		if (!promptEnabled()) {
			return null;
		}
		return getUsernamePasswordIfPromptEnabled(location, previousInfo);
	}

	/**
	 * Opens a UI prompt to capture information about trusted content if prompting is enabled.
	 *  
	 * @param untrustedChain - an array of certificate chains for which there is no current trust anchor.  May be
	 * <code>null</code>, which means there are no untrusted certificate chains.
	 * @param unsignedDetail - an array of strings, where each String describes content that is not signed.
	 * May be <code>null</code>, which means there is no unsigned content
	 * @return  the TrustInfo that describes the user's choices for trusting certificates and
	 * unsigned content, or <code>null</code> if prompting is disabled.
	 * @see #promptEnabled()
	 */
	public final TrustInfo getTrustInfoIfPromptEnabled(Certificate[][] untrustedChain, String[] unsignedDetail) {
		if (!promptEnabled())
			return null;
		return getTrustInfoIfPromptEnabled(untrustedChain, unsignedDetail);
	}
}
