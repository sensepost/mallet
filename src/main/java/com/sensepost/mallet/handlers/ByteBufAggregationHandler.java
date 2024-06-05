package com.sensepost.mallet.handlers;

import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ObjectUtil;

public class ByteBufAggregationHandler extends ChannelDuplexHandler {

    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long MAX_BUFFER_SIZE = 64 * 1024 * 1024; // 64kB

    private long readerIdleTimeNanos;
    private long lastReadTime = 0;
    private ByteBuf buffer = null;
    private Future<?> timeoutFuture = null;

    private byte state;
    private static final byte ST_INITIALIZED = 1;
    private static final byte ST_DESTROYED = 2;

    private boolean reading = false;
    private boolean readComplete = false;

    public ByteBufAggregationHandler() {
        this(100, TimeUnit.MILLISECONDS);
    }

    public ByteBufAggregationHandler(long timeout, TimeUnit unit) {
        ObjectUtil.checkNotNull(unit, "unit");
        readerIdleTimeNanos = Math.max(unit.toNanos(timeout), MIN_TIMEOUT_NANOS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            // channelActive() event has been fired already, which means
            // this.channelActive() will not be invoked. We have to initialize here instead.
            initialize(ctx);
        } else {
            // channelActive() event has not been fired yet. this.channelActive() will be
            // invoked and initialization will occur there.
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        // Initialize early if channel is active already.
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // This method will be invoked only if this handler was added
        // before channelActive() event is fired. If a user adds this handler
        // after the channelActive() event, initialize() will be called by beforeAdd().
        initialize(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            lastReadTime = ticksInNanos();
            reading = true;
            readComplete = false;
            ByteBuf buf = (ByteBuf) msg;
            if (buffer == null) {
                buffer = buf;
            } else {
                buffer.writeBytes(buf);
                buf.release();
                if (buffer.readableBytes() > MAX_BUFFER_SIZE) {
                    ctx.fireChannelRead(buffer);
                    buffer = null;
                }
            }
        } else {
            reading = false;
            readComplete = false;
            if (buffer != null) {
                ctx.fireChannelRead(buffer);
                buffer = null;
            }
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if ((readerIdleTimeNanos > 0) && reading) {
            lastReadTime = ticksInNanos();
            reading = false;
            readComplete = true;
            ctx.read();
        } else
            ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            if (buffer != null) {
                ctx.fireChannelRead(buffer);
                buffer = null;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (buffer != null) {
            ctx.fireChannelRead(buffer);
            buffer = null;
        }
        super.close(ctx, promise);
    }

    private void initialize(ChannelHandlerContext ctx) {
        // Avoid the case where destroy() is called before scheduling timeouts.
        // See: https://github.com/netty/netty/issues/143
        switch (state) {
        case 1:
        case 2:
            return;
        default:
            break;
        }

        state = ST_INITIALIZED;

        lastReadTime = ticksInNanos();
        timeoutFuture = schedule(ctx, new TimeoutTask(ctx), readerIdleTimeNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * This method is visible for testing!
     */
    long ticksInNanos() {
        return System.nanoTime();
    }

    /**
     * This method is visible for testing!
     */
    Future<?> schedule(ChannelHandlerContext ctx, Runnable task, long delay, TimeUnit unit) {
        return ctx.executor().schedule(task, delay, unit);
    }

    private void destroy() {
        state = ST_DESTROYED;

        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private final class TimeoutTask implements Runnable {

        private final ChannelHandlerContext ctx;

        TimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            run(ctx);
        }

        private void run(ChannelHandlerContext ctx) {
            long nextDelay = readerIdleTimeNanos;
            if (!reading) {
                nextDelay -= ticksInNanos() - lastReadTime;
            }

            if (nextDelay <= 0) {
                // Reader is idle - set a new timeout and notify the callback.
                timeoutFuture = schedule(ctx, this, readerIdleTimeNanos, TimeUnit.NANOSECONDS);

                if (buffer != null) {
                    try {
                        ctx.fireChannelRead(buffer);
                    } catch (Throwable t) {
                        ctx.fireExceptionCaught(t);
                    }
                    buffer = null;
                }
                if (readComplete) {
                    try {
                        ctx.fireChannelReadComplete();
                    } catch (Throwable t) {
                        ctx.fireExceptionCaught(t);
                    }
                    readComplete = false;
                }
            } else {
                // Read occurred before the timeout - set a new timeout with shorter delay.
                timeoutFuture = schedule(ctx, this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }

}
