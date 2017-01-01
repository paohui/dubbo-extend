/*
 * Copyright 1999-2011 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.transport.netty;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.io.UnsafeByteArrayInputStream;
import com.alibaba.dubbo.common.io.UnsafeByteArrayOutputStream;
import com.alibaba.dubbo.remoting.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * NettyCodecAdapter.
 * 
 * @author qian.lei
 */
final class Netty4CodecAdapter {

    private final ChannelHandler encoder = new InternalEncoder();
    
    private final ChannelHandler decoder = new InternalDecoder();

    private final Codec codec;
    
    private final URL url;
    
    private final int            bufferSize;
    
    private final com.alibaba.dubbo.remoting.ChannelHandler handler;

    public Netty4CodecAdapter(Codec codec, URL url, com.alibaba.dubbo.remoting.ChannelHandler handler) {
        this.codec = codec;
        this.url = url;
        this.handler = handler;
        int b = url.getPositiveParameter(Constants.BUFFER_KEY, Constants.DEFAULT_BUFFER_SIZE);
        this.bufferSize = b >= Constants.MIN_BUFFER_SIZE && b <= Constants.MAX_BUFFER_SIZE ? b : Constants.DEFAULT_BUFFER_SIZE;
    }

    public ChannelHandler getEncoder() {
        return encoder;
    }

    public ChannelHandler getDecoder() {
        return decoder;
    }

    private class InternalEncoder extends MessageToByteEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(1024); // 不需要关闭
            Netty4Channel channel = Netty4Channel.getOrAddChannel(ctx.channel(), url, handler);
            try {
            	codec.encode(channel, os, msg);
            } finally {
                Netty4Channel.removeChannelIfDisconnected(ctx.channel());
            }
            out.writeBytes(os.toByteArray());
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            int readable = in.readableBytes();
            if (readable <= 0) {
                return;
            }
            byte[] bytes = new byte[readable];
            in.readBytes(bytes);

            Netty4Channel channel = Netty4Channel.getOrAddChannel(ctx.channel(), url, handler);
            try {
                UnsafeByteArrayInputStream bis = new UnsafeByteArrayInputStream(bytes);
                Object msg = codec.decode(channel, bis);
                if (msg == Codec.NEED_MORE_INPUT) {
                    return;
                }
                in.readerIndex(bis.position());
                out.add(msg);
            }finally {
                Netty4Channel.removeChannelIfDisconnected(ctx.channel());
            }
        }
    }
}