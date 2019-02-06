package com.sensepost.mallet.graph;

import java.net.SocketAddress;

import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.channel.ProxyChannel;
import com.sensepost.mallet.channel.ProxyChannelInitializer;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

class ReportingProxyChannelInitializer extends ProxyChannelInitializer {

	private InterceptController controller;
	
	public ReportingProxyChannelInitializer(InterceptController controller, ChannelHandler... handlers) {
		super(handlers);
		this.controller = controller;
	}
	
	@Override
	protected ProxyChannel newProxyChannel(Channel ch) {
		return ch instanceof DuplexChannel ? new DuplexReportingProxyChannel((DuplexChannel)ch) : new ReportingProxyChannel(ch);
	}

	private class ReportingProxyChannel extends ProxyChannel {

		public ReportingProxyChannel(Channel ch) {
			super(ch);
		}

		@Override
		protected void handleInboundMessage(Object msg) {
			controller.addChannelEvent(new InterceptController.ChannelReadEvent(new FakeContext(this), msg));
			super.handleInboundMessage(msg);
			controller.addChannelEvent(new InterceptController.ExceptionCaughtEvent(new FakeContext(this), new RuntimeException("Unhandled message reached the end of the pipeline, closing!")));
			close();
		}

		@Override
		protected void handleInboundException(Throwable cause) {
			controller.addChannelEvent(new InterceptController.ExceptionCaughtEvent(new FakeContext(this), new RuntimeException("Unhandled Throwable reached the end of the pipeline, closing!", cause)));
			super.handleInboundException(cause);
			close();
		}

		@Override
		protected void handleInboundUserEvent(Object evt) {
			controller.addChannelEvent(new InterceptController.UserEventTriggeredEvent(new FakeContext(this), evt));
			super.handleInboundUserEvent(evt);
		}
		
	}

	private class DuplexReportingProxyChannel extends ProxyChannel.DuplexProxyChannel {

		public DuplexReportingProxyChannel(DuplexChannel ch) {
			super(ch);
		}

		@Override
		protected void handleInboundMessage(Object msg) {
			controller.addChannelEvent(new InterceptController.ChannelReadEvent(new FakeContext(this), msg));
			super.handleInboundMessage(msg);
			controller.addChannelEvent(new InterceptController.ExceptionCaughtEvent(new FakeContext(this), new RuntimeException("Unhandled message reached the end of the pipeline, closing!")));
			close();
		}

		@Override
		protected void handleInboundException(Throwable cause) {
			controller.addChannelEvent(new InterceptController.ExceptionCaughtEvent(new FakeContext(this), new RuntimeException("Unhandled Throwable reached the end of the pipeline, closing!", cause)));
			super.handleInboundException(cause);
			close();
		}

		@Override
		protected void handleInboundUserEvent(Object evt) {
			controller.addChannelEvent(new InterceptController.UserEventTriggeredEvent(new FakeContext(this), evt));
			super.handleInboundUserEvent(evt);
		}
		
	}

	private class FakeContext implements ChannelHandlerContext {

		private Channel channel;

		public FakeContext(Channel channel) {
			this.channel = channel;
		}

		@Override
		public ChannelFuture bind(SocketAddress localAddress) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture connect(SocketAddress remoteAddress) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture disconnect() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture close() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture deregister() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
				ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture disconnect(ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture close(ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture deregister(ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture write(Object msg) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture write(Object msg, ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture writeAndFlush(Object msg) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelPromise newPromise() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelProgressivePromise newProgressivePromise() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture newSucceededFuture() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelFuture newFailedFuture(Throwable cause) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelPromise voidPromise() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Channel channel() {
			return channel;
		}

		@Override
		public EventExecutor executor() {
			return channel.eventLoop();
		}

		@Override
		public String name() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandler handler() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isRemoved() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireChannelRegistered() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireChannelUnregistered() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireChannelActive() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireChannelInactive() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
			// do nothing
			return this;
		}

		@Override
		public ChannelHandlerContext fireUserEventTriggered(Object evt) {
			// do nothing
			return this;
		}

		@Override
		public ChannelHandlerContext fireChannelRead(Object msg) {
			// do nothing
			return this;
		}

		@Override
		public ChannelHandlerContext fireChannelReadComplete() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext fireChannelWritabilityChanged() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext read() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelHandlerContext flush() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ChannelPipeline pipeline() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ByteBufAllocator alloc() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Attribute<T> attr(AttributeKey<T> key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> boolean hasAttr(AttributeKey<T> key) {
			throw new UnsupportedOperationException();
		}
		
	};

}