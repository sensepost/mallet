package com.sensepost.mallet.handlers.http;

import java.net.InetSocketAddress;

import com.sensepost.mallet.ConnectRequest;
import com.sensepost.mallet.ConnectTargetHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class HttpConnectInitializer extends ChannelInitializer<Channel> {

    private HttpServerCodec http;
    private HttpConnectHandler connect;
    private ConnectTargetHandler target;

    static final HttpResponse OK = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    static final HttpResponse BAD = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
    static final HttpResponse NOT_ALLOWED = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.METHOD_NOT_ALLOWED);

    @Override
    protected void initChannel(Channel ch) throws Exception {
        http = new HttpServerCodec();
        connect = new HttpConnectHandler();
        target = new ConnectTargetHandler();
        ChannelPipeline p = ch.pipeline();
        String me = p.context(this).name();
        p.addAfter(me, null, target);
        p.addAfter(me, null, connect);
        p.addAfter(me, null, http);
    }

    private class HttpConnectHandler extends SimpleChannelInboundHandler<HttpObject> {

        private HttpRequest msg;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject obj) throws Exception {
            if (obj instanceof HttpRequest) {
                msg = (HttpRequest) obj;
            } else if (obj instanceof LastHttpContent) {
                if (!msg.method().equals(HttpMethod.CONNECT)) {
                    ctx.writeAndFlush(NOT_ALLOWED).addListener(ChannelFutureListener.CLOSE);
                } else {
                    try {
                        String uri = msg.uri();
                        int colon = uri.indexOf(':');
                        String host = uri.substring(0, colon);
                        int port = Integer.parseInt(uri.substring(colon + 1));
                        if (port < 1 || port > 65535)
                            throw new NumberFormatException();
                        final Promise<Channel> connectPromise = ctx.executor().newPromise();
                        connectPromise.addListener(new HttpConnectionResponseSender());
                        InetSocketAddress target = InetSocketAddress.createUnresolved(host, port);
                        ConnectRequest cr = new ConnectRequest(target, connectPromise);
                        ctx.fireUserEventTriggered(cr);
                    } catch (Exception e) {
                        ctx.writeAndFlush(BAD).addListener(ChannelFutureListener.CLOSE);
                        return;
                    } finally {
                        ReferenceCountUtil.release(msg);
                        msg = null;
                    }
                }
            }
        }

        private class HttpConnectionResponseSender implements FutureListener<Channel> {
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                Channel ch = future.get();
                if (future.isSuccess()) {
                    ch.writeAndFlush(OK).addListener(new CodecRemover());
                } else {
                    ch.writeAndFlush(BAD).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

        private class CodecRemover implements ChannelFutureListener {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel ch = future.channel();
                if (future.isSuccess()) {
                    ch.pipeline().remove(target);
                    ch.pipeline().remove(connect);
                    ch.pipeline().remove(http);
                } else {
                    ch.close();
                }
            }
        }

    }
}
