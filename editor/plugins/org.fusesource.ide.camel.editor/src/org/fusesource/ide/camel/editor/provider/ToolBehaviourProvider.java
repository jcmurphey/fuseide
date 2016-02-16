/*******************************************************************************
 * Copyright (c) 2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package org.fusesource.ide.camel.editor.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.ICreateConnectionFeature;
import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.features.context.IDoubleClickContext;
import org.eclipse.graphiti.features.context.IPictogramElementContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CustomContext;
import org.eclipse.graphiti.features.custom.ICustomFeature;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.palette.IPaletteCompartmentEntry;
import org.eclipse.graphiti.palette.IToolEntry;
import org.eclipse.graphiti.palette.impl.ObjectCreationToolEntry;
import org.eclipse.graphiti.palette.impl.PaletteCompartmentEntry;
import org.eclipse.graphiti.platform.IPlatformImageConstants;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.tb.ContextButtonEntry;
import org.eclipse.graphiti.tb.ContextMenuEntry;
import org.eclipse.graphiti.tb.DefaultToolBehaviorProvider;
import org.eclipse.graphiti.tb.IContextButtonEntry;
import org.eclipse.graphiti.tb.IContextButtonPadData;
import org.eclipse.graphiti.tb.IContextMenuEntry;
import org.eclipse.graphiti.tb.IDecorator;
import org.eclipse.graphiti.tb.ImageDecorator;
import org.fusesource.ide.camel.editor.CamelDesignEditor;
import org.fusesource.ide.camel.editor.features.create.ext.CreateConnectorFigureFeature;
import org.fusesource.ide.camel.editor.features.create.ext.CreateFigureFeature;
import org.fusesource.ide.camel.editor.features.custom.CollapseFeature;
import org.fusesource.ide.camel.editor.features.custom.DeleteAllEndpointBreakpointsFeature;
import org.fusesource.ide.camel.editor.features.custom.DoubleClickFeature;
import org.fusesource.ide.camel.editor.features.custom.GoIntoContainerFeature;
import org.fusesource.ide.camel.editor.features.custom.SetEndpointBreakpointFeature;
import org.fusesource.ide.camel.editor.internal.CamelEditorUIActivator;
import org.fusesource.ide.camel.editor.internal.UIMessages;
import org.fusesource.ide.camel.editor.provider.ext.ICustomPaletteEntry;
import org.fusesource.ide.camel.editor.provider.ext.PaletteCategoryItemProvider;
import org.fusesource.ide.camel.editor.utils.CamelUtils;
import org.fusesource.ide.camel.model.service.core.catalog.CamelModelFactory;
import org.fusesource.ide.camel.model.service.core.catalog.components.Component;
import org.fusesource.ide.camel.model.service.core.catalog.components.ComponentModel;
import org.fusesource.ide.camel.model.service.core.model.CamelElementConnection;
import org.fusesource.ide.camel.model.service.core.model.CamelModelElement;
import org.fusesource.ide.camel.validation.ValidationFactory;
import org.fusesource.ide.camel.validation.ValidationResult;
import org.fusesource.ide.foundation.core.util.Objects;
import org.fusesource.ide.foundation.core.util.Strings;
import org.fusesource.ide.launcher.debug.model.CamelConditionalBreakpoint;
import org.fusesource.ide.launcher.debug.model.CamelEndpointBreakpoint;
import org.fusesource.ide.launcher.debug.util.CamelDebugUtils;

/**
 * @author lhein
 */
public class ToolBehaviourProvider extends DefaultToolBehaviorProvider {

	public static final String PALETTE_ENTRY_PROVIDER_EXT_POINT_ID = "org.fusesource.ide.editor.paletteContributor";
	public static final String PALETTE_CATEGORY_NAME = "categoryName";
	public static final String PALETTE_ICON_ATTR = "paletteIcon";
	public static final String DIAGRAM_IMAGE_ATTR = "diagramImage";
	public static final String EXT_ID_ATTR = "id";

	private static HashMap<ICreateFeature, IConfigurationElement> paletteItemExtensions = new HashMap<ICreateFeature, IConfigurationElement>();

	private static final ArrayList<String> CONNECTORS_WHITELIST;

	static {
		CONNECTORS_WHITELIST = new ArrayList<String>();

		CONNECTORS_WHITELIST.add("activemq");
		CONNECTORS_WHITELIST.add("atom");
		CONNECTORS_WHITELIST.add("controlbus");
		CONNECTORS_WHITELIST.add("cxf");
		CONNECTORS_WHITELIST.add("cxfrs");
		CONNECTORS_WHITELIST.add("cxfbean");
		CONNECTORS_WHITELIST.add("direct");
		CONNECTORS_WHITELIST.add("direct-vm");
		CONNECTORS_WHITELIST.add("ejb");
		CONNECTORS_WHITELIST.add("file");
		CONNECTORS_WHITELIST.add("ftp");
		CONNECTORS_WHITELIST.add("ftps");
		CONNECTORS_WHITELIST.add("sftp");
		CONNECTORS_WHITELIST.add("imap");
		CONNECTORS_WHITELIST.add("imaps");
		// CONNECTORS_WHITELIST.add("infinispan"); // abandoned since fuse 6.2.0
		CONNECTORS_WHITELIST.add("jdbc");
		CONNECTORS_WHITELIST.add("jgroups");
		CONNECTORS_WHITELIST.add("jms");
		CONNECTORS_WHITELIST.add("language");
		CONNECTORS_WHITELIST.add("linkedin");
		CONNECTORS_WHITELIST.add("mina2");
		CONNECTORS_WHITELIST.add("mqtt");
		CONNECTORS_WHITELIST.add("mvel");
		CONNECTORS_WHITELIST.add("netty");
		CONNECTORS_WHITELIST.add("netty-http");
		CONNECTORS_WHITELIST.add("netty4");
		CONNECTORS_WHITELIST.add("netty4-http");
		CONNECTORS_WHITELIST.add("pop3");
		CONNECTORS_WHITELIST.add("pop3s");
		CONNECTORS_WHITELIST.add("quartz");
		CONNECTORS_WHITELIST.add("quartz2");
		CONNECTORS_WHITELIST.add("restlet");
		CONNECTORS_WHITELIST.add("rss");
		CONNECTORS_WHITELIST.add("salesforce");
		CONNECTORS_WHITELIST.add("sap-netweaver");
		CONNECTORS_WHITELIST.add("scheduler");
		CONNECTORS_WHITELIST.add("seda");
		CONNECTORS_WHITELIST.add("servlet");
		CONNECTORS_WHITELIST.add("smtp");
		CONNECTORS_WHITELIST.add("smtps");
		CONNECTORS_WHITELIST.add("sql");
		CONNECTORS_WHITELIST.add("timer");
		CONNECTORS_WHITELIST.add("vm");
		CONNECTORS_WHITELIST.add("xquery");
		CONNECTORS_WHITELIST.add("xslt");

		// CONNECTORS_WHITELIST.add("");
	}

	/**
	 * constructor
	 * 
	 * @param dtp
	 */
	public ToolBehaviourProvider(IDiagramTypeProvider dtp) {
		super(dtp);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getContextButtonPad(org.eclipse.graphiti.features.context.IPictogramElementContext)
	 */
	@Override
	public IContextButtonPadData getContextButtonPad(IPictogramElementContext context) {
		final IFeatureProvider featureProvider = getFeatureProvider();

		IContextButtonPadData data = super.getContextButtonPad(context);
		PictogramElement pe = context.getPictogramElement();
		Object bo = getFeatureProvider().getBusinessObjectForPictogramElement(pe);

		// 1. set the generic context buttons
		// note, that we do not add 'remove' (just as an example)
		setGenericContextButtons(data, pe, CONTEXT_BUTTON_DELETE | CONTEXT_BUTTON_UPDATE);

		// 2. set the collapse button
		CustomContext cc = new CustomContext(new PictogramElement[] { pe });
		ICustomFeature[] cf = getFeatureProvider().getCustomFeatures(cc);
		for (ICustomFeature f : cf) {
			if (f instanceof CollapseFeature && f.canExecute(cc)) {
				String image = IPlatformImageConstants.IMG_EDIT_COLLAPSE;
				String collapseExpand = "Collapse";

				if (Boolean.parseBoolean(
						Graphiti.getPeService().getPropertyValue(pe, CollapseFeature.PROP_COLLAPSED_STATE))) {
					image = IPlatformImageConstants.IMG_EDIT_EXPAND;
					collapseExpand = "Expand";
				}

				String name = "";
				if (bo instanceof CamelModelElement) {
					CamelModelElement bo2 = (CamelModelElement) bo;
					if (bo2 != null && bo2.getName() != null) {
						name = bo2.getName();
					}
				}

				IContextButtonEntry collapseButton = new ContextButtonEntry(f, cc);
				collapseButton.setDescription(collapseExpand + " " + name);
				collapseButton.setText(collapseExpand);
				collapseButton.setIconId(image);
				data.setCollapseContextButton(collapseButton);
			} else if (f instanceof GoIntoContainerFeature && f.canExecute(cc)) {
				IContextButtonEntry goIntoButton = new ContextButtonEntry(f, cc);
				goIntoButton.setDescription(f.getDescription());
				goIntoButton.setText(f.getName());
				goIntoButton.setIconId(ImageProvider.IMG_OUTLINE_TREE);
				data.getGenericContextButtons().add(goIntoButton);
			}
		}

		// 3. add one domain specific context-button, which offers all
		// available connection-features as drag&drop features...
		// 3.a. create new CreateConnectionContext
		{
			CreateConnectionContext ccc = new CreateConnectionContext();
			ccc.setSourcePictogramElement(pe);
			Anchor anchor = null;
			if (pe instanceof Anchor) {
				anchor = (Anchor) pe;
			} else if (pe instanceof AnchorContainer) {
				// assume, that our shapes always have chopbox anchors
				anchor = Graphiti.getPeService().getChopboxAnchor((AnchorContainer) pe);
			}
			ccc.setSourceAnchor(anchor);

			// 3.b. create context button and add all applicable features
			ContextButtonEntry button = new ContextButtonEntry(null, context);
			button.setText("Create connection"); //$NON-NLS-1$
			button.setIconId(ImageProvider.IMG_FLOW);
			ICreateConnectionFeature[] features = featureProvider.getCreateConnectionFeatures();
			for (ICreateConnectionFeature feature : features) {
				if (feature.isAvailable(ccc) && feature.canStartConnection(ccc))
					button.addDragAndDropFeature(feature);
			}

			// 3.c. add context button, if it contains at least one feature
			if (button.getDragAndDropFeatures().size() > 0) {
				data.getDomainSpecificContextButtons().add(button);
			}
		}

		// add buttons for breakpoint manipulation
		cc = new CustomContext(new PictogramElement[] { pe });
		cf = getFeatureProvider().getCustomFeatures(cc);
		for (ICustomFeature f : cf) {
			if (f instanceof DeleteAllEndpointBreakpointsFeature)
				continue;
			if (f instanceof SetEndpointBreakpointFeature) {
				if (f.isAvailable(cc)) {
					IContextButtonEntry button = new ContextButtonEntry(f, cc);
					data.getDomainSpecificContextButtons().add(button);

				}
			}
		}

		return data;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getContextMenu(org.eclipse.graphiti.features.context.ICustomContext)
	 */
	@Override
	public IContextMenuEntry[] getContextMenu(ICustomContext context) {
		List<IContextMenuEntry> entries = new LinkedList<IContextMenuEntry>();
		// create a menu-entry in the sub-menu for each custom feature
		IFeatureProvider fp = getFeatureProvider();
		ICustomFeature[] customFeatures = fp.getCustomFeatures(context);
		for (int i = 0; i < customFeatures.length; i++) {
			ICustomFeature customFeature = customFeatures[i];
			if (customFeature.isAvailable(context)) {
				ContextMenuEntry menuEntry = new ContextMenuEntry(customFeature, context);
				entries.add(menuEntry);
			}
		}

//		// create a sub-menu for all AddNode operations
//		ContextMenuEntry addNodesMenu = new ContextMenuEntry(null, null);
//		// set the menu label
//		addNodesMenu.setText(EditorMessages.camelMenuAddLabel); // $NON-NLS-1$
//		// set the description
//		addNodesMenu.setDescription("Add and connect new nodes"); //$NON-NLS-1$
//		// display sub-menu hierarchical or flat
//		addNodesMenu.setSubmenu(true);
//
//		ArrayList<IToolEntry> additionalToolEntries = new ArrayList<IToolEntry>();
//		additionalToolEntries.addAll(getAggregatedToolEntries());
//		// additionalToolEntries.addAll(getConnectorsToolEntries());
//		// additionalToolEntries.addAll(getExtensionPointToolEntries());
//
//		// new use a factory for building the menu structure
//		AddNodeMenuFactory f = new AddNodeMenuFactory();
//		f.setupMenuStructure(addNodesMenu, context, fp, additionalToolEntries);
//
//		entries.add(addNodesMenu);
//
//		addNodesMenu.add(new ContextMenuEntry(new AddRouteFeature(fp), context));

		return entries.toArray(new IContextMenuEntry[entries.size()]);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getPalette()
	 */
	@Override
	public IPaletteCompartmentEntry[] getPalette() {
		List<IPaletteCompartmentEntry> ret = new ArrayList<IPaletteCompartmentEntry>();

		// the folder for component types
		PaletteCompartmentEntry compartmentEntryComponents = new PaletteCompartmentEntry(
				UIMessages.connectorsDrawerTitle, null);
		ret.add(compartmentEntryComponents);
		compartmentEntryComponents.setInitiallyOpen(true);

		// the folder for endpoint types
		PaletteCompartmentEntry compartmentEntryEndpoints = new PaletteCompartmentEntry(UIMessages.endpointsDrawerTitle,
				null);
		ret.add(compartmentEntryEndpoints);
		compartmentEntryEndpoints.setInitiallyOpen(false);

		// the folder for routing types
		PaletteCompartmentEntry compartmentEntryRouting = new PaletteCompartmentEntry(UIMessages.routingDrawerTitle,
				null);
		ret.add(compartmentEntryRouting);
		compartmentEntryRouting.setInitiallyOpen(false);

		// the folder for control flow types
		PaletteCompartmentEntry compartmentEntryControlFlow = new PaletteCompartmentEntry(
				UIMessages.controlFlowDrawerTitle, null);
		ret.add(compartmentEntryControlFlow);
		compartmentEntryControlFlow.setInitiallyOpen(false);

		// the folder for transformation types
		PaletteCompartmentEntry compartmentEntryTransformation = new PaletteCompartmentEntry(
				UIMessages.transformationDrawerTitle, null);
		ret.add(compartmentEntryTransformation);
		compartmentEntryTransformation.setInitiallyOpen(false);

		// the folder for other types
		PaletteCompartmentEntry compartmentEntryMisc = new PaletteCompartmentEntry(UIMessages.miscellaneousDrawerTitle,
				null);
		ret.add(compartmentEntryMisc);
		compartmentEntryMisc.setInitiallyOpen(false);

		HashMap<String, PaletteCompartmentEntry> userdefinedEntries = new HashMap<String, PaletteCompartmentEntry>();

		ArrayList<IToolEntry> paletteItems = getAggregatedToolEntries();
		for (IToolEntry toolEntry : paletteItems) {
			if (toolEntry instanceof ObjectCreationToolEntry) {
				ObjectCreationToolEntry octe = (ObjectCreationToolEntry) toolEntry;
				if (octe.getCreateFeature() instanceof PaletteCategoryItemProvider) {
					PaletteCategoryItemProvider.CATEGORY_TYPE pcit = null;
					String catname = null;
					if (paletteItemExtensions.containsKey(octe.getCreateFeature())) {
						catname = paletteItemExtensions.get(octe.getCreateFeature())
								.getAttribute(PALETTE_CATEGORY_NAME);
						pcit = PaletteCategoryItemProvider.CATEGORY_TYPE.getCategoryType(catname);
					} else {
						// defaults to components
						pcit = ((PaletteCategoryItemProvider) octe.getCreateFeature()).getCategoryType();
						catname = ProviderHelper
								.convertCamelCase(((CreateFigureFeature) octe.getCreateFeature()).getCategoryName());
					}
					switch (pcit) {
					case COMPONENTS:
						compartmentEntryComponents.addToolEntry(toolEntry);
						break;
					case ENDPOINTS:
						compartmentEntryEndpoints.addToolEntry(toolEntry);
						break;
					case ROUTING:
						compartmentEntryRouting.addToolEntry(toolEntry);
						break;
					case CONTROL_FLOW:
						compartmentEntryControlFlow.addToolEntry(toolEntry);
						break;
					case TRANSFORMATION:
						compartmentEntryTransformation.addToolEntry(toolEntry);
						break;
					case MISCELLANEOUS:
						compartmentEntryMisc.addToolEntry(toolEntry);
						break;
					case USER_DEFINED:
						PaletteCompartmentEntry def = null;
						if (catname != null && userdefinedEntries.containsKey(catname) == false) {
							def = new PaletteCompartmentEntry(catname, null);
							def.setInitiallyOpen(false);
							userdefinedEntries.put(catname, def);
						}
						def = userdefinedEntries.get(catname);
						def.addToolEntry(toolEntry);
						break;
					case NONE:
					default: // do not add those items
						break;
					}
				}
			}
		}

		// finally add the user defined drawers to the return value
		for (PaletteCompartmentEntry e : userdefinedEntries.values()) {
			ret.add(e);
		}

		return ret.toArray(new IPaletteCompartmentEntry[ret.size()]);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getDecorators(org.eclipse.graphiti.mm.pictograms.PictogramElement)
	 */
	@Override
	public IDecorator[] getDecorators(PictogramElement pe) {
		List<IDecorator> decorators = new LinkedList<IDecorator>();

		// decorators only for visible elements
		if (pe.isVisible()) {
			// first we add super decorators
			IDecorator[] superDecorators = super.getDecorators(pe);
			for (IDecorator d : superDecorators)
				decorators.add(d);

			// and then our own
			IFeatureProvider featureProvider = getFeatureProvider();
			Object bo = featureProvider.getBusinessObjectForPictogramElement(pe);
			if (bo instanceof CamelModelElement) {
				CamelModelElement node = (CamelModelElement) bo;

				ValidationResult res = ValidationFactory.getInstance().validate(node);
				if (res.getInformationCount() > 0) {
					for (String message : res.getInformations()) {
						IDecorator imageRenderingDecorator = new ImageDecorator(
								IPlatformImageConstants.IMG_ECLIPSE_INFORMATION_TSK);
						imageRenderingDecorator.setMessage(message);
						decorators.add(imageRenderingDecorator);
					}
				}
				if (res.getWarningCount() > 0) {
					for (String message : res.getWarnings()) {
						IDecorator imageRenderingDecorator = new ImageDecorator(
								IPlatformImageConstants.IMG_ECLIPSE_WARNING_TSK);
						imageRenderingDecorator.setMessage(message);
						decorators.add(imageRenderingDecorator);
					}
				}
				if (res.getErrorCount() > 0) {
					for (String message : res.getErrors()) {
						IDecorator imageRenderingDecorator = new ImageDecorator(
								IPlatformImageConstants.IMG_ECLIPSE_ERROR_TSK);
						imageRenderingDecorator.setMessage(message);
						decorators.add(imageRenderingDecorator);
					}
				}

				// decorate breakpoints on endpoints
				if (getDiagramTypeProvider().getDiagramBehavior().getDiagramContainer() != null
						&& getDiagramTypeProvider().getDiagramBehavior()
								.getDiagramContainer() instanceof CamelDesignEditor) {

					CamelDesignEditor editor = (CamelDesignEditor) getDiagramTypeProvider().getDiagramBehavior()
							.getDiagramContainer();
					if (editor.getWorkspaceProject() != null) {
						IResource file = editor.getModel().getResource();
						String projectName = editor.getWorkspaceProject().getName();
						IBreakpoint bp = CamelDebugUtils.getBreakpointForSelection(node.getId(), file.getName(),
								projectName);
						if (bp != null && bp instanceof CamelEndpointBreakpoint) {
							CamelEndpointBreakpoint cep = (CamelEndpointBreakpoint) bp;

							// we only want to decorate breakpoints which belong
							// to this
							// project
							if (cep.getProjectName().equals(projectName)) {
								try {
									if (cep.isEnabled() && bp instanceof CamelConditionalBreakpoint) {
										// show enabled breakpoint decorator
										IDecorator imageRenderingDecorator = new ImageDecorator(
												ImageProvider.IMG_YELLOWDOT);
										imageRenderingDecorator.setMessage("");
										decorators.add(imageRenderingDecorator);
									} else if (cep.isEnabled() && bp instanceof CamelEndpointBreakpoint) {
										// show enabled breakpoint decorator
										IDecorator imageRenderingDecorator = new ImageDecorator(
												ImageProvider.IMG_REDDOT);
										imageRenderingDecorator.setMessage("");
										decorators.add(imageRenderingDecorator);
									} else {
										// show disabled breakpoint decorator
										IDecorator imageRenderingDecorator = new ImageDecorator(
												ImageProvider.IMG_GRAYDOT);
										imageRenderingDecorator.setMessage("");
										decorators.add(imageRenderingDecorator);
									}
								} catch (CoreException e) {
									CamelEditorUIActivator.pluginLog().logError(e);
								}
							}
						}
					}
				}

				return decorators.toArray(new IDecorator[decorators.size()]);
			}
		}

		return super.getDecorators(pe);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getToolTip(org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm)
	 */
	@Override
	public String getToolTip(GraphicsAlgorithm ga) {
		PictogramElement pe = ga.getPictogramElement();
		Object bo = getFeatureProvider().getBusinessObjectForPictogramElement(pe);
		if (bo instanceof CamelModelElement) {
			String name = ((CamelModelElement) bo).getDescription(); // getDisplayToolTip();
			if (name != null && !name.isEmpty()) {
				return name;
			}
		}
		return (String) super.getToolTip(ga);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#equalsBusinessObjects(java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean equalsBusinessObjects(Object o1, Object o2) {
		if (o1 instanceof CamelModelElement || o2 instanceof CamelModelElement) {
			return Objects.equal(o1, o2);
		}
		return super.equalsBusinessObjects(o1, o2);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getClickArea(org.eclipse.graphiti.mm.pictograms.PictogramElement)
	 */
	@Override
	public GraphicsAlgorithm[] getClickArea(PictogramElement pe) {
		IFeatureProvider featureProvider = getFeatureProvider();
		Object bo = featureProvider.getBusinessObjectForPictogramElement(pe);
		if (bo instanceof CamelModelElement && !(bo instanceof CamelElementConnection)) {
			GraphicsAlgorithm rectangle = pe.getGraphicsAlgorithm();
			return new GraphicsAlgorithm[] { rectangle };
		}
		return super.getClickArea(pe);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getSelectionBorder(org.eclipse.graphiti.mm.pictograms.PictogramElement)
	 */
	@Override
	public GraphicsAlgorithm getSelectionBorder(PictogramElement pe) {
		IFeatureProvider featureProvider = getFeatureProvider();
		Object bo = featureProvider.getBusinessObjectForPictogramElement(pe);
		if (bo instanceof CamelModelElement) {
			GraphicsAlgorithm rectangle = pe.getGraphicsAlgorithm();
			return rectangle;
		}
		return super.getSelectionBorder(pe);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#isMultiSelectionEnabled()
	 */
	@Override
	public boolean isMultiSelectionEnabled() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.tb.DefaultToolBehaviorProvider#getDoubleClickFeature(org.eclipse.graphiti.features.context.IDoubleClickContext)
	 */
	@Override
	public ICustomFeature getDoubleClickFeature(IDoubleClickContext context) {
		return new DoubleClickFeature(getFeatureProvider());
	}
	
	/**
	 * returns a list of tool entries for the palette - generated from the EIP 
	 * catalog entries and from the default graphiti ones
	 * 
	 * @return
	 */
	public ArrayList<IToolEntry> getPredefinedToolEntries() {
		ArrayList<IToolEntry> entries = new ArrayList<IToolEntry>();

		// add compartments from super class and skip first as its the
		// connection menu
		IPaletteCompartmentEntry[] superCompartments = super.getPalette();
		for (int i = 1; i < superCompartments.length; i++) {
			IPaletteCompartmentEntry entry = superCompartments[i];
			for (IToolEntry toolEntry : entry.getToolEntries()) {
				if (toolEntry instanceof ObjectCreationToolEntry) {
					ObjectCreationToolEntry octe = (ObjectCreationToolEntry) toolEntry;
					if (octe.getCreateFeature() instanceof PaletteCategoryItemProvider) {
						entries.add(octe);
					}
				}
			}
		}

		// sort the palette entries
		Collections.sort(entries, new Comparator<IToolEntry>() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see java.util.Comparator#compare(java.lang.Object,
			 * java.lang.Object)
			 */
			@Override
			public int compare(IToolEntry o1, IToolEntry o2) {
				return o1.getLabel().compareToIgnoreCase(o2.getLabel());
			}
		});

		return entries;
	}

	/**
	 * returns a list of tool entries for the palette - generated from the 
	 * extension point we provide 
	 * 
	 * @return
	 */
	public ArrayList<IToolEntry> getExtensionPointToolEntries() {
		ArrayList<IToolEntry> entries = new ArrayList<IToolEntry>();

		// inject palette entries delivered via extension points
		IConfigurationElement[] extensions = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(PALETTE_ENTRY_PROVIDER_EXT_POINT_ID);
		for (IConfigurationElement e : extensions) {
			try {
				final Object o = e.createExecutableExtension("class");

				if (o instanceof ICustomPaletteEntry) {
					ICustomPaletteEntry pe = (ICustomPaletteEntry) o;
					ICreateFeature cf = pe.newCreateFeature(getFeatureProvider());
					String paletteIcon = Strings.isBlank(e.getAttribute(PALETTE_ICON_ATTR)) ? cf.getCreateImageId()
							: ImageProvider.PREFIX + e.getAttribute(EXT_ID_ATTR) + ImageProvider.POSTFIX_SMALL;
					String diagramImg = Strings.isBlank(e.getAttribute(DIAGRAM_IMAGE_ATTR)) ? cf.getCreateLargeImageId()
							: ImageProvider.PREFIX + e.getAttribute(EXT_ID_ATTR) + ImageProvider.POSTFIX_LARGE;
					IToolEntry te = new ObjectCreationToolEntry(cf.getName(), cf.getDescription(), paletteIcon,
							diagramImg, cf);
					entries.add(te);
					paletteItemExtensions.put(cf, e);
				}
			} catch (CoreException ex) {
				ex.printStackTrace();
				CamelEditorUIActivator.pluginLog().logError(ex);
				continue;
			}
		}

		// sort the palette entries
		Collections.sort(entries, new Comparator<IToolEntry>() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see java.util.Comparator#compare(java.lang.Object,
			 * java.lang.Object)
			 */
			@Override
			public int compare(IToolEntry o1, IToolEntry o2) {
				return o1.getLabel().compareToIgnoreCase(o2.getLabel());
			}
		});

		return entries;
	}

	/**
	 * returns a list of tool entries generated from the component/connector 
	 * catalog entries
	 * 
	 * @return
	 */
	public ArrayList<IToolEntry> getConnectorsToolEntries() {
		ArrayList<IToolEntry> entries = new ArrayList<IToolEntry>();

		// inject palette entries generated out of the component model file
		String camelVersion = CamelUtils.getCurrentProjectCamelVersion();

		ComponentModel componentModel = CamelModelFactory.getModelForVersion(camelVersion).getComponentModel();
		for (Component component : componentModel.getSupportedComponents()) {
			if (shouldBeIgnored(component.getSchemeTitle()))
				continue;
			ICreateFeature cf = new CreateConnectorFigureFeature(getFeatureProvider(), component);
			IToolEntry te = new ObjectCreationToolEntry(cf.getName(), cf.getDescription(), cf.getCreateImageId(),
					cf.getCreateLargeImageId(), cf);
			entries.add(te);
		}

		// sort the palette entries
		Collections.sort(entries, new Comparator<IToolEntry>() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see java.util.Comparator#compare(java.lang.Object,
			 * java.lang.Object)
			 */
			@Override
			public int compare(IToolEntry o1, IToolEntry o2) {
				return o1.getLabel().compareToIgnoreCase(o2.getLabel());
			}
		});

		return entries;
	}

	/**
	 * returns a list of all aggregated tool entries from all sources
	 * 
	 * @return
	 */
	public ArrayList<IToolEntry> getAggregatedToolEntries() {
		ArrayList<IToolEntry> entries = new ArrayList<IToolEntry>();

		entries.addAll(getPredefinedToolEntries());
		entries.addAll(getConnectorsToolEntries());
		entries.addAll(getExtensionPointToolEntries());

		// sort the palette entries
		Collections.sort(entries, new Comparator<IToolEntry>() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see java.util.Comparator#compare(java.lang.Object,
			 * java.lang.Object)
			 */
			@Override
			public int compare(IToolEntry o1, IToolEntry o2) {
				return o1.getLabel().compareToIgnoreCase(o2.getLabel());
			}
		});

		return entries;
	}

	/**
	 * checks whether a component should be ignored or not and therefore not put
	 * onto the palette of the editor
	 * 
	 * @param connectorId
	 * @return
	 */
	private boolean shouldBeIgnored(String connectorId) {
		return !CONNECTORS_WHITELIST.contains(connectorId);
	}
}
