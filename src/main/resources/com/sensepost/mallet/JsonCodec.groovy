import com.fasterxml.jackson.databind.*;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.*;

return new ByteToMessageCodec<JsonNode>(JsonNode.class) {
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected void encode(ChannelHandlerContext ctx, JsonNode msg, ByteBuf out) throws Exception {
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);
        objectMapper.writeValue(byteBufOutputStream, msg);
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<JsonNode> out) throws Exception {
        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(buf);
        out.add(objectMapper.readTree(byteBufInputStream));
    }

};
