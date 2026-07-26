package com.zxylearn.build_guard_server.config;

import com.zxylearn.build_guard_server.service.DeviceMessageListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Netty TCP 服务端 — 接收 sim 或真实设备通过 TCP 长连接推送的 JSON 设备消息。
 *
 * 协议：换行分隔的 JSON（每行一个完整的 JSON 消息）。
 * 与 HTTP 轮询不同，TCP 是长连接，设备主动推送，延迟更低。
 */
@Component
public class NettyTcpServer {

    private static final Logger log = LoggerFactory.getLogger(NettyTcpServer.class);

    private final DeviceMessageListener deviceMessageListener;
    private final int port;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyTcpServer(DeviceMessageListener deviceMessageListener,
                          @Value("${buildguard.tcp.port:19090}") int port) {
        this.deviceMessageListener = deviceMessageListener;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 换行符分隔的帧解码器（最大 64KB）
                                    .addLast(new DelimiterBasedFrameDecoder(65536, Delimiters.lineDelimiter()))
                                    .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                    .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                    .addLast(new DeviceMessageHandler(deviceMessageListener));
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            serverChannel = bootstrap.bind().sync().channel();
            log.info("Netty TCP 设备网关已启动 port={}", port);
        } catch (Exception e) {
            log.error("Netty TCP 启动失败: {}", e.getMessage());
        }
    }

    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    /**
     * 设备消息处理器：每收到一行 JSON → 解析 → 交给 DeviceMessageListener
     */
    private static class DeviceMessageHandler extends SimpleChannelInboundHandler<String> {

        private final DeviceMessageListener listener;
        private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

        DeviceMessageHandler(DeviceMessageListener listener) {
            this.listener = listener;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            String trimmed = msg.trim();
            if (trimmed.isEmpty()) return;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = objectMapper.readValue(trimmed, Map.class);
                listener.handleDeviceMessage(message, "sim.tcp.source");
            } catch (Exception e) {
                log.debug("TCP 消息解析失败: {} — {}", e.getMessage(), trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("TCP 设备断开: {}", ctx.channel().remoteAddress());
        }
    }
}
