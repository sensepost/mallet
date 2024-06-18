import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;

class StringCodec extends CombinedChannelDuplexHandler<StringDecoder, StringEncoder> {
    public StringCodec(Charset c) {
		super(new StringDecoder(c), new StringEncoder(c));
    }
}

return new StringCodec(CharsetUtil.UTF_8);
