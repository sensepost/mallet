/**
 * Copyright (c) 2006-2012, JGraph Ltd */
package com.sensepost.mallet.swing;

import java.awt.Color;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.util.EventObject;
import java.util.Hashtable;

import javax.swing.ImageIcon;
import javax.swing.UIManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mxgraph.examples.swing.editor.BasicGraphEditor;
import com.mxgraph.examples.swing.editor.EditorMenuBar;
import com.mxgraph.examples.swing.editor.EditorPalette;
import com.mxgraph.io.mxCodec;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxGraphTransferable;
import com.mxgraph.swing.util.mxSwingConstants;
import com.mxgraph.swing.view.mxCellEditor;
import com.mxgraph.swing.view.mxICellEditor;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxDomUtils;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
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

	public GraphEditor() {
		this("mxGraph Editor", new CustomGraphComponent(new CustomGraph()));
	}

	private Element createElement(Document doc, String type, String className, String... params) {
		Element e = doc.createElement(type);
		e.setAttribute("classname", className);
		if (params != null) {
			for (int i=0; i<params.length; i++) {
				e.appendChild(doc.createElement("Parameter")).
					appendChild(doc.createCDATASection(params[i]));
			}
		}
		return e;
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

		Element scriptHandler = createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ScriptHandler", 
				"import io.netty.channel.*;\r\n\r\nreturn new ChannelDuplexHandler();\r\n", 
				"groovy");

		Element logHandler = createElement(xmlDocument, "ChannelHandler", 
				"io.netty.handler.logging.LoggingHandler");

		Element intercept = createElement(xmlDocument, "Intercept", "com.sensepost.mallet.InterceptHandler", 
				"{InterceptController}");

		Element relay = createElement(xmlDocument, "Relay", "com.sensepost.mallet.RelayHandler", 
				"{InterceptController}");

		Element targetHandler = createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.graph.TargetSpecificChannelHandler");
		
		Element sink = xmlDocument.createElement("Sink");

		// Creates the shapes palette
		EditorPalette basicPalette = insertPalette(mxResources.get("basic"));
		EditorPalette protocolPalette =
				insertPalette(mxResources.get("protocolHandlers"));
		// EditorPalette symbolsPalette =
		// insertPalette(mxResources.get("symbols"));

		// Sets the edge template to be used for creating new edges if an edge
		// is clicked in the shape palette
		basicPalette.addListener(mxEvent.SELECT, new mxIEventListener() {
			public void invoke(Object sender, mxEventObject evt) {
				Object tmp = evt.getProperty("transferable");

				if (tmp instanceof mxGraphTransferable) {
					mxGraphTransferable t = (mxGraphTransferable) tmp;
					Object cell = t.getCells()[0];

					if (graph.getModel().isEdge(cell)) {
						((CustomGraph) graph).setEdgeTemplate(cell);
					}
				}
			}

		});

		// Adds some template cells for dropping into the graph
		// shapesPalette
		// .addTemplate(
		// "Container",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/swimlane.png")),
		// "swimlane", 280, 280, "Container");
		// shapesPalette
		// .addTemplate(
		// "Icon",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/rounded.png")),
		// "icon;image=/com/mxgraph/examples/swing/images/wrench.png",
		// 70, 70, "Icon");
		// shapesPalette
		// .addTemplate(
		// "Label",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/rounded.png")),
		// "label;image=/com/mxgraph/examples/swing/images/gear.png",
		// 130, 50, "Label");
		basicPalette.addTemplate("Listener",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rectangle.png")), null,
				160, 120, listener);
		basicPalette.addTemplate("Socks",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, socks);
		basicPalette.addTemplate("Target",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, fixedHandler);
		basicPalette.addTemplate("Handler",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, handler);
		basicPalette.addTemplate("Script",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, scriptHandler);
		basicPalette.addTemplate("Logger",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, logHandler);
		basicPalette.addTemplate("Intercept",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/doublerectangle.png")),
				"rectangle;shape=doubleRectangle", 160, 120, intercept);
		basicPalette.addTemplate("Relay",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/doublerectangle.png")),
				"rectangle;shape=doubleRectangle", 160, 120, relay);
		// shapesPalette
		// .addTemplate(
		// "Ellipse",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/ellipse.png")),
		// "ellipse", 160, 160, "");
		// shapesPalette
		// .addTemplate(
		// "Double Ellipse",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/doubleellipse.png")),
		// "ellipse;shape=doubleEllipse", 160, 160, "");
		// shapesPalette
		// .addTemplate(
		// "Triangle",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/triangle.png")),
		// "triangle", 120, 160, "");
		// shapesPalette
		// .addTemplate(
		// "Rhombus",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/rhombus.png")),
		// "rhombus", 160, 160, "");
		// shapesPalette
		// .addTemplate(
		// "Horizontal Line",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/hline.png")),
		// "line", 160, 10, "");
		basicPalette.addTemplate("TargetSpecific",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/hexagon.png")),
				"shape=hexagon", 160, 120, targetHandler);
		basicPalette.addTemplate("Sink",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/cylinder.png")),
				"shape=cylinder", 120, 160, sink);
		// shapesPalette
		// .addTemplate(
		// "Actor",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/actor.png")),
		// "shape=actor", 120, 160, "");
		// shapesPalette
		// .addTemplate(
		// "Cloud",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/cloud.png")),
		// "ellipse;shape=cloud", 160, 120, "");

		// shapesPalette
		// .addEdgeTemplate(
		// "Straight",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/straight.png")),
		// "straight", 120, 120, "");
		// shapesPalette
		// .addEdgeTemplate(
		// "Horizontal Connector",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/connect.png")),
		// null, 100, 100, "");
		// shapesPalette
		// .addEdgeTemplate(
		// "Vertical Connector",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/vertical.png")),
		// "vertical", 100, 100, "");
		// shapesPalette
		// .addEdgeTemplate(
		// "Entity Relation",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/entity.png")),
		// "entity", 100, 100, "");
		// shapesPalette
		// .addEdgeTemplate(
		// "Arrow",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/arrow.png")),
		// "arrow", 120, 120, "");

		// imagesPalette
		// .addTemplate(
		// "Bell",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/bell.png")),
		// "image;image=/com/mxgraph/examples/swing/images/bell.png",
		// 50, 50, "Bell");
		// imagesPalette
		// .addTemplate(
		// "Box",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/box.png")),
		// "image;image=/com/mxgraph/examples/swing/images/box.png",
		// 50, 50, "Box");
		// imagesPalette
		// .addTemplate(
		// "Cube",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/cube_green.png")),
		// "image;image=/com/mxgraph/examples/swing/images/cube_green.png",
		// 50, 50, "Cube");
		// imagesPalette
		// .addTemplate(
		// "User",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/dude3.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/dude3.png",
		// 50, 50, "User");
		// imagesPalette
		// .addTemplate(
		// "Earth",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/earth.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/earth.png",
		// 50, 50, "Earth");
		// imagesPalette
		// .addTemplate(
		// "Gear",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/gear.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/gear.png",
		// 50, 50, "Gear");
		// imagesPalette
		// .addTemplate(
		// "Home",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/house.png")),
		// "image;image=/com/mxgraph/examples/swing/images/house.png",
		// 50, 50, "Home");
		// imagesPalette
		// .addTemplate(
		// "Package",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/package.png")),
		// "image;image=/com/mxgraph/examples/swing/images/package.png",
		// 50, 50, "Package");
		// imagesPalette
		// .addTemplate(
		// "Printer",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/printer.png")),
		// "image;image=/com/mxgraph/examples/swing/images/printer.png",
		// 50, 50, "Printer");
		// imagesPalette
		// .addTemplate(
		// "Server",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/server.png")),
		// "image;image=/com/mxgraph/examples/swing/images/server.png",
		// 50, 50, "Server");
		// imagesPalette
		// .addTemplate(
		// "Workplace",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/workplace.png")),
		// "image;image=/com/mxgraph/examples/swing/images/workplace.png",
		// 50, 50, "Workplace");
		// imagesPalette
		// .addTemplate(
		// "Wrench",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/wrench.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/wrench.png",
		// 50, 50, "Wrench");
		//
		// symbolsPalette
		// .addTemplate(
		// "Cancel",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/cancel_end.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/cancel_end.png",
		// 80, 80, "Cancel");
		// symbolsPalette
		// .addTemplate(
		// "Error",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/error.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/error.png",
		// 80, 80, "Error");
		// symbolsPalette
		// .addTemplate(
		// "Event",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/event.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/event.png",
		// 80, 80, "Event");
		// symbolsPalette
		// .addTemplate(
		// "Fork",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/fork.png")),
		// "rhombusImage;image=/com/mxgraph/examples/swing/images/fork.png",
		// 80, 80, "Fork");
		// symbolsPalette
		// .addTemplate(
		// "Inclusive",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/inclusive.png")),
		// "rhombusImage;image=/com/mxgraph/examples/swing/images/inclusive.png",
		// 80, 80, "Inclusive");
		// symbolsPalette
		// .addTemplate(
		// "Link",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/link.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/link.png",
		// 80, 80, "Link");
		// symbolsPalette
		// .addTemplate(
		// "Merge",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/merge.png")),
		// "rhombusImage;image=/com/mxgraph/examples/swing/images/merge.png",
		// 80, 80, "Merge");
		// symbolsPalette
		// .addTemplate(
		// "Message",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/message.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/message.png",
		// 80, 80, "Message");
		// symbolsPalette
		// .addTemplate(
		// "Multiple",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/multiple.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/multiple.png",
		// 80, 80, "Multiple");
		// symbolsPalette
		// .addTemplate(
		// "Rule",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/rule.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/rule.png",
		// 80, 80, "Rule");
		// symbolsPalette
		// .addTemplate(
		// "Terminate",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/terminate.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/terminate.png",
		// 80, 80, "Terminate");
		// symbolsPalette
		// .addTemplate(
		// "Timer",
		// new ImageIcon(
		// GraphEditor.class
		// .getResource("/com/mxgraph/examples/swing/images/timer.png")),
		// "roundImage;image=/com/mxgraph/examples/swing/images/timer.png",
		// 80, 80, "Timer");
		
		protocolPalette.addTemplate("SSL Server",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.SslServerHandler", "{SSLServerCertificateMap}"));
		protocolPalette.addTemplate("SSL Client",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.SslClientHandler"));

		protocolPalette.addTemplate("Http2 SSL Server",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.Http2SslServerHandler", "{SSLServerCertificateMap}"));
		protocolPalette.addTemplate("Http2SSL Client",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ssl.Http2SslClientHandler"));
		
		protocolPalette.addTemplate("HttpServerCodec",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpServerCodec"));
		protocolPalette.addTemplate("HttpClientCodec",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpClientCodec"));
		protocolPalette.addTemplate("HttpObjectAggregator",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.http.HttpObjectAggregator", "1048576"));
		InputStream upsideDownStream = GraphEditor.class.getResourceAsStream("/com/sensepost/mallet/script.groovy");
		if (upsideDownStream != null) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(upsideDownStream))) {
				String line;
				StringBuffer buff = new StringBuffer();
				while ((line = br.readLine()) != null)
					buff.append(line).append("\n");
				Element upsidedown = createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ScriptHandler", 
						buff.toString(), "groovy");
				protocolPalette.addTemplate("UpsideDown-ternet",
						new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
						"rounded=1", 160, 120, upsidedown);
			} catch (IOException ioe) {}
		}
		
		protocolPalette.addTemplate("StringDecoder",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.string.StringDecoder", "io.netty.util.CharsetUtil.UTF_8"));
		
		protocolPalette.addTemplate("StringEncoder",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.string.StringEncoder", "io.netty.util.CharsetUtil.UTF_8"));
		

		protocolPalette.addTemplate("JsonFrameDecoder",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "io.netty.handler.codec.json.JsonObjectDecoder"));
		
		protocolPalette.addTemplate("JsonObjectCodec",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.ScriptHandler", "import com.fasterxml.jackson.databind.*;\nimport com.fasterxml.jackson.databind.node.*;\n\nimport io.netty.buffer.*;\nimport io.netty.channel.*;\nimport io.netty.handler.codec.*;\n\nimport java.util.List;\n\nreturn new ByteToMessageCodec<JsonNode>(JsonNode.class) {\n    private final ObjectMapper objectMapper = new ObjectMapper();\n\n    protected void encode(ChannelHandlerContext ctx, JsonNode msg, ByteBuf out) throws Exception {\n        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);\n        objectMapper.writeValue(byteBufOutputStream, msg);\n    }\n\n    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<JsonNode> out) throws Exception {\n        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(buf);\n        out.add(objectMapper.readTree(byteBufInputStream));\n    }\n\n};\n\n", "groovy"));
		
		protocolPalette.addTemplate("SimpleBinaryModification",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
				"rounded=1", 160, 120, createElement(xmlDocument, "ChannelHandler", "com.sensepost.mallet.handlers.SimpleBinaryModificationHandler", "abcdef", "ABCDEF"));
		
		protocolPalette.addTemplate("ComplexBinaryModification",
				new ImageIcon(GraphEditor.class.getResource("/com/mxgraph/examples/swing/images/rounded.png")),
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
			setAlternateEdgeStyle("edgeStyle=mxEdgeStyle.ElbowConnector;elbow=vertical");
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
			return super.validateCell(cell, context);
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

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		mxSwingConstants.SHADOW_COLOR = Color.LIGHT_GRAY;
		mxConstants.W3C_SHADOWCOLOR = "#D3D3D3";

		GraphEditor editor = new GraphEditor();
		editor.createFrame(new EditorMenuBar(editor)).setVisible(true);
	}
}
