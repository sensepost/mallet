var ChannelDuplexHandler = Java.type("io.netty.channel.ChannelDuplexHandler");
var MyChannelDuplexHandler = Java.extend(ChannelDuplexHandler, {
    channelRead: function(ctx, msg) {
        ctx.fireUserEventTriggered("Read from Javascript");
        ctx.fireChannelRead(msg);
    },
    write: function(ctx, msg, promise) {
        ctx.fireUserEventTriggered("Write from Javascript");
        ctx.write(msg, promise);
    }
});
var _ = new MyChannelDuplexHandler();
