from io.netty.channel import ChannelDuplexHandler, ChannelHandler
from io.netty.channel import ChannelHandlerContext
from io.netty.channel import ChannelPromise

class JythonHandler(ChannelDuplexHandler, ChannelHandler):
    def __init__(self):
        print "Init"
        pass

    def channelRead(self, ctx, msg):
        ctx.fireUserEventTriggered("Hello from Jython")
        ctx.fireChannelRead(msg)

_=JythonHandler()

