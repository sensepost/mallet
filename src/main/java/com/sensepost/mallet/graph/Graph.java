package com.sensepost.mallet.graph;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import javax.script.Bindings;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mxgraph.analysis.StructuralException;
import com.mxgraph.analysis.mxAnalysisGraph;
import com.mxgraph.analysis.mxGraphProperties;
import com.mxgraph.analysis.mxGraphStructure;
import com.mxgraph.io.mxCodec;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGraphModel.mxChildChange;
import com.mxgraph.model.mxGraphModel.mxGeometryChange;
import com.mxgraph.model.mxGraphModel.mxRootChange;
import com.mxgraph.model.mxGraphModel.mxValueChange;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxCellOverlay;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.util.mxUndoableEdit;
import com.mxgraph.util.mxUndoableEdit.mxUndoableChange;
import com.mxgraph.util.mxUtils;
import com.mxgraph.util.mxXmlUtils;
import com.mxgraph.view.mxGraph;
import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.DatagramRelayHandler;
import com.sensepost.mallet.InterceptHandler;
import com.sensepost.mallet.RelayHandler;

public class Graph implements GraphLookup {

	private boolean direct = true, socks = false;

	private mxGraphComponent graphComponent;
	private mxGraph graph;

	private Map<Class<? extends Channel>, EventLoopGroup> bossGroups = new HashMap<>();
	private Map<Class<? extends Channel>, EventLoopGroup> workerGroups = new HashMap<>();

	private Map<Object, Channel> channels = new HashMap<>();
	
	private WeakHashMap<ChannelHandler, Object> handlerVertexMap = new WeakHashMap<>();

	private Bindings scriptContext;

	private InstanceFactory instanceFactory;
	
	public Graph(final mxGraphComponent graphComponent, Bindings scriptContext) {
		this.instanceFactory = new InstanceFactory(scriptContext);
		this.graphComponent = graphComponent;
		this.graph = graphComponent.getGraph();
		this.scriptContext = scriptContext;
		
		graph.getModel().addListener(mxEvent.CHANGE, new mxIEventListener() {
			
			@Override
			public void invoke(Object sender, mxEventObject evt) {
				mxUndoableEdit edit = (mxUndoableEdit) evt.getProperty("edit");
				List<mxUndoableChange> changes = edit.getChanges();
				for (mxUndoableChange change : changes) {
					if (change instanceof mxRootChange) {
						try {
							startServers();
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else if (change instanceof mxValueChange) {
						mxValueChange vc = (mxValueChange) change;
						if (graph.getModel().isVertex(vc.getCell())) {
							stopStartServerFromChange(vc.getPrevious(), vc.getCell());
						}
					} else if (change instanceof mxChildChange) {
						mxChildChange cc = (mxChildChange) change;
						if (graph.getModel().isVertex(cc.getChild())) {
							Object[] incoming = graph.getIncomingEdges(cc.getChild());
							if (incoming == null || incoming.length == 0)
								stopStartServerFromChange(cc.getPrevious(), cc.getChild());
						}
					} else if (!(change instanceof mxGeometryChange)) {
						System.out.println("Change: " + change.getClass());
					}
				}
			}
		});

	}

	public mxGraph getGraph() {
		return graph;
	}

	public void loadGraph(File file) throws IOException {
		Document document = mxXmlUtils.parseXml(mxUtils.readFile(file.getAbsolutePath()));
		mxCodec codec = new mxCodec(document);
		codec.decode(document.getDocumentElement(), graph.getModel());
		layoutGraph(graph);
	}

	private void layoutGraph(mxGraph graph) {
		mxIGraphLayout layout = new mxHierarchicalLayout(graph);
		graph.getModel().beginUpdate();
		try {
			Object[] cells = graph.getChildCells(graph.getDefaultParent());
			for (int i = 0; i < cells.length; i++) {
				graph.updateCellSize(cells[i]);
			}

			layout.execute(graph.getDefaultParent());
		} finally {
			graph.getModel().endUpdate();
		}
	}

	private void startServersFromGraph() {
		mxAnalysisGraph aGraph = new mxAnalysisGraph();
		aGraph.setGraph(graph);

		mxGraphProperties.setDirected(aGraph.getProperties(), true);

		Object[] sourceVertices;
		try {
			sourceVertices = mxGraphStructure.getSourceVertices(aGraph);
		} catch (StructuralException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		for (int i = 0; i < sourceVertices.length; i++) {
			startServerFromSourceVertex(sourceVertices[i]).addListener(new AddServerChannelListener(sourceVertices[i]));
		}
	}

	private ChannelFuture startServerFromSourceVertex(Object vertex) {
		Object serverValue = graph.getModel().getValue(vertex);

		// parse getValue() for vertex to
		// determine what sort of EventLoopGroup we need, etc
		Class<? extends AbstractChannel> channelClass;
		try {
			channelClass = getChannelClass(getClassName(serverValue));
		} catch (ClassNotFoundException e) {
			addGraphException(vertex, e);
			return null;
		}
		SocketAddress address = parseSocketAddress(channelClass, serverValue);
		if (ServerChannel.class.isAssignableFrom(channelClass)) {
			Class<? extends ServerChannel> serverClass = (Class<? extends ServerChannel>) channelClass;
			ServerBootstrap b = new ServerBootstrap()
				.attr(ChannelAttributes.GRAPH, this).childOption(ChannelOption.AUTO_READ, true)
				.childOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
			b.channel(serverClass);
			b.childHandler(new GraphChannelInitializer(vertex));
			b.group(getEventGroup(bossGroups, channelClass, 1), getEventGroup(workerGroups, channelClass, 0));
			b.attr(ChannelAttributes.GRAPH, this);
			return b.bind(address);			
		} else {
			Bootstrap b = new Bootstrap().channel(channelClass)
					.group(getEventGroup(workerGroups, channelClass, 0))
					.handler(new GraphChannelInitializer(vertex));
			b.attr(ChannelAttributes.GRAPH, this);
			return b.bind(address);
		}
	}
	
	private ChannelFuture stopServerFromSourceValue(Object serverValue) throws ClassNotFoundException {
		if (channels == null || channels.size() == 0)
			return null;
		
		Channel channel = channels.remove(serverValue);
		if (channel != null) {
			return channel.close();
		}
		return null;
	}
	
	private EventLoopGroup getEventGroup(Map<Class<? extends Channel>, EventLoopGroup> cache,
			Class<? extends Channel> channelClass, int threads) {
		EventLoopGroup group = cache.get(channelClass);
		if (group != null)
			return group;
		if (AbstractNioChannel.class.isAssignableFrom(channelClass)) {
			group = new NioEventLoopGroup(threads);
			cache.put(channelClass, group);
			return group;
		}
		throw new IllegalArgumentException(channelClass.toString() + " is not supported yet");
	}

	/**
	 * assumes that o is a String on two lines, first line is the class of the
	 * server, second line is the socketaddress
	 * 
	 * @param channelClass
	 * @param o
	 *            the value Object for the server vertex
	 * @return the SocketAddress specified
	 */
	private SocketAddress parseSocketAddress(Class<? extends Channel> channelClass, Object o) {
		try {
			Method remoteAddress = channelClass.getMethod("remoteAddress", new Class<?>[0]);
			Class<?> ra = remoteAddress.getReturnType();
			if (InetSocketAddress.class.isAssignableFrom(ra)) {
				if (o instanceof String) {
					String sa = (String) o;
					if (sa.indexOf('\n') > -1) {
						sa = sa.substring(sa.indexOf('\n') + 1);
						return parseInetSocketAddress(sa);
					}
				} else if (o instanceof Element) {
					Element e = (Element) o;
					String sa = e.getAttribute("address");
					return parseInetSocketAddress(sa);
				}
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not parse the socket address from: '" + o + "'", e);
		}
		throw new IllegalArgumentException("Could not parse the socket address from: '" + o + "'");
	}

	private InetSocketAddress parseInetSocketAddress(String sa) {
		int c = sa.indexOf(':');
		if (c > -1) {
			String address = sa.substring(0, c);
			int port = Integer.parseInt(sa.substring(c + 1));
			return new InetSocketAddress(address, port);
			// FIXME: check that this is actually a bind-able
			// address?
		}
		throw new RuntimeException("Could not parse '" + sa + "' as an InetSocketAddress");
	}

	@SuppressWarnings("unchecked")
	private Class<? extends AbstractChannel> getChannelClass(String className) throws ClassNotFoundException {
		Class<?> clazz = Class.forName(className);
		if (AbstractChannel.class.isAssignableFrom(clazz))
			return (Class<? extends AbstractChannel>) clazz;
		throw new ClassCastException(className + " does not extend AbstractChannel");
	}

	private boolean isSink(Object v) {
		if (v instanceof String)
			return ("Connect".equals(v));
		if (v instanceof Element) {
			Element e = (Element) v;
			return "Sink".equals(e.getTagName());
		}
		if (v instanceof GraphNode)
			return false;
		throw new RuntimeException("Unexpected cell value");
	}

	private ChannelHandler[] getChannelHandlers(Object o)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		// FIXME: Add a handler to catch exceptions and link them back to the graph node
		// By inserting an exception catching handler after each "node", we can wrap the
		// exception with a "GraphNodeException" that contains a reference to the graph node
		// that threw the exception. When it reaches the end of the pipeline, we can then 
		// annotate the graph to show where the exception was thrown. This is useful in 
		// cases where you may have more than one of a particular handler in the graph
		// e.g. multiple SSLHandler's (client and server, for example)
		List<ChannelHandler> handlers = new ArrayList<ChannelHandler>();
		do {
			if (graph.getModel().isEdge(o))
				o = graph.getModel().getTerminal(o, false);
			Object v = graph.getModel().getValue(o);
			if (isSink(v))
				break;
			ChannelHandler h = getChannelHandler(v);
			handlers.add(h);
			handlers.add(new ExceptionCatcher(this, o));
			Object[] outgoing = graph.getOutgoingEdges(o);
			if (h instanceof IndeterminateChannelHandler) {
				IndeterminateChannelHandler ich = (IndeterminateChannelHandler) h;
				String[] options = new String[outgoing.length];
				for (int i = 0; i < outgoing.length; i++)
					options[i] = (String) graph.getModel().getValue(outgoing[i]);
				ich.setOutboundOptions(options);
			}
			if ((h instanceof InterceptHandler) || (h instanceof RelayHandler || (h instanceof DatagramRelayHandler))
					|| (h instanceof IndeterminateChannelHandler)) {
				handlerVertexMap.put(h, o);
				break;
			}
			if (outgoing == null || outgoing.length != 1)
				break;
			o = outgoing[0];
		} while (true);
		return handlers.toArray(new ChannelHandler[handlers.size()]);
	}

	private ChannelHandler getChannelHandler(Object o)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		String className = getClassName(o);
		String[] parameters = getParameters(o);
		Object handler = instanceFactory.getClassInstance(className, ChannelHandler.class, parameters);
		return (ChannelHandler) handler;
	}

	private String getClassName(Object o) {
		if (o instanceof String) {
			String s = (String) o;
			if (s.indexOf('\n') > -1)
				s = s.substring(0, s.indexOf('\n'));
			return s;
		} else if (o instanceof Element) {
			Element e = (Element) o;
			String className = e.getAttribute("classname");
			return className;
		}
		throw new RuntimeException("Don't know how to get classname from a " + o.getClass());
	}

	private String[] getParameters(Object o) {
		if (o instanceof GraphNode) {
			return ((GraphNode) o).getArguments();
		} else if (o instanceof Element) {
			Element e = (Element) o;
			NodeList parameters = e.getElementsByTagName("Parameter");
			String[] p = new String[parameters.getLength()];
			for (int i = 0; i < parameters.getLength(); i++) {
				Node n = parameters.item(i);
				p[i] = n.getTextContent();
			}
			return p;
		}
		throw new RuntimeException("Don't know how to get parameters from a " + o.getClass());
	}

	@Override
	public void startServers() {
		shutdownServers();
		startServersFromGraph();
	}

	@Override
	synchronized public ChannelHandler[] getNextHandlers(ChannelHandler handler, String option) {
		Object vertex = handlerVertexMap.remove(handler);
		Object[] outgoing = graph.getOutgoingEdges(vertex);
		try {
			for (Object edge : outgoing) {
				Object v = graph.getModel().getValue(edge);
				if (option.equals(v))
					return getChannelHandlers(graph.getModel().getTerminal(edge, false));
			}
			throw new NullPointerException("No match found for " + handler.getClass() + ", option " + option);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	synchronized public ChannelHandler[] getClientChannelInitializer(ChannelHandler handler) {
		return getClientChannelInitializer(handler, false);
	}

	@Override
	synchronized public ChannelHandler[] getClientChannelInitializer(ChannelHandler handler, boolean retain) {
		Object vertex = retain ? handlerVertexMap.get(handler) : handlerVertexMap.remove(handler);
		try {
			Object[] outgoing = graph.getOutgoingEdges(vertex);
			if (outgoing == null || outgoing.length != 1)
				throw new IllegalStateException("Exactly one outgoing edge allowed!");
			ArrayList<ChannelHandler> handlers = new ArrayList<ChannelHandler>(
					Arrays.asList(getChannelHandlers(outgoing[0])));
			handlers.add(0, handler);
			Collections.reverse(handlers); // FIXME: Decide where to do the
											// reversing, in the graph, or in
											// the caller
			return handlers.toArray(new ChannelHandler[handlers.size()]);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	synchronized public ChannelHandler[] getProxyInitializer(ChannelHandler handler, SocketAddress target) {
		if (direct)
			return new ChannelHandler[] { handler };
		else if (socks)
			return new ChannelHandler[] { new Socks5ProxyHandler(new InetSocketAddress("127.0.0.1", 1081)), handler };
		else
			return new ChannelHandler[] { new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 8080)), handler };
	}

	@Override
	public void shutdownServers() {
		List<ChannelFuture> closeFutures = new LinkedList<>();
		Iterator<Entry<Object, Channel>> it = channels.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Object, Channel> e = it.next();
			closeFutures.add(e.getValue().close());
			it.remove();
		}
		
		shutdownEventLoop(bossGroups);
		shutdownEventLoop(workerGroups);
	}

	private void shutdownEventLoop(Map<Class<? extends Channel>, EventLoopGroup> cache) {
		Iterator<Entry<Class<? extends Channel>, EventLoopGroup>> it = cache.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Class<? extends Channel>, EventLoopGroup> e = it.next();
			e.getValue().shutdownGracefully();
			it.remove();
		}
	}

	private void stopStartServerFromChange(Object previous, Object cell) {
		ChannelFuture stopFuture = null;
		if (previous != null) {
			// a Listener must have no incoming edges
			Object[] incomingPrevious = graph.getIncomingEdges(previous);
			if (incomingPrevious == null || incomingPrevious.length == 0) {
				try {
					stopFuture = stopServerFromSourceValue(previous);
				} catch (ClassNotFoundException e) {
					// It wasn't really a listener! No worries!
				}
			}
		}
		if (cell != null) {
			Object[] incomingNow = graph.getIncomingEdges(cell);
			if (incomingNow.length == 0) {
				ChannelFutureListener cfl = new StopAndStartChannelListener(cell);
				if (stopFuture == null) {
					try {
						cfl.operationComplete(null);
					} catch (Exception e) {
						addGraphException(cell, e);
					}
				} else
					stopFuture.addListener(cfl);
			}
		}
	}
	
	public void addGraphException(Object node, final Throwable cause) {
		String warning = cause.getLocalizedMessage();
		if (warning == null) {
			warning = cause.toString();
		}
		mxCellOverlay overlay = (mxCellOverlay) graphComponent.setCellWarning(node, warning);
		if (overlay != null)
			overlay.addMouseListener(new MouseAdapter() {
				/**
				 * Selects the associated cell in the graph
				 */
				public void mousePressed(MouseEvent e) {
					cause.printStackTrace();
				}
			});
	}
	
	private class GraphChannelInitializer extends ChannelInitializer<Channel> {

		private Object serverVertex;

		public GraphChannelInitializer(Object serverVertex) {
			this.serverVertex = serverVertex;
		}

		@Override
		protected void initChannel(Channel ch) throws Exception {
			Object[] edges = graph.getEdges(serverVertex);
			if (edges == null || edges.length == 0) {
				addGraphException(serverVertex, new IllegalStateException("No outbound edge"));
				ch.close();
				return;
			}
			if (edges.length > 1) {
				addGraphException(serverVertex, new IllegalStateException("Too many outbound edges"));
				ch.close();
				return;
			}

			ChannelPipeline p = ch.pipeline();
			String me = p.context(this).name();
			p.addAfter(me, null, new ExceptionCatcher(Graph.this, serverVertex));
			
			Object serverEdge = edges[0];
			ChannelHandler[] handlers = getChannelHandlers(serverEdge);
			if (ch.parent() != null) {
				GraphLookup gl = ch.parent().attr(ChannelAttributes.GRAPH).get();
				ch.attr(ChannelAttributes.GRAPH).set(gl);
			}
			p.addAfter(me, null, new ConnectionNumberChannelHandler());
			p.addLast(handlers);
		}
	}
	
	private class AddServerChannelListener implements ChannelFutureListener {
		private Object node;
		
		AddServerChannelListener(Object node) {
			this.node = node;
		}
		
		@Override
		public void operationComplete(ChannelFuture future)
				throws Exception {
			if (future.isSuccess()) {
				channels.put(node, future.channel());
			} else {
				addGraphException(node, future.cause());
			}
		}
		
	}
	
	private class StopAndStartChannelListener implements ChannelFutureListener {

		private Object vertex;
		
		public StopAndStartChannelListener(Object vertex) {
			this.vertex = vertex;
		}
		
		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			try {
				startServerFromSourceVertex(vertex).addListener(new AddServerChannelListener(vertex));
			} catch (Exception e) {
				addGraphException(vertex,  e);
			}
		}
		
	}
	
	private String elementAsString(Object o) {
		if (o instanceof Element) {
			Element e = (Element) o;
			try {
				TransformerFactory transFactory = TransformerFactory.newInstance();
				Transformer transformer = transFactory.newTransformer();
				StringWriter buffer = new StringWriter();
				transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
				transformer.transform(new DOMSource(e),
						new StreamResult(buffer));
				return buffer.toString();
			} catch (TransformerException e1) {
				e1.printStackTrace();
				return null;
			}
		} else {
			return String.valueOf(o);
		}
	}
}
