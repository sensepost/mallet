package com.sensepost.mallet.graph;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxCellOverlay;
import com.sensepost.mallet.ChannelAttributes;

public class ExceptionCatcher extends ChannelDuplexHandler {
	
	private Object node;
	private mxGraphComponent graphComponent;
	
	public ExceptionCatcher(mxGraphComponent graphComponent, Object node) {
		this.graphComponent = graphComponent;
		this.node = node;
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		addCause(ctx, cause);
		super.exceptionCaught(ctx, cause);
	}

	private void addCause(ChannelHandlerContext ctx, final Throwable cause) {
		Attribute<Throwable> attr = ctx.channel().attr(ChannelAttributes.CAUSE);
		Throwable prev = attr.get();
		if (cause != prev) {
			attr.set(cause);
			if (cause instanceof ClosedChannelException)
				return;
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
	}
	
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		if (evt != null) {
			try {
				Attribute<Object> attr = ctx.channel().attr(ChannelAttributes.CAUSE_EVENT);
				Object prev = attr.get();
				if (prev != evt) {
					attr.set(evt);
					Throwable cause = findThrowable(evt);
					if (cause != null && !(cause instanceof ClosedChannelException))
						addCause(ctx, cause);
				}
			} catch (Exception e) {}
		}
		super.userEventTriggered(ctx, evt);
	}

	private Throwable findThrowable(Object evt) {
		try {
			Class<?> c = evt.getClass();
			Method[] methods = c.getMethods();
			for (Method m : methods) {
				if (Throwable.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
					Throwable cause = (Throwable) m.invoke(evt);
					if (cause != null)
						return cause;
				}
			}
		} catch (Exception e) {}
		return null;
	}

	@Override
	public void connect(ChannelHandlerContext ctx,
			SocketAddress remoteAddress, SocketAddress localAddress,
			ChannelPromise promise) throws Exception {
		// DefaultChannelPromise
		super.connect(ctx, remoteAddress, localAddress, promise);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		super.write(ctx, msg, promise);
	}

}