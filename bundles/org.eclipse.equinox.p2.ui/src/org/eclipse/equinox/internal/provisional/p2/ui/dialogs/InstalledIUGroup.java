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
package org.eclipse.equinox.internal.provisional.p2.ui.dialogs;

import org.eclipse.equinox.internal.p2.ui.ProvUIActivator;
import org.eclipse.equinox.internal.p2.ui.dialogs.StructuredIUGroup;
import org.eclipse.equinox.internal.p2.ui.viewers.DeferredQueryContentProvider;
import org.eclipse.equinox.internal.p2.ui.viewers.IUDetailsLabelProvider;
import org.eclipse.equinox.internal.provisional.p2.ui.ProvUI;
import org.eclipse.equinox.internal.provisional.p2.ui.model.ProfileElement;
import org.eclipse.equinox.internal.provisional.p2.ui.policy.Policy;
import org.eclipse.equinox.internal.provisional.p2.ui.viewers.*;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.*;

/**
 * An InstalledIUGroup is a reusable UI component that displays the
 * IU's in a given profile.
 * 
 * @since 3.4
 */
public class InstalledIUGroup extends StructuredIUGroup {

	private String profileId;

	/**
	 * Create a group that represents the installed IU's.
	 * 
	 * @param parent the parent composite for the group
	 * @param font The font to use for calculating pixel sizes.  This font is
	 * not managed by the receiver.
	 * @param profileId the id of the profile whose content is being shown.
	 * @param columnConfig the columns to be shown
	 */
	public InstalledIUGroup(Policy policy, final Composite parent, Font font, String profileId, IUColumnConfig[] columnConfig) {
		super(policy, parent, font, columnConfig);
		if (profileId == null)
			this.profileId = policy.getProfileChooser().getProfileId(ProvUI.getDefaultParentShell());
		else
			this.profileId = profileId;
		createGroupComposite(parent);
	}

	protected StructuredViewer createViewer(Composite parent) {
		// Table of installed IU's
		TreeViewer installedIUViewer = new TreeViewer(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);

		// Filters and sorters before establishing content, so we don't refresh unnecessarily.
		installedIUViewer.setComparator(new IUComparator(IUComparator.IU_NAME));
		installedIUViewer.setComparer(new ProvElementComparer());

		// Now the content.
		installedIUViewer.setContentProvider(new DeferredQueryContentProvider());

		// Now the visuals, columns before labels.
		setTreeColumns(installedIUViewer.getTree());
		installedIUViewer.setLabelProvider(new IUDetailsLabelProvider());

		// Input last.
		installedIUViewer.setInput(getInput());

		final StructuredViewerProvisioningListener listener = new StructuredViewerProvisioningListener(installedIUViewer, StructuredViewerProvisioningListener.PROV_EVENT_IU | StructuredViewerProvisioningListener.PROV_EVENT_PROFILE);
		ProvUIActivator.getDefault().addProvisioningListener(listener);
		installedIUViewer.getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				ProvUIActivator.getDefault().removeProvisioningListener(listener);
			}
		});
		return installedIUViewer;
	}

	private void setTreeColumns(Tree tree) {
		IUColumnConfig[] columns = getColumnConfig();
		tree.setHeaderVisible(true);

		for (int i = 0; i < columns.length; i++) {
			TreeColumn tc = new TreeColumn(tree, SWT.NONE, i);
			tc.setResizable(true);
			tc.setText(columns[i].columnTitle);
			tc.setWidth(convertHorizontalDLUsToPixels(columns[i].defaultColumnWidth));
		}
	}

	Object getInput() {
		ProfileElement element = new ProfileElement(null, profileId);
		return element;
	}

	/**
	 * Get the viewer used to represent the installed IU's
	 */
	public StructuredViewer getStructuredViewer() {
		return super.getStructuredViewer();
	}

	public Control getDefaultFocusControl() {
		return super.getDefaultFocusControl();
	}
}