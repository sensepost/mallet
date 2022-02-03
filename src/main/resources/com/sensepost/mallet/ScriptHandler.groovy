import io.netty.channel.*;

return new ChannelDuplexHandler() {
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // do something with inbound msg
        ctx.fireChannelRead(msg);
    }
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // do something with outbound msg
        ctx.write(msg, promise);
    }
};
