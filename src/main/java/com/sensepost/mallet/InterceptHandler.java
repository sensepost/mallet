package com.sensepost.mallet;

import java.net.SocketAddress;

import com.sensepost.mallet.model.ChannelEvent;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;

@Sharable
public class InterceptHandler extends ChannelDuplexHandler {

    private InterceptController controller;

    private ExceptionListener exceptionListener = new ExceptionListener();
    
    private EventExecutorGroup executor = new DefaultEventLoopGroup();

    public InterceptHandler(InterceptController controller) {
        if (controller == null)
            throw new NullPointerException("controller");
        this.controller = controller;
    }

    private void submitEvent(final ChannelEvent evt) throws Exception {
        controller.processChannelEvent(evt);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newBindEvent(ctx, localAddress, promise));
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newConnectEvent(ctx, remoteAddress, localAddress, promise));
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newDisconnectEvent(ctx, promise));
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newCloseEvent(ctx, promise));
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newDeregisterEvent(ctx, promise));
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newReadEvent(ctx));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        promise.addListener(exceptionListener);
        submitEvent(ChannelEvent.newWriteEvent(ctx, promise, msg));
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newFlushEvent(ctx));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelRegisteredEvent(ctx));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelUnregisteredEvent(ctx));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelActiveEvent(ctx));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelInactiveEvent(ctx));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        submitEvent(ChannelEvent.newChannelReadEvent(ctx, msg));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelReadCompleteEvent(ctx));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        submitEvent(ChannelEvent.newUserEventTriggeredEvent(ctx, evt));
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        submitEvent(ChannelEvent.newChannelWritabilityChangedEvent(ctx));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        submitEvent(ChannelEvent.newExceptionCaughtEvent(ctx, cause));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
    }

    private class ExceptionListener implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.cause() != null) {
                String id = future.channel().id().asLongText();
                submitEvent(ChannelEvent.newExceptionCaughtEvent(id, future.cause()));
            }
        }

    }
}
