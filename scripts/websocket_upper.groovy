import io.netty.channel.*;

import io.netty.handler.codec.http.websocketx.*;

return new ChannelDuplexHandler() {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
        throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) msg).text();
            msg = new TextWebSocketFrame(text.toUpperCase());
        }
        super.channelRead(ctx, msg);
    }

};

