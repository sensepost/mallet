package com.sensepost.mallet.handlers.messagepack;

import io.netty.channel.CombinedChannelDuplexHandler;

public class MessagePackCodec extends CombinedChannelDuplexHandler<MessagePackDecoder, MessagePackEncoder> {

    public MessagePackCodec() {
        init(new MessagePackDecoder(), new MessagePackEncoder());
    }
    
}
