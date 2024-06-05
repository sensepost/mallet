package com.sensepost.mallet.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import com.sensepost.mallet.ChannelAttributes;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.pcap.PcapWriteHandler;

public class PcapWriterInitializer extends ChannelInitializer<Channel> {

    private AtomicInteger users = new AtomicInteger(0);
    private final OutputStream outputStream;
    private final PcapWriteHandler.Builder builder;
    private final ChannelFutureListener closeListener = new ChannelFutureListener() {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (users.decrementAndGet() == 0) {
                System.err.println("All PcapWriterHandlers are closed, closing the file.");
                try {
                    outputStream.close();
                } catch (IOException ioe) {
                    
                }
            }
        }
        
    };
    
    private final ChannelFutureListener bindListener = new ChannelFutureListener() {
        
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                users.incrementAndGet();
            } else {
                System.err.println("Server bind failed, closing pcap");
                try {
                    outputStream.close();
                } catch (IOException ioe) {
                    
                }
            }
        }
        
    };

    public PcapWriterInitializer(OutputStream outputStream) {
        this.outputStream = outputStream;
        builder = PcapWriteHandler.builder().sharedOutputStream(true);
    }
    
    public ChannelFutureListener bindListener() {
        return bindListener;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        String name = p.context(this).name();
        p.addAfter(name, null, builder.build(outputStream));
        users.incrementAndGet();
        ch.attr(ChannelAttributes.PCAP_SSL_INITIALIZER).set(this);
        ch.closeFuture().addListener(closeListener);
    }

}
