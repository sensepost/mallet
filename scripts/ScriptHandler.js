var ChannelDuplexHandler = Java.type("io.netty.channel.ChannelDuplexHandler");
var MyChannelDuplexHandler = Java.extend(ChannelDuplexHandler, {
    channelRead: function(ctx, msg) {
        ctx.fireUserEventTriggered("Hello from Javascript");
        ctx.fireChannelRead(msg);
    }
});
var _ = new MyChannelDuplexHandler();
