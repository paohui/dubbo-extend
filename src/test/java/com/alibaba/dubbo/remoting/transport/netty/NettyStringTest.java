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
import com.alibaba.dubbo.remoting.exchange.ExchangeChannel;
import com.alibaba.dubbo.remoting.exchange.ExchangeServer;
import com.alibaba.dubbo.remoting.exchange.Exchangers;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import junit.framework.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * User: heyman
 * Date: 4/26/11
 * Time: 4:13 PM
 */
public class NettyStringTest {
    static ExchangeServer server;
    static ExchangeChannel client;

    @BeforeClass
    public static void setUp() throws Exception {
        //int port = (int) (1000 * Math.random() + 10000);
        int port = 10004;
        System.out.println(port);
        server = Exchangers.bind(URL.valueOf("telnet://0.0.0.0:" + port + "?server=netty4"), new TelnetServerHandler());
        client = Exchangers.connect(URL.valueOf("telnet://127.0.0.1:" + port + "?client=netty4"), new TelnetClientHandler());
    }

    @Test
    public void testHandler() throws Exception {
        //Thread.sleep(20000);
        client.request("world\r\n");
        ResponseFuture world = client.request("world",10000);
        String result = (String)world.get();
        Assert.assertEquals("Did you say 'world'?\r\n",result);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        //Thread.sleep(2000000);
        try {
            if (server != null)
                server.close();
        } finally {
            if (client != null)
                client.close();
        }
    }
}