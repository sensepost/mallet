import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ByteToMessageDecoder;

import com.sensepost.mallet.ScriptHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

class MessagePackDecoder extends ByteToMessageDecoder {
    
    private ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >  0) {
            InputStream is = new ByteBufInputStream(in);
            while (in.readableBytes() > 0) {
                // The cast below is loadbearing, even though it should not be. Otherwise groovy 
                // invokes "readValue(DataInput, Class<T>)" which is not implemented, 
                // instead of "readValue(InputStream, Class<T>)" which is. (╯°□°）╯︵ ┻━┻
                out.add(objectMapper.readValue((InputStream) is, Object.class));
            }
        }
    }
}

class MessagePackEncoder extends MessageToByteEncoder<Object> {

    private ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        out.writeBytes(objectMapper.writeValueAsBytes(msg));
    }
    
}

class MessagePackCodec extends CombinedChannelDuplexHandler<MessagePackDecoder, MessagePackEncoder> {

    public MessagePackCodec() {
        init(new MessagePackDecoder(), new MessagePackEncoder());
    }

}

return new ChannelDuplexHandler() {
    private int state = 0;
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }
        ctx.fireChannelRead(msg);
        if (msg instanceof TextWebSocketFrame && state == 0) {
            state = 1;
        } else if (msg instanceof BinaryWebSocketFrame && state == 1) {
            state = 2;
            String name = ctx.name();
            ctx.pipeline().addAfter(name, null, new MessagePackCodec());
            ctx.pipeline().addAfter(name, null, new ProtobufVarint32FrameDecoder());
            ctx.pipeline().addAfter(name, null, new ProtobufVarint32LengthFieldPrepender());
            ctx.pipeline().addAfter(name, null, new ScriptHandler("./scripts/BinaryWebSocketFrameCodec.groovy"));
            ctx.fireUserEventTriggered("Added Blazorpack handlers");
        }
    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise);
        if (msg instanceof TextWebSocketFrame && state == 0) {
            state = 1;
        } else if (msg instanceof BinaryWebSocketFrame && state == 1) {
            state = 2;
            String name = ctx.name();
            ctx.pipeline().addAfter(name, null, new MessagePackCodec());
            ctx.pipeline().addAfter(name, null, new ProtobufVarint32FrameDecoder());
            ctx.pipeline().addAfter(name, null, new ProtobufVarint32LengthFieldPrepender());
            ctx.pipeline().addAfter(name, null, new ScriptHandler("./scripts/BinaryWebSocketFrameCodec.groovy"));
            ctx.fireUserEventTriggered("Added Blazorpack handlers");
        }
    }
};
