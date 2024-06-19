from io.netty.channel import ChannelDuplexHandler, ChannelHandler
from io.netty.channel import ChannelHandlerContext
from io.netty.channel import ChannelPromise

class JythonHandler(ChannelDuplexHandler, ChannelHandler):
    def __init__(self):
        pass

    def channelRead(self, ctx, msg):
        ctx.fireUserEventTriggered("Read from Jython")
        ctx.fireChannelRead(msg)

    def write(self, ctx, msg, promise):
        ctx.fireUserEventTriggered("Write from Jython")
        ctx.write(msg, promise)

_ = JythonHandler()

