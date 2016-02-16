/*******************************************************************************
 * Copyright (c) 2016 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.fusesource.ide.camel.validation.xml;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.validation.AbstractValidator;
import org.eclipse.wst.validation.ValidationEvent;
import org.eclipse.wst.validation.ValidationResult;
import org.eclipse.wst.validation.ValidationState;
import org.fusesource.ide.camel.model.service.core.io.CamelIOHandler;
import org.fusesource.ide.camel.model.service.core.model.CamelFile;
import org.fusesource.ide.camel.model.service.core.model.CamelModelElement;
import org.fusesource.ide.camel.validation.CamelValidationActivator;
import org.fusesource.ide.camel.validation.diagram.BasicNodeValidator;
import org.w3c.dom.Node;

public class XMLCamelRoutesValidator extends AbstractValidator {

	private static final String MARKER_TYPE = CamelValidationActivator.PLUGIN_ID + ".JBossFuseToolingValidationProblem";

	public XMLCamelRoutesValidator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.wst.validation.AbstractValidator#validate(org.eclipse.wst.
	 * validation.ValidationEvent, org.eclipse.wst.validation.ValidationState,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public ValidationResult validate(ValidationEvent event, ValidationState state, IProgressMonitor monitor) {
		IResource resource = event.getResource();
		try {
			resource.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		CamelFile camelFile = new CamelIOHandler().loadCamelModel(resource, monitor);

		ValidationResult validationResult = super.validate(event, state, monitor);
		if (validationResult == null) {
			validationResult = new ValidationResult();
		}
		checkId(camelFile, validationResult, resource);
		return validationResult;
	}

	/**
	 * @param camelFile
	 * @param validationResult
	 * @param resource
	 */
	private void checkId(CamelFile camelFile, ValidationResult validationResult, IResource resource) {
		for (CamelModelElement cme : camelFile.getChildElements()) {
			checkCamelModelElement(cme, validationResult, resource);
		}
	}

	/**
	 * @param cme
	 * @param validationResult
	 * @param resource
	 * @param locator
	 */
	private void checkCamelModelElement(CamelModelElement cme, ValidationResult validationResult, IResource resource) {
		org.fusesource.ide.camel.validation.ValidationResult result = new BasicNodeValidator().validate(cme);
		for (String error : result.getErrors()) {
			validationResult.incrementError(1);
			Node xmlNode = cme.getXmlNode();
			createMarker(resource, xmlNode, error);
		}
		// if (id == null || id.isEmpty()) {
		// validationResult.incrementError(1);
		// Node xmlNode = cme.getXmlNode();
		// createMarker(resource, xmlNode, "The id is mandatory.");
		// }
		for (CamelModelElement cmeChild : cme.getChildElements()) {
			checkCamelModelElement(cmeChild, validationResult, resource);
		}
	}

	/**
	 * @param resource
	 * @param xmlNode
	 * @param message
	 */
	private void createMarker(IResource resource, Node xmlNode, final String message) {
		try {
			IMarker marker = resource.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			marker.setAttribute(IMarker.LINE_NUMBER, xmlNode.getUserData(CamelIOHandler.LINE_NUMBER_ATT_NAME));
			// marker.setAttribute(IMarker.CHAR_START, 0);
			// marker.setAttribute(IMarker.CHAR_END, 10);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

}
