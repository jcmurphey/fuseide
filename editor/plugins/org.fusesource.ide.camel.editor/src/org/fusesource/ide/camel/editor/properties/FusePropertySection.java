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
package org.fusesource.ide.camel.editor.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.core.databinding.observable.map.WritableMap;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.internal.forms.widgets.FormsResources;
import org.eclipse.ui.views.properties.tabbed.AbstractPropertySection;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.fusesource.ide.camel.editor.utils.CamelUtils;
import org.fusesource.ide.camel.editor.utils.DiagramUtils;
import org.fusesource.ide.camel.editor.utils.NodeUtils;
import org.fusesource.ide.camel.model.service.core.catalog.CamelModel;
import org.fusesource.ide.camel.model.service.core.catalog.CamelModelFactory;
import org.fusesource.ide.camel.model.service.core.catalog.Parameter;
import org.fusesource.ide.camel.model.service.core.catalog.components.Component;
import org.fusesource.ide.camel.model.service.core.catalog.dataformats.DataFormat;
import org.fusesource.ide.camel.model.service.core.catalog.eips.Eip;
import org.fusesource.ide.camel.model.service.core.catalog.languages.Language;
import org.fusesource.ide.camel.model.service.core.model.CamelModelElement;
import org.fusesource.ide.camel.model.service.core.util.CamelComponentUtils;
import org.fusesource.ide.camel.model.service.core.util.PropertiesUtils;
import org.fusesource.ide.foundation.core.util.Strings;
import org.fusesource.ide.foundation.ui.util.Selections;
import org.w3c.dom.Node;

/**
 * @author Aurelien Pupier
 */
public abstract class FusePropertySection extends AbstractPropertySection {

	public static final String DEFAULT_GROUP 	= "General";
	public static final String GROUP_PATH 		= "Path";
	public static final String GROUP_COMMON		= "Common";
	public static final String GROUP_CONSUMER 	= "Consumer";
	public static final String GROUP_PRODUCER 	= "Producer";
	
	protected FormToolkit toolkit;
	protected Form form;
	protected CTabFolder tabFolder;
	protected List<CTabItem> tabs = new ArrayList<CTabItem>();
	protected CamelModelElement selectedEP;
    protected DataBindingContext dbc;
    protected IObservableMap modelMap = new WritableMap();
    protected Composite parent;
    protected TabbedPropertySheetPage aTabbedPropertySheetPage;

    protected Component component;	// used for connectors
    protected Eip eip;	// used for eips
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ui.views.properties.tabbed.AbstractPropertySection#dispose()
     */
    @Override
    public void dispose() {
    	if (this.form != null) this.form.dispose();
        if (this.tabs.isEmpty() == false) {
        	for (CTabItem tab : this.tabs) {
        		if (!tab.isDisposed()) tab.dispose();
        	}
        	tabs.clear();
        }
    	if (this.tabFolder != null) this.tabFolder.dispose();    
        if (toolkit != null) {
            toolkit.dispose();
            toolkit = null;
        }
        this.aTabbedPropertySheetPage = null;
        this.component = null;
        this.eip = null;
        super.dispose();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ui.views.properties.tabbed.AbstractPropertySection#setInput
     * (org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
     */
    @Override
    public void setInput(IWorkbenchPart part, ISelection selection) {
    	super.setInput(part, selection);
    	
        this.dbc = new DataBindingContext();

        Object o = Selections.getFirstSelection(selection);
        CamelModelElement n = NodeUtils.toCamelElement(o);

        createTabFolder();        
        
        if (n.getUnderlyingMetaModelObject() != null) {
            this.selectedEP = n;
            this.eip = PropertiesUtils.getEipFor(selectedEP);
            String headerText = "";
            if (eip != null) headerText += Strings.convertCamelCase(eip.getName());
            headerText += String.format(" (%s)", DiagramUtils.filterFigureLabel(selectedEP.getDisplayText()));
            form.setText(headerText);
            if (selectedEP.isEndpointElement()) {
            	this.component = PropertiesUtils.getComponentFor(selectedEP);        
            }
        } else {
            this.selectedEP = null;
            form.setText("");
        }

        int idx = Math.max(tabFolder.getSelectionIndex(), 0);

        if (this.tabs.isEmpty() == false) {
        	for (CTabItem tab : this.tabs) {
        		if (!tab.isDisposed()) tab.dispose();
        	}
        	tabs.clear();
        }

        // now generate the tab contents
        createContentTabs(tabFolder);
        
        tabFolder.setSingle(tabFolder.getItemCount()==1);
        tabFolder.setSelection(idx >= tabFolder.getItemCount() ? 0 : idx);

        form.redraw();
        form.layout();
        form.update();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ui.views.properties.tabbed.AbstractPropertySection#createControls
     * (org.eclipse.swt.widgets.Composite,
     * org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage)
     */
    @Override
    public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
        super.createControls(parent, aTabbedPropertySheetPage);
        
        this.toolkit = new FormToolkit(parent.getDisplay());
        this.parent = parent;
        this.aTabbedPropertySheetPage = aTabbedPropertySheetPage;
        
        // now setup the file binding properties page
        parent.setLayout(new GridLayout());
        parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    }
    
    /**
     * creates the tab folder to hold all tabs
     */
    private void createTabFolder() {

    	if (this.form != null) form.dispose();
    	
    	this.form = this.toolkit.createForm(this.parent);
    	this.form.setLayoutData(new GridData(GridData.FILL_BOTH));
    	this.form.getBody().setLayout(new GridLayout(1, false));

    	
    	if (tabFolder != null) {
    		tabFolder.dispose();
    	}

    	tabFolder = new CTabFolder(form.getBody(), SWT.TOP | SWT.FLAT);
        toolkit.adapt(tabFolder, true, true);
        tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH));

        Color selectedColor = toolkit.getColors().getColor(IFormColors.SEPARATOR);
        tabFolder.setSelectionBackground(new Color[] { selectedColor, toolkit.getColors().getBackground() }, new int[] { 20 }, true);
        tabFolder.setCursor(FormsResources.getHandCursor());
        toolkit.paintBordersFor(tabFolder);

        toolkit.decorateFormHeading(form);
        
        form.layout();
        parent.layout(true);
        tabFolder.setSelection(0);
    }
    
    /**
     * creates the tabs needed to be displayed to users
     * 
     * @param tabFolder
     */
    protected abstract void createContentTabs(CTabFolder tabFolder);
    
	/**
	 * /!\ public fo rtest purpose only 
	 * @param toolkit 
	 * @param page The page on which it will be created
	 * @param p The property for which the label is generated
	 */
	public void createPropertyLabel(FormToolkit toolkit, Composite page, Parameter p) {
	    String s = computePropertyDisplayName(p);
	    Label l = toolkit.createLabel(page, s);         
	    l.setLayoutData(new GridData());
	    addDescriptionAsTooltip(p, l);
		if (isRequired(p)) {
	    	l.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
	    }
	}

	protected Display getDisplay() {
		return Display.getDefault();
	}

	protected String computePropertyDisplayName(Parameter parameter) {
		String s = Strings.humanize(parameter.getName());
		if(isRequired(parameter)){
			s += " *";
		}
	    if (isDeprecated(parameter)){
	    	s += " (deprecated)"; 
	    }
		return s;
	}
	
	private void addDescriptionAsTooltip(Parameter parameter, Label label) {
		String description = parameter.getDescription();
		if (description != null) {
	    	label.setToolTipText(description);
	    }
	}

	protected boolean isRequired(Parameter parameter) {
		return isParameterValueTrue(parameter.getRequired());
	}

	private boolean isDeprecated(Parameter parameter) {
		return isParameterValueTrue(parameter.getDeprecated());
	}
	
	private boolean isParameterValueTrue(String parameterValue) {
		return parameterValue != null && parameterValue.equalsIgnoreCase("true");
	}

	/**
	 * retrieves the camel model
	 * 
	 * @param modelElement
	 * @return
	 */
    protected CamelModel getCamelModel(CamelModelElement modelElement) {
    	String prjCamelVersion = CamelUtils.getCurrentProjectCamelVersion();
		// then get the meta model for the given camel version
		CamelModel model = CamelModelFactory.getModelForVersion(prjCamelVersion);
		if (model == null) {
			return null;
		}
		return model;
    }
    
    /**
     * called when user switches the expression language
     * 
     * @param language				the new language for the expression
     * @param eform					the expandable form to use
     * @param expressionElement		the expression element if simple expression, otherwise it will be the container element which contains the expression element as parameter "expression"
     * @param page					the page
     * @param prop					the property which is currently used
     */
    protected void languageChanged(String language, Composite eform, CamelModelElement expressionElement, Composite page, Parameter prop) {
        for (Control co : eform.getChildren()) if (co.getData("fuseExpressionClient") != null) co.dispose();
        Composite client = getWidgetFactory().createComposite(eform);
        client.setData("fuseExpressionClient", true);
        client.setLayoutData(new GridData(GridData.FILL_BOTH));
        client.setLayout(new GridLayout(4, false));
        
        CamelModelElement uiExpressionElement = null;
        
        if (prop.getName().equalsIgnoreCase("expression")) {
        	// normal expression subnode - no cascading -> when.<expression>
        	// the content of expressionElement is the language node itself
            if (expressionElement != null && expressionElement.getTranslatedNodeName().equals(language) == false) {
            	Node oldExpNode = null;
            	for (int i=0; i<selectedEP.getXmlNode().getChildNodes().getLength(); i++) {
            		if (org.fusesource.ide.foundation.core.util.CamelUtils.getTranslatedNodeName(selectedEP.getXmlNode().getChildNodes().item(i)).equals(expressionElement.getTranslatedNodeName())) {
            			oldExpNode = selectedEP.getXmlNode().getChildNodes().item(i);
            			break;
            		}
            	}
            	if (language.trim().length()>0) {
	            	Node expNode = selectedEP.createElement(language, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
	            	expressionElement = new CamelModelElement(this.selectedEP, expNode);
	            	selectedEP.setParameter(prop.getName(), expressionElement);
	            	selectedEP.getXmlNode().replaceChild(expNode, oldExpNode);
            	} else {
            		// user wants to delete the expression
            		selectedEP.getXmlNode().removeChild(oldExpNode);
            		selectedEP.removeParameter(prop.getName());
            	}
        	} else if (expressionElement == null && language.trim().length()>0) {
        		// no expression set, but now we set one
        		Node expNode = selectedEP.createElement(language, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
        		expressionElement = new CamelModelElement(this.selectedEP, expNode);
            	selectedEP.getXmlNode().insertBefore(expNode, selectedEP.getXmlNode().getFirstChild());
            	this.selectedEP.setParameter(prop.getName(), expressionElement);
            } 
            uiExpressionElement = expressionElement;

        } else {
        	
        	// cascaded expression subnode -> onException.handled.<expression>
        	// the content of expressionElement is the container element which holds the expression as parameter "expression"
        	if (expressionElement != null && expressionElement.getParameter("expression") != null) {
            	// 1. container element exists and expression element exists
        		Node oldExpNode = null;
            	List<String> langs = Arrays.asList(CamelComponentUtils.getOneOfList(prop));
            	for (int i=0; i<expressionElement.getXmlNode().getChildNodes().getLength(); i++) {
            		Node n = expressionElement.getXmlNode().getChildNodes().item(i);
            		if (langs.contains(org.fusesource.ide.foundation.core.util.CamelUtils.getTranslatedNodeName(n))) {
            			oldExpNode = n;
            			break;
            		}
            	}
            	CamelModelElement expElement = (CamelModelElement)expressionElement.getParameter("expression");
            	if (expElement.getTranslatedNodeName().equals(language) == false) {
            		if (language.trim().length()>0) {
	            		Node expNode = selectedEP.createElement(language, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
	                	uiExpressionElement = new CamelModelElement(expressionElement, expNode);
	                	expressionElement.getXmlNode().replaceChild(expNode, oldExpNode);
	                	expressionElement.setParameter("expression", uiExpressionElement);
            		} else {
            			// user deletes the expression
            			selectedEP.getXmlNode().removeChild(expressionElement.getXmlNode());
            			selectedEP.removeParameter(prop.getName());
            		}
            	} else {
            		uiExpressionElement = expElement;
            	}
        		
        	} else if (expressionElement != null && expressionElement.getParameter("expression") == null) {
        		// 2. container element exists but no expression element exists
        		Node expNode = selectedEP.createElement(language, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
        		uiExpressionElement = new CamelModelElement(expressionElement, expNode);
        		expressionElement.getXmlNode().appendChild(expNode);
            	expressionElement.setParameter("expression", uiExpressionElement);
        		
        	} else if (expressionElement == null && language.trim().length()>0) {
        		// 3. No container but language set
        		Node expContainerNode = selectedEP.createElement(prop.getName(), selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
        		Node expNode = selectedEP.createElement(language, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
        		CamelModelElement expContainerElement = new CamelModelElement(selectedEP, expContainerNode);
        		expressionElement = new CamelModelElement(expContainerElement, expNode);
        		expContainerElement.getXmlNode().appendChild(expNode);
            	selectedEP.getXmlNode().insertBefore(expContainerNode, selectedEP.getXmlNode().getFirstChild());
            	expContainerElement.setParameter("expression", expressionElement);
            	this.selectedEP.setParameter(prop.getName(), expContainerElement);
            	uiExpressionElement = expressionElement;        		
        	}
        }
        
        prepareExpressionUIForLanguage(language, uiExpressionElement, client);
		page.layout(true);
		refresh();
		eform.layout(true);
		aTabbedPropertySheetPage.resizeScrolledComposite();
    }
    
    /**
     * prepares the ui for expression elements
     * 
     * @param language
     * @param expressionElement
     * @param parent
     */
    protected void prepareExpressionUIForLanguage(String language, CamelModelElement expressionElement, Composite parent) {
    	CamelModel model = getCamelModel(expressionElement);
    	// now create the new fields
    	Language lang = model.getLanguageModel().getLanguageByName(language);
    	if (lang != null) {
    		List<Parameter> props = lang.getParameters();
    		props.sort(new ParameterPriorityComparator()); 
    		
    		for (Parameter p : props) {
    			createPropertyLabel(toolkit, parent, p);
                
                // Field
                Control field = getControlForParameter(p, parent, expressionElement, lang);
                field.setToolTipText(p.getDescription());
    		}
    	} else {
    		// seems to be not in language catalog - use eip catalog
    		Eip eip = model.getEipModel().getEIPByName(language);
    		if (eip != null) {
    			List<Parameter> props = eip.getParameters();
        		props.sort(new ParameterPriorityComparator()); 
        		
        		for (Parameter p : props) {
        			createPropertyLabel(toolkit, parent, p);
                    
                    // Field
                    Control field = getControlForParameter(p, parent, expressionElement, lang);
                    field.setToolTipText(p.getDescription());
        		}
    		}
    	}
    }
    
    /**
     * creates the control for the given parameter
     * 
     * @param p
     * @param parent
     * @param expressionElement
     * @param lang
     * @return
     */
    protected Control getControlForParameter(final Parameter p, Composite parent, final CamelModelElement expressionElement, Language lang) {
    	Control c = null;
    	
    	// BOOLEAN PROPERTIES
    	if (CamelComponentUtils.isBooleanProperty(p)) {
			final Button checkBox = getWidgetFactory().createButton(parent, "", SWT.CHECK);
    		Boolean b = Boolean.parseBoolean( (expressionElement != null && expressionElement.getParameter(p.getName()) != null ? (String)expressionElement.getParameter(p.getName()) : lang != null ? lang.getParameter(p.getName()).getDefaultValue() : p.getDefaultValue()));
    		checkBox.setSelection(b);
    		checkBox.addSelectionListener(new SelectionAdapter() {
	            /* (non-Javadoc)
	             * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
	             */
	            @Override
	            public void widgetSelected(SelectionEvent e) {
	            	Button chkBox = (Button)e.getSource();
	                expressionElement.setParameter(p.getName(), checkBox.getSelection());
	            }
	        });
			checkBox.setLayoutData(createPropertyFieldLayoutData());
    		c = checkBox;
        
    		// TEXT PROPERTIES
    	} else if (CamelComponentUtils.isTextProperty(p)) {
	        Text txtField = getWidgetFactory().createText(parent, (String)(expressionElement != null && expressionElement.getParameter(p.getName()) != null ? expressionElement.getParameter(p.getName()) : lang != null ? lang.getParameter(p.getName()).getDefaultValue() : p.getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.LEFT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                expressionElement.setParameter(p.getName(), txt.getText());
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;
        
	    // NUMBER PROPERTIES
	    } else if (CamelComponentUtils.isNumberProperty(p)) {
	        Text txtField = getWidgetFactory().createText(parent, (String)(expressionElement != null && expressionElement.getParameter(p.getName()) != null ? expressionElement.getParameter(p.getName()) : lang != null ? lang.getParameter(p.getName()).getDefaultValue() : p.getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.RIGHT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                String val = txt.getText();
	                try {
	                	Double.parseDouble(val);
	                	txt.setBackground(ColorConstants.white);
	                    expressionElement.setParameter(p.getName(), txt.getText());
	                } catch (NumberFormatException ex) {
	                	// invalid character found
	                    txt.setBackground(ColorConstants.red);
	                    return;
	                }
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;

        // OTHER
	    } else {
	    	Text txtField = getWidgetFactory().createText(parent, (String)(expressionElement != null && expressionElement.getParameter(p.getName()) != null ? expressionElement.getParameter(p.getName()) : lang != null ? lang.getParameter(p.getName()).getDefaultValue() : p.getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.LEFT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                expressionElement.setParameter(p.getName(), txt.getText());
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;
	    }
    	
    	return c;
    }
    
    /**
     * called when user switches the expression language
     * 
     * @param language				the new language for the expression
     * @param eform					the expandable form to use
     * @param dataFormatElement		the expression element if simple expression, otherwise it will be the container element which contains the expression element as parameter "expression"
     * @param page					the page
     * @param prop					the property which is currently used
     */
    protected void dataFormatChanged(String dataformat, Composite eform, CamelModelElement dataFormatElement, Composite page, Parameter prop) {
        for (Control co : eform.getChildren()) if (co.getData("fuseDataFormatClient") != null) co.dispose();
        Composite client = getWidgetFactory().createComposite(eform);
        client.setData("fuseDataFormatClient", true);
        client.setLayoutData(new GridData(GridData.FILL_BOTH));
        client.setLayout(new GridLayout(4, false));
        
        CamelModelElement uiDataFormatElement = null;
        
        if (dataFormatElement != null && dataFormatElement.getTranslatedNodeName().equals(dataformat) == false) {
        	Node oldExpNode = null;
        	for (int i=0; i<selectedEP.getXmlNode().getChildNodes().getLength(); i++) {
        		if (org.fusesource.ide.foundation.core.util.CamelUtils.getTranslatedNodeName(selectedEP.getXmlNode().getChildNodes().item(i)).equalsIgnoreCase(dataFormatElement.getTranslatedNodeName())) {
        			oldExpNode = selectedEP.getXmlNode().getChildNodes().item(i);
        			break;
        		}
        	}
        	if (dataformat.trim().length()>0) {
            	Node expNode = selectedEP.createElement(dataformat, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
            	dataFormatElement = new CamelModelElement(this.selectedEP, expNode);
            	selectedEP.setParameter(prop.getName(), dataFormatElement);
            	if (oldExpNode == null) {
            		System.err.println("pdd");
            	}
            	selectedEP.getXmlNode().replaceChild(expNode, oldExpNode);
        	} else {
        		// user wants to delete the expression
        		selectedEP.getXmlNode().removeChild(oldExpNode);
        		selectedEP.removeParameter(prop.getName());
        	}
    	} else if (dataFormatElement == null && dataformat.trim().length()>0) {
    		// no expression set, but now we set one
    		Node expNode = selectedEP.createElement(dataformat, selectedEP != null && selectedEP.getXmlNode() != null ? selectedEP.getXmlNode().getPrefix() : null);
    		dataFormatElement = new CamelModelElement(this.selectedEP, expNode);
        	selectedEP.getXmlNode().insertBefore(expNode, selectedEP.getXmlNode().getFirstChild());
        	this.selectedEP.setParameter(prop.getName(), dataFormatElement);
        } 
        uiDataFormatElement = dataFormatElement;
        
        prepareDataFormatUIForDataFormat(dataformat, uiDataFormatElement, client);
		page.layout(true);
		refresh();
		eform.layout(true);
		aTabbedPropertySheetPage.resizeScrolledComposite();
    }
    
    /**
     * prepares the ui for the data format element
     * 
     * @param dataformat
     * @param dataFormatElement
     * @param parent
     */
    protected void prepareDataFormatUIForDataFormat(String dataformat, CamelModelElement dataFormatElement, Composite parent) {
    	CamelModel model = getCamelModel(dataFormatElement);
    	
    	// now create the new fields
    	DataFormat df = model.getDataformatModel().getDataFormatByName(dataformat);
    	if (df != null) {
    		List<Parameter> props = df.getParameters();
    		props.sort(new ParameterPriorityComparator()); 
    		
    		for (Parameter p : props) {
                createPropertyLabel(toolkit, parent, p);
                
                // Field
                Control field = getControlForParameter(p, parent, dataFormatElement, df);
                field.setToolTipText(p.getDescription());
    		}
    	}
    }
    
    /**
     * returns the control for the given parameter
     * 
     * @param p
     * @param parent
     * @param dataFormatElement
     * @param df
     * @return
     */
    protected Control getControlForParameter(final Parameter p, Composite parent, final CamelModelElement dataFormatElement, DataFormat df) {
    	Control c = null;
    	
    	// BOOLEAN PROPERTIES
    	if (CamelComponentUtils.isBooleanProperty(p)) {
			final Button checkBox = getWidgetFactory().createButton(parent, "", SWT.CHECK);
    		String boolVal = dataFormatElement.getParameter(p.getName()) instanceof Boolean ? Boolean.toString((boolean)dataFormatElement.getParameter(p.getName())) : (String)dataFormatElement.getParameter(p.getName());
    		Boolean b = Boolean.parseBoolean( (dataFormatElement != null && dataFormatElement.getParameter(p.getName()) != null ? boolVal : df.getParameter(p.getName()).getDefaultValue()));
    		checkBox.setSelection(b);
    		checkBox.addSelectionListener(new SelectionAdapter() {
	            /* (non-Javadoc)
	             * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
	             */
	            @Override
	            public void widgetSelected(SelectionEvent e) {
	            	dataFormatElement.setParameter(p.getName(), checkBox.getSelection());
	            }
	        });
			checkBox.setLayoutData(createPropertyFieldLayoutData());
    		c = checkBox;
        
    		// TEXT PROPERTIES
    	} else if (CamelComponentUtils.isTextProperty(p)) {
	        Text txtField = getWidgetFactory().createText(parent, (String)(dataFormatElement != null && dataFormatElement.getParameter(p.getName()) != null ? dataFormatElement.getParameter(p.getName()) : df.getParameter(p.getName()).getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.LEFT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                dataFormatElement.setParameter(p.getName(), txt.getText());
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;
        
	    // NUMBER PROPERTIES
	    } else if (CamelComponentUtils.isNumberProperty(p)) {
	        Text txtField = getWidgetFactory().createText(parent, (String)(dataFormatElement != null && dataFormatElement.getParameter(p.getName()) != null ? dataFormatElement.getParameter(p.getName()) : df.getParameter(p.getName()).getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.RIGHT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                String val = txt.getText();
	                try {
	                	Double.parseDouble(val);
	                	txt.setBackground(ColorConstants.white);
	                	dataFormatElement.setParameter(p.getName(), txt.getText());
	                } catch (NumberFormatException ex) {
	                	// invalid character found
	                    txt.setBackground(ColorConstants.red);
	                    return;
	                }
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;

        // OTHER
	    } else {
	    	Text txtField = getWidgetFactory().createText(parent, (String)(dataFormatElement != null && dataFormatElement.getParameter(p.getName()) != null ? dataFormatElement.getParameter(p.getName()) : df.getParameter(p.getName()).getDefaultValue()), SWT.SINGLE | SWT.BORDER | SWT.LEFT);
	        txtField.addModifyListener(new ModifyListener() {
	            @Override
	            public void modifyText(ModifyEvent e) {
	                Text txt = (Text)e.getSource();
	                dataFormatElement.setParameter(p.getName(), txt.getText());
	            }
	        });
			txtField.setLayoutData(createPropertyFieldLayoutData());
	        c = txtField;
	    }
    	
    	return c;
    }

	protected void createHelpDecoration(Parameter parameter, Control control) {
		String description = parameter.getDescription();
		if (description != null) {
			ControlDecoration helpDecoration = new ControlDecoration(control, SWT.BOTTOM | SWT.LEFT);
			helpDecoration.setShowOnlyOnFocus(true);
			FieldDecoration fieldDecoration = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_INFORMATION);
			helpDecoration.setImage(fieldDecoration.getImage());
			helpDecoration.setDescriptionText(description);
			control.setToolTipText(description);
		}
	}

	protected GridData createPropertyFieldLayoutData() {
		return GridDataFactory.fillDefaults().indent(5, 0).span(3, 1).grab(true, false).create();
	}
}
