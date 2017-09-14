/**
 * Copyright (c) 2007-2012, JGraph Ltd
 */
package com.mxgraph.examples.swing;

import java.io.IOException;
import java.util.Arrays;

import javax.swing.JFrame;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.mxgraph.io.mxCodec;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.handler.mxKeyboardHandler;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.util.mxDomUtils;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.util.mxUtils;
import com.mxgraph.util.mxXmlUtils;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxMultiplicity;

public class Validation extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8928982366041695471L;

	public Validation() {
		super("Hello, World!");

		Document xmlDocument = mxDomUtils.createDocument();
		Element listener = xmlDocument.createElement("Listener");
		listener.setAttribute("classname", "io.netty.channel.socket.nio.NioServerSocketChannel");
		listener.setAttribute("socketaddress", "localhost:1080");

		Element socks = xmlDocument.createElement("ChannelHandler");
		socks.setAttribute("classname", "com.sensepost.mallet.SocksInitializer");

		Element logger = xmlDocument.createElement("ChannelHandler");
		logger.setAttribute("classname", "io.netty.handler.logging.LoggingHandler");

		Element relay = xmlDocument.createElement("Relay");
		relay.setAttribute("classname", "com.sensepost.mallet.InterceptHandler");
		relay.appendChild(xmlDocument.createElement("Parameter"))
				.appendChild(xmlDocument.createCDATASection("{InterceptController}\r\nFoo\r\nBar\r\n< > & ]]> foobar\r\n"));

		Element sink = xmlDocument.createElement("Sink");

		mxGraph graph = new mxGraph();
		Object parent = graph.getDefaultParent();

		graph.getModel().beginUpdate();
		try {
			Object v1 = graph.insertVertex(parent, null, listener, 20, 20, 80, 30);
			Object v2 = graph.insertVertex(parent, null, socks, 120, 20, 80, 30);
			Object v2_5 = graph.insertVertex(parent, null, logger, 170, 20, 80, 30);
			Object v3 = graph.insertVertex(parent, null, relay, 220, 20, 80, 30);
			Object v3_5 = graph.insertVertex(parent, null, logger.cloneNode(true), 270, 20, 80, 30);
			Object v4 = graph.insertVertex(parent, null, sink, 320, 20, 80, 30);

			graph.insertEdge(parent, null, "", v1, v2);
			graph.insertEdge(parent, null, "", v2, v2_5);
			graph.insertEdge(parent, null, "", v2_5, v3);
			graph.insertEdge(parent, null, "", v3, v3_5);
			graph.insertEdge(parent, null, "", v3_5, v4);
			// Object e4 = graph.insertEdge(parent, null, "", v1, v4);
		} finally {
			graph.getModel().endUpdate();
		}

		mxMultiplicity[] multiplicities = new mxMultiplicity[3];

		multiplicities[0] = new mxMultiplicity(true, "Listener", null, null, 1, "1",
				Arrays.asList(new String[] { "ChannelHandler", "Relay" }),
				"Listener must have only 1 ChannelHandler or Relay", "Listener must connect to handler", true);

		// Source node does not want any incoming connections
		multiplicities[1] = new mxMultiplicity(false, "Listener", null, null, 0, "0", null,
				"Listener must have no incoming edge", null, true); // Type does
																	// not
																	// matter

		// Target needs exactly one incoming connection from Source
		multiplicities[2] = new mxMultiplicity(false, "ChannelHandler", null, null, 1, "1",
				Arrays.asList(new String[] { "Listener", "ChannelHandler" }),
				"ChannelHandler can only accept connections from a Listener or a previous Handler",
				"Handler must connect from Listener or Handler", true);

		graph.setMultiplicities(multiplicities);

		mxCodec codec = new mxCodec();
		String xml = mxXmlUtils.getXml(codec.encode(graph.getModel()));

		try {
			mxUtils.writeFile(xml, "validation2.mxe");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		final mxGraphComponent graphComponent = new mxGraphComponent(graph);
		graph.setMultigraph(false);
		graph.setAllowDanglingEdges(false);
		graphComponent.setConnectable(true);
		graphComponent.setToolTips(true);

		// Enables rubberband selection
		new mxRubberband(graphComponent);
		new mxKeyboardHandler(graphComponent);

		// Installs automatic validation (use editor.validation = true
		// if you are using an mxEditor instance)
		graph.getModel().addListener(mxEvent.CHANGE, new mxIEventListener() {
			public void invoke(Object sender, mxEventObject evt) {
				graphComponent.validateGraph();
			}
		});

		// Initial validation
		graphComponent.validateGraph();

		getContentPane().add(graphComponent);
	}

	public static void main(String[] args) {
		Validation frame = new Validation();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 320);
		frame.setVisible(true);
	}

}
