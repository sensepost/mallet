package com.sensepost.mallet.ognl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class OgnlHandler extends ChannelDuplexHandler {

    private OgnlExpression readExpression, writeExpression;

    public OgnlHandler(String readExpression, String writeExpression) {
        this.readExpression = readExpression == null ? null : new OgnlExpression(readExpression);
        this.writeExpression = writeExpression == null ? null : new OgnlExpression(writeExpression);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (readExpression != null) {
            msg = readExpression.getValue(msg);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (writeExpression != null) {
            msg = writeExpression.getValue(msg);
        }
        super.write(ctx, msg, promise);
    }

}
