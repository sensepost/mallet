/**
 * Copyright (c) 2006-2012, JGraph Ltd */
package com.sensepost.mallet.swing;

import java.awt.Color;
import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.EventObject;
import java.util.Hashtable;

import javax.swing.ImageIcon;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mxgraph.examples.swing.editor.BasicGraphEditor;
import com.mxgraph.examples.swing.editor.EditorPalette;
import com.mxgraph.io.mxCodec;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.view.mxCellEditor;
import com.mxgraph.swing.view.mxICellEditor;
import com.mxgraph.util.mxDomUtils;
import com.mxgraph.util.mxResources;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.sensepost.mallet.util.XmlUtil;

public class GraphEditor extends BasicGraphEditor {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4601740824088314699L;

	/**
	 * Holds the shared number formatter.
	 * 
	 * @see NumberFormat#getInstance()
	 */
	public static final NumberFormat numberFormat = NumberFormat.getInstance();

	/**
	 * Holds the URL for the icon to be used as a handle for creating new
	 * connections. This is currently unused.
	 */
	public static URL url = null;

	// GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/connector.gif");

	private static final ImageIcon IMAGE_ROUNDED = new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png"));
	
	private static final ImageIcon IMAGE_RECTANGLE = new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rectangle.png"));
	
	private static final ImageIcon IMAGE_DOUBLERECTANGLE = new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/doublerectangle.png"));
	
	private static final ImageIcon IMAGE_HEXAGON = new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/hexagon.png"));
	
	public GraphEditor() {
		this("mxGraph Editor", new CustomGraphComponent(new CustomGraph()));
	}

	private Element createElement(Document doc, String type, String className, String... params) {
		Element e = doc.createElement(type);
		e.setAttribute("classname", className);
		if (params != null) {
			for (int i=0; i<params.length; i++) {
				e.appendChild(doc.createElement("Parameter")).
					appendChild(doc.createTextNode(params[i]));
			}
		}
		return e;
	}
	
	private void loadScriptHandler(Document xmlDocument, EditorPalette protocolPalette, String path) {
       try (InputStream in = GraphEditor.class.getResourceAsStream(path)) {
           if (in != null) {
               Element e = createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ScriptHandler", path);
               String[] parts = path.split("[/\\\\]");
               String fn = parts[parts.length - 1];
               String name = fn.split("\\.")[0];
               protocolPalette.addTemplate(name,
                       IMAGE_ROUNDED,
                       "rounded=1", 160, 120, e);
           }
       } catch (IOException ioe) {}
	}
	
	/**
	 * 
	 */
	public GraphEditor(String appTitle, mxGraphComponent component) {
		super(appTitle, component);
		final mxGraph graph = graphComponent.getGraph();

		Document xmlDocument = mxDomUtils.createDocument();
		Element listener = xmlDocument.createElement("Listener");
		listener.setAttribute("classname", "io.netty.channel.socket.nio.NioServerSocketChannel");
		listener.setAttribute("address", "localhost:1080");

		Element socks = createElement(xmlDocument, "ChannelHandler", 
				"com.sensepost.mallet.SocksInitializer");

		Element fixedHandler = createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.FixedTargetHandler", 
				"0.0.0.0:0");

		Element handler = createElement(xmlDocument, "ChannelHandler", 
				"io.netty.channel.ChannelDuplexHandler");

		Element logHandler = createElement(xmlDocument, "ChannelHandler", 
				"io.netty.handler.logging.LoggingHandler");

		Element intercept = createElement(xmlDocument, "Intercept", "com.sensepost.mallet.InterceptHandler", 
				"{InterceptController}");

		Element relay = createElement(xmlDocument, "Relay", "com.sensepost.mallet.RelayHandler", 
				"{InterceptController}");

		Element udpRelay = createElement(xmlDocument, "Relay", "com.sensepost.mallet.DatagramRelayHandler");

		Element targetHandler = createElement(xmlDocument, "IndeterminateChannelHandler", "com.sensepost.mallet.graph.TargetSpecificChannelHandler");
		
		Element socks5Handler = createElement(xmlDocument, "ChannelHandler", "io.netty.handler.proxy.Socks5ProxyHandler", 
				"0.0.0.0:1080");

		Element sink = xmlDocument.createElement("Sink");

		// Creates the shapes palette
		EditorPalette basicPalette = insertPalette(mxResources.get("basic"));
		EditorPalette protocolPalette = insertPalette(mxResources.get("protocolHandlers"));
		// EditorPalette symbolsPalette =
		// insertPalette(mxResources.get("symbols"));

        basicPalette.addTemplate("Listener", IMAGE_RECTANGLE, null, 160, 120, listener);
        basicPalette.addTemplate("Socks", IMAGE_ROUNDED, "rounded=1", 160, 120, socks);
        basicPalette.addTemplate("Target", IMAGE_ROUNDED, "rounded=1", 160, 120, fixedHandler);
        basicPalette.addTemplate("Handler", IMAGE_ROUNDED, "rounded=1", 160, 120, handler);
        loadScriptHandler(xmlDocument, basicPalette, "/com/sensepost/mallet/ScriptHandler.groovy");
        basicPalette.addTemplate("Logger", IMAGE_ROUNDED, "rounded=1", 160, 120, logHandler);
        basicPalette.addTemplate("Intercept", IMAGE_DOUBLERECTANGLE, "intercept;shape=doubleRectangle", 160, 120,
                intercept);
        basicPalette.addTemplate("Relay", IMAGE_DOUBLERECTANGLE, "relay;shape=doubleRectangle", 160, 120, relay);
        basicPalette.addTemplate("UDP Relay", IMAGE_DOUBLERECTANGLE, "relay;shape=doubleRectangle", 160, 120, udpRelay);

		basicPalette.addTemplate("TargetSpecific",
				IMAGE_HEXAGON,
				"shape=hexagon", 160, 120, targetHandler);
		basicPalette.addTemplate("Socks5 Client",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, socks5Handler);
		basicPalette.addTemplate("Sink",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/cylinder.png")),
				"shape=cylinder", 120, 160, sink);

		protocolPalette.addTemplate("Ssl Sniff",
				IMAGE_HEXAGON,
				"shape=hexagon", 160, 120, createElement(xmlDocument, "IndeterminateChannelHandler", "com.sensepost.mallet.ssl.SslSniffHandler"));

		protocolPalette.addTemplate("SSL Server",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.SslServerHandler", "{SSLServerCertificateMap}"));
		
		protocolPalette.addTemplate("SSL Client",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.SslClientHandler"));

		protocolPalette.addTemplate("Http2 SSL Server",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.Http2SslServerHandler", "{SSLServerCertificateMap}"));
		protocolPalette.addTemplate("Http2 SSL Client",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.Http2SslClientHandler"));

		protocolPalette.addTemplate("HTTP Sniff",
		        IMAGE_HEXAGON,
				"shape=hexagon", 160, 120, createElement(xmlDocument, "IndeterminateChannelHandler", "com.sensepost.mallet.handlers.http.HttpSniffHandler"));
		protocolPalette.addTemplate("HttpServerCodec",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpServerCodec"));
		protocolPalette.addTemplate("HttpClientCodec",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpClientCodec"));
		protocolPalette.addTemplate("HttpObjectAggregator",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpObjectAggregator", "1048576"));
		
        loadScriptHandler(xmlDocument, protocolPalette, "/com/sensepost/mallet/UpsideDownternet.groovy");
		
		loadScriptHandler(xmlDocument, protocolPalette, "/com/sensepost/mallet/StringCodec.groovy");
        
        protocolPalette.addTemplate("StringDecoder", IMAGE_ROUNDED, "rounded=1", 160, 120, createElement(xmlDocument,
                "ChannelHandler", "io.netty.handler.codec.string.StringDecoder", "io.netty.util.CharsetUtil.UTF_8"));
        
        protocolPalette.addTemplate("StringEncoder", IMAGE_ROUNDED, "rounded=1", 160, 120, createElement(xmlDocument,
                "ChannelHandler", "io.netty.handler.codec.string.StringEncoder", "io.netty.util.CharsetUtil.UTF_8"));

		protocolPalette.addTemplate("StringCodec", IMAGE_ROUNDED, "rounded=1", 160, 120, createElement(xmlDocument,
				"ChannelHandler", "com.sensepost.mallet.handlers.StringCodec", "io.netty.util.CharsetUtil.UTF_8"));

		protocolPalette.addTemplate("JsonObjectDecoder", IMAGE_ROUNDED, "rounded=1", 160, 120,
                createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.json.JsonObjectDecoder"));
		
        loadScriptHandler(xmlDocument, protocolPalette, "/com/sensepost/mallet/JsonCodec.groovy");
        		
		protocolPalette.addTemplate("SimpleBinaryModification",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.handlers.SimpleBinaryModificationHandler", "abcdef", "ABCDEF"));
		
		protocolPalette.addTemplate("ComplexBinaryModification",
				IMAGE_ROUNDED,
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.handlers.ComplexBinaryModificationHandler", "abcdef", "ABCDEF"));
				
	}

	/**
	 * 
	 */
	public static class CustomGraphComponent extends mxGraphComponent {

		/**
		 * 
		 */
		private static final long serialVersionUID = -6833603133512882012L;

		/**
		 * 
		 * @param graph
		 */
		public CustomGraphComponent(mxGraph graph) {
			super(graph);

			// Sets switches typically used in an editor
			setPageVisible(false);
			setGridVisible(false);
			setToolTips(true);
			getConnectionHandler().setCreateTarget(true);
			
			graph.setSplitEnabled(true);

			// Loads the default stylesheet from an external file
			mxCodec codec = new mxCodec();
			try {
				Document doc = XmlUtil.loadDocument(GraphEditor.class
						.getResource("/com/mxgraph/examples/swing/resources/default-style.xml").toString());
				codec.decode(doc.getDocumentElement(), graph.getStylesheet());
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			// Sets the background to white
			getViewport().setOpaque(true);
			getViewport().setBackground(Color.WHITE);
		}

		@Override
		protected mxICellEditor createCellEditor() {
			return new mxICellEditor() {
				private CustomCellEditor cce = new CustomCellEditor(CustomGraphComponent.this);
				private mxCellEditor ce = new mxCellEditor(CustomGraphComponent.this);
				private mxICellEditor editor = null;

				@Override
				public Object getEditingCell() {
					if (editor == null)
						return null;
					return editor.getEditingCell();
				}

				@Override
				public void startEditing(Object cell, EventObject trigger) {
					if (cell instanceof mxCell) {
						mxCell c = (mxCell) cell;
						if (c.isEdge()) {
							editor = ce;
						} else {
							editor = cce;
						}
						editor.startEditing(cell, trigger);
					}
				}

				@Override
				public void stopEditing(boolean cancel) {
					if (editor != null)
						editor.stopEditing(cancel);
					editor = null;
				}
			};
		}

		/**
		 * Overrides drop behaviour to set the cell style if the target is not a
		 * valid drop target and the cells are of the same type (eg. both
		 * vertices or both edges).
		 */
		public Object[] importCells(Object[] cells, double dx, double dy, Object target, Point location) {
			if (target == null && cells.length == 1 && location != null) {
				target = getCellAt(location.x, location.y);

				if (target instanceof mxICell && cells[0] instanceof mxICell) {
					mxICell targetCell = (mxICell) target;
					mxICell dropCell = (mxICell) cells[0];
					
					if (targetCell.isVertex() == dropCell.isVertex()) {
						// make target null, otherwise we create a group
						cells = super.importCells(cells, dx, dy, null, location);

						Object parent = graph.getModel().getParent(target);
						// we cloned it, so update the reference
						dropCell = (mxICell) cells[0];
						graph.insertEdge(parent, null, "", target, dropCell);
						
						graph.setSelectionCell(dropCell);
						
						return null;
					}
				}
			}

			return super.importCells(cells, dx, dy, target, location);
		}

	}

	/**
	 * A graph that creates new edges from a given template edge.
	 */
	public static class CustomGraph extends mxGraph {
		/**
		 * Holds the edge to be used as a template for inserting new edges.
		 */
		protected Object edgeTemplate;

		/**
		 * Custom graph that defines the alternate edge style to be used when
		 * the middle control point of edges is double clicked (flipped).
		 */
		public CustomGraph() {
//			setAlternateEdgeStyle("edgeStyle=mxEdgeStyle.ElbowConnector;elbow=vertical");
		}

		/**
		 * Sets the edge template to be used to inserting edges.
		 */
		public void setEdgeTemplate(Object template) {
			edgeTemplate = template;
		}

		private String quoteHtml(String text) {
			return text.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
		}
		
		/**
		 * Prints out some useful information about the cell in the tooltip.
		 */
		public String getToolTipForCell(Object cell) {
			mxGeometry geo = getModel().getGeometry(cell);
			mxCellState state = getView().getState(cell);

			Object v = getModel().getValue(cell);
			if (getModel().isEdge(cell)) {
				return v == null ? null : v.toString();
			} else {
				if (!(v instanceof Element))
					return null;
				String tip = "<html>";
				Element e = (Element) v;
				String classname = e.getAttribute("classname");
				tip += "Class: " + classname;
				String sa = e.getAttribute("address");
				if (sa != null && sa.length() > 0) {
					tip += "<br>Address: " + sa;
				} else {
					NodeList p = e.getElementsByTagName("Parameter");
					for (int i=0; i<p.getLength(); i++) {
						tip += "<br>" + quoteHtml(p.item(i).getTextContent());
					}
				}
				tip += "</html>";
				
				return tip;
			}

		}

		/**
		 * Overrides the method to use the currently selected edge template for
		 * new edges.
		 * 
		 * @param graph
		 * @param parent
		 * @param id
		 * @param value
		 * @param source
		 * @param target
		 * @param style
		 * @return
		 */
		public Object createEdge(Object parent, String id, Object value, Object source, Object target, String style) {
			if (edgeTemplate != null) {
				mxCell edge = (mxCell) cloneCells(new Object[] { edgeTemplate })[0];
				edge.setId(id);

				return edge;
			}

			return super.createEdge(parent, id, value, source, target, style);
		}

		@Override
		public String validateEdge(Object edge, Object source, Object target) {
			return super.validateEdge(edge, source, target);
		}

		@Override
		public String validateCell(Object cell, Hashtable<Object, Object> context) {
			Object value = getModel().getValue(cell);
			if (value instanceof Element) {
				Element element = (Element) value;
				String tagName = element.getTagName();
				Object[] incoming = getIncomingEdges(cell);
				if (incoming != null && incoming.length > 1)
					return "Only 1 incoming edge allowed";
				Object[] outgoing = getOutgoingEdges(cell);
				if ("Listener".equals(tagName)) {
					if (incoming != null && incoming.length != 0)
						return "Listener cannot have incoming edges";
					if (outgoing != null && outgoing.length != 1)
						return "Listener must have one outgoing edge";
				} else if (!"IndeterminateChannelHandler".equals(tagName) && outgoing != null && outgoing.length > 1) {
					return "Only one outgoing edge";
				} else if ("Relay".equals(tagName) && (outgoing == null || outgoing.length != 1)) {
					return "Relay must have an outgoing edge";
				} else if ("Sink".equals(tagName)) {
					if (incoming == null || incoming.length == 0)
						return "Sink must be the last vertex";
					if (outgoing != null && outgoing.length != 0)
						return "Sink must be the last vertex";
					String path = validatePath(cell);
					if (path != null)
						return path;
				}
			}
			return super.validateCell(cell, context);
		}

		private String validatePath(Object sink) {
			Object cell = sink;
			Object[] incoming = getIncomingEdges(cell);
			while (incoming != null && incoming.length == 1) {
				cell = getModel().getTerminal(incoming[0], true);
				Object value = getModel().getValue(cell);
				if (value instanceof Element) {
					Element element = (Element) value;
					String tagName = element.getTagName();
					if ("Relay".equals(tagName))
						return null;
				}
				incoming = getIncomingEdges(cell);
			}
			return "Any path with a Sink must have a Relay";
		}

		@Override
		public String convertValueToString(Object cell) {
			if (cell instanceof mxCell) {
				mxCell c = (mxCell) cell;
				Object v = c.getValue();
				if (v instanceof Element) {
					Element e = (Element) v;
					String t = e.getTagName();
					StringBuilder b = new StringBuilder(t);
					String className = e.getAttribute("classname");
					if (className != null) {
						int d = className.lastIndexOf('.');
						className = className.substring(d + 1);
						b.append("\n").append(className);
					}
					if ("Listener".equals(t)) {
						String sa = e.getAttribute("socketaddress");
						b.append("\n").append(sa);
					}
					return b.toString();
				}
			}
			return super.convertValueToString(cell);
		}

		@Override
		public boolean isValidDropTarget(Object cell, Object[] cells) {
			// FIXME: implement this to stop cells being dropped incorrectly
			return super.isValidDropTarget(cell, cells);
		}

	}

}
