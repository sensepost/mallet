package com.sensepost.mallet.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.pcap.PcapWriteHandler;

public class PcapWriterInitializer extends ChannelInitializer<Channel> {

    private AtomicInteger users = new AtomicInteger(0);
    private final File pcapFile;
    private final OutputStream outputStream;
    private final PcapWriteHandler.Builder builder;
    private final ChannelFutureListener closeListener = new CloseListener();

    private class CloseListener implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            int i = users.decrementAndGet();
            if (i == 0) {
                try {
                    outputStream.close();
                } catch (IOException ioe) {
                }
                deleteIfEmpty();
            }
        }

    }

    private final ChannelFutureListener bindListener = new BindListener();
    
    private class BindListener implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                future.channel().closeFuture().addListener(closeListener);
                int i = users.incrementAndGet();
            } else {
                try {
                    outputStream.close();
                } catch (IOException ioe) {
                }
                deleteIfEmpty();
            }
        }

    }
    
    private final long size;

    public PcapWriterInitializer(File pcapFile) throws IOException {
        this.pcapFile = pcapFile;
        outputStream = new FileOutputStream(pcapFile);
        PcapWriteHandler.writeGlobalHeader(outputStream);
        outputStream.flush();
        size = pcapFile.length();
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
        int i = users.incrementAndGet();
        ch.closeFuture().addListener(closeListener);
    }

    private void deleteIfEmpty() {
        long size = pcapFile.length();
        if (size == this.size) {
            pcapFile.delete();
        }
    }
}
