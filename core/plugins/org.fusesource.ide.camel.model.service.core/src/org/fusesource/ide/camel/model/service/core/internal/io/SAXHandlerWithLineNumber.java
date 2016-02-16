/*******************************************************************************
 * Copyright (c) 2016 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/ 
package org.fusesource.ide.camel.model.service.core.internal.io;

import java.util.Stack;

import org.fusesource.ide.camel.model.service.core.io.CamelIOHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Aurelien Pupier
 *
 */
public class SAXHandlerWithLineNumber extends DefaultHandler {

	private final Document doc;
	private final Stack<Element> elementStack = new Stack<Element>();
	private final StringBuilder textBuffer = new StringBuilder();
	private Locator locator;

	/**
	 * @param doc
	 * @param textBuffer
	 * @param elementStack
	 */
	public SAXHandlerWithLineNumber(Document doc) {
		this.doc = doc;
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator; // Save the locator, so that it can be
								// used later for line tracking when
								// traversing nodes.
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		addTextIfNeeded();
		Element el = doc.createElement(qName);
		for (int i = 0; i < attributes.getLength(); i++) {
			el.setAttribute(attributes.getQName(i), attributes.getValue(i));
		}
		el.setUserData(CamelIOHandler.LINE_NUMBER_ATT_NAME, Integer.valueOf(locator.getLineNumber()), new UserDataHandler() {

			@Override
			public void handle(short arg0, String arg1, Object arg2, Node arg3, Node arg4) {
			}
		});
		elementStack.push(el);
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		addTextIfNeeded();
		Element closedEl = elementStack.pop();
		if (elementStack.isEmpty()) { // Is this the root element?
			doc.appendChild(closedEl);
		} else {
			Element parentEl = elementStack.peek();
			parentEl.appendChild(closedEl);
		}
	}

	@Override
	public void characters(char ch[], int start, int length) throws SAXException {
		textBuffer.append(ch, start, length);
	}

	// Outputs text accumulated under the current node
	private void addTextIfNeeded() {
		if (textBuffer.length() > 0) {
			Element el = elementStack.peek();
			Node textNode = doc.createTextNode(textBuffer.toString());
			el.appendChild(textNode);
			textBuffer.delete(0, textBuffer.length());
		}
	}

	/**
	 * @return the locator
	 */
	public Locator getLocator() {
		return locator;
	}
}