package com.alibaba.dubbo.remoting;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.exchange.ExchangeServer;
import com.alibaba.dubbo.remoting.exchange.Exchangers;
import com.alibaba.dubbo.remoting.transport.netty.TelnetServerHandler;

/**
 * Created by paohui01 on 2016/12/26.
 */
public class TestSystemArray {

    public static void  main(String []ars) throws RemotingException, InterruptedException {
        ExchangeServer bind = Exchangers.bind(URL.valueOf("telnet://0.0.0.0:" + 10001 + "?server=netty4"), new TelnetServerHandler());
        Thread.sleep(1000000);
    }
}
