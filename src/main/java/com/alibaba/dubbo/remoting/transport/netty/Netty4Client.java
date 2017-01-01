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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;

/**
 * NettyClient.
 * 
 * @author qian.lei
 * @author william.liangf
 */
public class Netty4Client extends AbstractClient {
    
    private static final Logger logger = LoggerFactory.getLogger(Netty4Client.class);

    // 因ChannelFactory的关闭有DirectMemory泄露，采用静态化规避
    // https://issues.jboss.org/browse/NETTY-424

    private EventLoopGroup workerGroup;

    private volatile Channel channel; // volatile, please copy reference to use

    private Bootstrap bootstrap;
    
    public Netty4Client(final URL url, final ChannelHandler handler) throws RemotingException {

        super(url, wrapChannelHandler(url, handler));
    }
    
    @Override
    protected void doOpen() throws Throwable {
        bootstrap = new Bootstrap();
        workerGroup = new NioEventLoopGroup();
        // config
        final Netty4Handler nettyHandler = new Netty4Handler(getUrl(), this);
        // @see org.jboss.netty.channel.socket.SocketChannelConfig
        bootstrap.group(workerGroup).option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY,true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,getConnectTimeout())
                .channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                Netty4CodecAdapter adapter = new Netty4CodecAdapter(getCodec(), getUrl(), Netty4Client.this);
                ch.pipeline().addLast(adapter.getDecoder());
                ch.pipeline().addLast(adapter.getEncoder());
                ch.pipeline().addLast(nettyHandler);
            }
        })

        ;


    }

    protected void doConnect() throws Throwable {
        long start = System.currentTimeMillis();
        ChannelFuture future = bootstrap.connect(getConnectAddress());
        try{
            boolean ret = future.awaitUninterruptibly(getConnectTimeout(), TimeUnit.MILLISECONDS);

            if (ret && future.isSuccess()) {
                Channel newChannel = future.channel();
                try {
                    // 关闭旧的连接
                    Channel oldChannel = Netty4Client.this.channel; // copy reference
                    if (oldChannel != null) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close old netty channel " + oldChannel + " on create new netty channel " + newChannel);
                            }
                            oldChannel.close();
                        } finally {
                            Netty4Channel.removeChannelIfDisconnected(oldChannel);
                        }
                    }
                } finally {
                    if (Netty4Client.this.isClosed()) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close new netty channel " + newChannel + ", because the client closed.");
                            }
                            newChannel.close();
                        } finally {
                            Netty4Client.this.channel = null;
                            Netty4Channel.removeChannelIfDisconnected(newChannel);
                        }
                    } else {
                        Netty4Client.this.channel = newChannel;
                    }
                }
            } else if (future.cause() != null) {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + ", error message is:" + future.cause().getMessage(), future.cause());
            } else {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + " client-side timeout "
                        + getConnectTimeout() + "ms (elapsed: " + (System.currentTimeMillis() - start) + "ms) from netty client "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
            }
        }finally{
            if (! isConnected()) {
                future.cancel(true);
            }
        }
    }

    @Override
    protected void doDisConnect() throws Throwable {
        try {
            Netty4Channel.removeChannelIfDisconnected(channel);
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }
    }
    
    @Override
    protected void doClose() throws Throwable {
        /*try {
            bootstrap.releaseExternalResources();
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }*/
    }

    @Override
    protected com.alibaba.dubbo.remoting.Channel getChannel() {
        Channel c = channel;
        if (c == null || ! c.isActive())
            return null;
        return Netty4Channel.getOrAddChannel(c, getUrl(), this);
    }

}