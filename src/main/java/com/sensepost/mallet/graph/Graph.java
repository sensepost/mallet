package com.sensepost.mallet.graph;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import javax.script.Bindings;

import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mxgraph.analysis.StructuralException;
import com.mxgraph.analysis.mxAnalysisGraph;
import com.mxgraph.analysis.mxGraphProperties;
import com.mxgraph.analysis.mxGraphStructure;
import com.mxgraph.model.mxGraphModel.mxChildChange;
import com.mxgraph.model.mxGraphModel.mxRootChange;
import com.mxgraph.model.mxGraphModel.mxValueChange;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxCellOverlay;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.util.mxUndoableEdit;
import com.mxgraph.util.mxUndoableEdit.mxUndoableChange;
import com.mxgraph.view.mxGraph;
import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.DatagramRelayHandler;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.RelayHandler;
import com.sensepost.mallet.channel.SubChannelHandler;
import com.sensepost.mallet.model.ChannelEvent;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

public class Graph implements GraphLookup {

	private boolean direct = true, socks = false;

	private mxGraphComponent graphComponent;
	private mxGraph graph;

	private Map<Class<? extends Channel>, EventLoopGroup> bossGroups = new HashMap<>();
	private Map<Class<? extends Channel>, EventLoopGroup> workerGroups = new HashMap<>();

	private Map<Object, Channel> channels = new HashMap<>();

	private WeakHashMap<ChannelHandler, Object> handlerVertexMap = new WeakHashMap<>();

	private Bindings scriptContext;
	private InterceptController controller;

	private InstanceFactory instanceFactory;

	public Graph(final mxGraphComponent graphComponent, InterceptController controller, Bindings scriptContext) {
		this.instanceFactory = new InstanceFactory(scriptContext);
		this.graphComponent = graphComponent;
		this.graph = graphComponent.getGraph();
		this.scriptContext = scriptContext;
		this.controller = controller;

		// Listen for new graphs being loaded, and stop and restart the listeners
		graph.getModel().addListener(mxEvent.CHANGE, new mxIEventListener() {
			@Override
			public void invoke(Object sender, mxEventObject evt) {
				mxUndoableEdit edit = (mxUndoableEdit) evt.getProperty("edit");
				List<mxUndoableChange> changes = edit.getChanges();
				for (mxUndoableChange change : changes) {
					if (change instanceof mxRootChange) {
						graph.getModel().beginUpdate();
						try {
							upgradeGraph(((mxRootChange) change).getRoot());
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							graph.getModel().endUpdate();
						}
					}
				}
				styleEdges(graph);
			}
		});
		
		// Listen for deletion of cells, and, if possible, reconnect the vertices
		// on either side
		graph.addListener(mxEvent.CELLS_REMOVED, new mxIEventListener() {

			@Override
			public void invoke(Object sender, mxEventObject evt) {
				Object[] cells = (Object[]) evt.getProperties().get("cells");
				Set<Object> sources = new HashSet<>();
				Set<Object> targets = new HashSet<>();
				Set<Object> deleted = new HashSet<>();
				for (int i = 0; i < cells.length; i++) {
					if (!graph.getModel().isVertex(cells[i])) {
						sources.add(graph.getModel().getTerminal(cells[i], true));
						targets.add(graph.getModel().getTerminal(cells[i], false));
					} else {
						deleted.add(cells[i]);
					}
				}
				sources.removeAll(deleted);
				targets.removeAll(deleted);
				if (sources.size() == 1 && targets.size() == 1) {
					graph.insertEdge(graph.getDefaultParent(), null, null, sources.iterator().next(),
							targets.iterator().next());
				}
			}

		});
		// Listen for changes to the nodes representing the listeners, and stop
		// and restart them if necessary
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
					}
				}
			}
		});
		// Keep the graph validation up to date
		graph.getModel().addListener(mxEvent.CHANGE, new mxIEventListener() {
			public void invoke(Object sender, mxEventObject evt) {
				graphComponent.validateGraph();
			}
		});
		startServers();
	}

	public mxGraph getGraph() {
		return graph;
	}

	/*
	 * Upgrades an older graph to the current format
	 */
	private void upgradeGraph(Object root) {
		Object[] cells = graph.getChildCells(root);
		if (cells == null)
			return;
		for (int i = 0; i < cells.length; i++) {
			Object cell = cells[i];
			upgradeGraph(cell);

			if (graph.getModel().isVertex(cells[i])) {
				Object value = graph.getModel().getValue(cell);
				if (!(value instanceof Element))
					return;
				Element e = (Element) value;
				if ("Relay".equals(e.getTagName())) {
					graph.getModel().setStyle(cell, "relay");
				} else if ("Intercept".equals(e.getTagName())) {
					graph.getModel().setStyle(cell, "intercept");
				} else if ("ChannelHandler".equals(e.getTagName())) {
					if ("com.sensepost.mallet.graph.TargetSpecificChannelHandler".equals(e.getAttribute("classname"))) {
						e.getOwnerDocument().renameNode(e, null, "IndeterminateChannelHandler");
						graph.getModel().setValue(cell, e);
					}
				}
			}
		}
	}

	/*
	 * styles the edges of the inbound graph and outbound graph
	 */
	private void styleEdges(mxGraph graph) {
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
		if (sourceVertices == null)
			return;

		try {
			graph.getModel().beginUpdate();
			stylePath(graph, sourceVertices, "incomingEdge");
		} finally {
			graph.getModel().endUpdate();
		}
	}

	private void stylePath(mxGraph graph, Object[] cells, String style) {
		mxIGraphModel model = graph.getModel();
		for (int i = 0; i < cells.length; i++) {
			Object cell = cells[i];
			String thisStyle = style;
			if (model.isEdge(cell)) {
				graph.setCellStyle(thisStyle, new Object[] { cell });
				cell = model.getTerminal(cell, false);
			}
			Object[] edges = graph.getOutgoingEdges(cell);
			thisStyle = isRelay(graph, cell) ? "outgoingEdge" : style;
			if (edges != null)
				stylePath(graph, edges, thisStyle);
		}
	}

	private boolean isRelay(mxGraph graph, Object cell) {
		Object value = graph.getModel().getValue(cell);
		String className = getClassName(value);
		if (className == null || "".equals(className))
			return false;
		try {
			Class<?> clazz = Class.forName(className);
			if (RelayHandler.class.isAssignableFrom(clazz) || DatagramRelayHandler.class.isAssignableFrom(clazz)) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
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
		} catch (ClassNotFoundException | ClassCastException e) {
			// addGraphException(vertex, e);
			return null;
		}
		SocketAddress address = parseSocketAddress(channelClass, serverValue);
		if (ServerChannel.class.isAssignableFrom(channelClass)) {
			@SuppressWarnings("unchecked")
			Class<? extends ServerChannel> serverClass = (Class<? extends ServerChannel>) channelClass;
			ServerBootstrap b = new ServerBootstrap().handler(new LoggingHandler()).attr(ChannelAttributes.GRAPH, this)
					.childOption(ChannelOption.AUTO_READ, true).childOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
			b.channel(serverClass);
			ChannelInitializer<Channel> initializer = new GraphChannelInitializer(vertex);
			initializer = subChannelInitializer(initializer);
			b.childHandler(initializer);
			b.group(getEventGroup(bossGroups, channelClass, 1), getEventGroup(workerGroups, channelClass, 0));
			b.attr(ChannelAttributes.GRAPH, this);
			return b.bind(address);
		} else {
			Bootstrap b = new Bootstrap().channel(channelClass).group(getEventGroup(workerGroups, channelClass, 0))
					.handler(new GraphChannelInitializer(vertex));
			b.attr(ChannelAttributes.GRAPH, this);
			return b.bind(address);
		}
	}

	private ChannelInitializer<Channel> subChannelInitializer(final ChannelInitializer<Channel> init) {
        return new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                SubChannelHandler sch = new SubChannelHandler(init);
                String name = ch.pipeline().context(this).name();
                ch.pipeline().addAfter(name, null, new DiscardChannelHandler());
                ch.pipeline().addAfter(name, null, new ReportingChannelHandler());
                ch.pipeline().addAfter(name, null, new LoggingHandler(LogLevel.INFO));
                ch.pipeline().replace(this, null, sch);
            }

        };
	}
	
	private ChannelInitializer<Channel> initializer(final ChannelHandler relay, final ChannelHandler... handlers) {
		return new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
	            ch.attr(ChannelAttributes.SCRIPT_CONTEXT).set(scriptContext);
				String name = ch.pipeline().context(this).name();
				for (ChannelHandler handler : handlers) {
					ch.pipeline().addBefore(name, null, handler);
				}
				ch.pipeline().addAfter(name, null, relay);
			}
			
		};
		
	}

	private ChannelFuture stopServerFromSourceValue(Object serverValue) {
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
	 * @param o            the value Object for the server vertex
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
			if (port > 0 && port < 65536)
				return new InetSocketAddress(address, port);
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
		throw new RuntimeException("Unexpected cell value");
	}

	private ChannelHandler[] getChannelHandlers(Object o)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		List<ChannelHandler> handlers = new ArrayList<ChannelHandler>();
		do {
			if (graph.getModel().isEdge(o))
				o = graph.getModel().getTerminal(o, false);
			Object v = graph.getModel().getValue(o);
			if (isSink(v))
				break;
			ChannelHandler h = getChannelHandler(v);
			if (h instanceof GraphNodeAware)
				((GraphNodeAware) h).setGraphNode(this, o);
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
			if ((h instanceof RelayHandler) || (h instanceof DatagramRelayHandler)
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
		if (o instanceof Element) {
			Element e = (Element) o;
			NodeList parameters = e.getElementsByTagName("Parameter");
			String[] p = new String[parameters.getLength()];
			for (int i = 0; i < parameters.getLength(); i++) {
				Node n = parameters.item(i);
				NodeList children = n.getChildNodes();
				if (children.getLength() == 1) {
					p[i] = children.item(0).getTextContent();
				} else { // find the CDATA node
					for (int j = 0; i < children.getLength(); j++) {
						if (children.item(j) instanceof CDATASection) {
							p[i] = ((CharacterData) children.item(j)).getData();
							break;
						}
					}
				}
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
	synchronized public ChannelInitializer<Channel> getNextHandlers(ChannelHandler handler, String option) {
		Object vertex = handlerVertexMap.remove(handler);
		Object[] outgoing = graph.getOutgoingEdges(vertex);
		try {
			for (Object edge : outgoing) {
				Object v = graph.getModel().getValue(edge);
				if ((option == null && (v == null || "".equals(v))) || option.equals(v)) {
					final ChannelHandler[] handlers = getChannelHandlers(graph.getModel().getTerminal(edge, false));
					return new ChannelInitializer<Channel>() {
						@Override
						protected void initChannel(Channel ch) throws Exception {
							String name = ch.pipeline().context(this).name();
							for (int i=handlers.length-1; i>=0; i--) {
								ch.pipeline().addAfter(name, null, handlers[i]);
							}
						}
					};
				}
			}
			throw new NullPointerException("No match found for " + handler.getClass() + ", option " + option);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	synchronized public ChannelInitializer<Channel> getClientChannelInitializer(ChannelHandler handler) {
		return getClientChannelInitializer(handler, false);
	}

	@Override
	synchronized public ChannelInitializer<Channel> getClientChannelInitializer(ChannelHandler handler, boolean retain) {
		Object vertex = retain ? handlerVertexMap.get(handler) : handlerVertexMap.remove(handler);
		if (vertex == null)
			throw new IllegalStateException(
					"Handler " + handler + " not found in handlerVertexMap: " + handlerVertexMap);
		try {
			Object[] outgoing = graph.getOutgoingEdges(vertex);
			if (outgoing == null || outgoing.length != 1)
				throw new IllegalStateException("Exactly one outgoing edge allowed! Currently "
						+ (outgoing == null ? "null" : outgoing.length));
			ChannelHandler[] handlers = getChannelHandlers(outgoing[0]);
			// Reversing needs to happen because of the difference in 
			// direction of flow in the client vs server
			reverse(handlers);
			// wrap them in a ChannelInitializer
			ChannelInitializer<Channel> init = initializer(handler, handlers);
			return subChannelInitializer(init);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			addGraphException(vertex, e);
			return null;
		}
	}

	private void reverse(ChannelHandler[] handlers) {
	    for (int left = 0, right = handlers.length - 1; left < right; left++, right--) {
	        // swap the values at the left and right indices
	        ChannelHandler temp = handlers[left];
	        handlers[left]  = handlers[right];
	        handlers[right] = temp;
	    }
	}

	@Override
	synchronized public ChannelHandler getProxyHandler(SocketAddress target) {
		if (direct)
			return null;
		else if (socks)
			return new Socks5ProxyHandler(new InetSocketAddress("127.0.0.1", 1081));
		else
			return new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 8080));
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
				stopFuture = stopServerFromSourceValue(previous);
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

	public void addGraphException(final Object node, final Throwable cause) {
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
					graphComponent.setCellWarning(node, null);
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
		    ch.attr(ChannelAttributes.SCRIPT_CONTEXT).set(scriptContext);

			Object[] edges = graph.getEdges(serverVertex);
			if (edges == null || edges.length == 0) {
				addGraphException(serverVertex, new IllegalStateException("No outbound edge"));
				ch.close();
				return;
			} else if (edges.length > 1) {
				addGraphException(serverVertex, new IllegalStateException("Too many outbound edges"));
				ch.close();
				return;
			}

			if (controller != null)
				controller.addChannel(ch.id().asLongText(), ch.localAddress(), ch.remoteAddress());
			ChannelPipeline p = ch.pipeline();
			String me = p.context(this).name();
			p.addAfter(me, null, new ExceptionCatcher(Graph.this, serverVertex));

			Object serverEdge = edges[0];
			ChannelHandler[] handlers = getChannelHandlers(serverEdge);
			ch.attr(ChannelAttributes.GRAPH).set(Graph.this);
			p.addLast(handlers);
		}
	}

	private class ReportingChannelHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			controller.processChannelEvent(ChannelEvent.newChannelReadEvent(ctx, msg));
			ctx.close();
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
			controller.processChannelEvent(ChannelEvent.newUserEventTriggeredEvent(ctx, evt));
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			controller.processChannelEvent(ChannelEvent.newExceptionCaughtEvent(ctx, cause));
			ctx.close();
		}
	}

	private class DiscardChannelHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		}
	}

	private class AddServerChannelListener implements ChannelFutureListener {
		private Object node;

		AddServerChannelListener(Object node) {
			this.node = node;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
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
				ChannelFuture cf = startServerFromSourceVertex(vertex);
				if (cf != null)
					cf.addListener(new AddServerChannelListener(vertex));
			} catch (Exception e) {
				addGraphException(vertex, e);
			}
		}

	}

}
