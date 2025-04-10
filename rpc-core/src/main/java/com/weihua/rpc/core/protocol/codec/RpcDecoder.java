package com.weihua.rpc.core.protocol.codec;

import com.weihua.rpc.common.exception.SerializeException;
import com.weihua.rpc.common.model.RpcRequest;
import com.weihua.rpc.common.model.RpcResponse;
import com.weihua.rpc.core.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * RPC解码器
 * 将网络中的二进制数据解码为Java对象
 */
@Slf4j
public class RpcDecoder extends ByteToMessageDecoder {

    private final Serializer serializer;

    // 协议魔数，用于快速识别协议包
    private static final byte[] MAGIC_NUMBER = { (byte) 0xAB, (byte) 0xBA };

    // 消息类型常量定义
    private static final byte MSG_TYPE_REQUEST = 1;
    private static final byte MSG_TYPE_RESPONSE = 2;
    private static final byte MSG_TYPE_HEARTBEAT = 3;

    // 协议头长度 = 魔数(2) + 版本(1) + 序列化类型(1) + 数据长度(4) = 8字节
    private static final int HEADER_LENGTH = 8;

    public RpcDecoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 检查可读字节是否足够包含协议头
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }

        // 记录当前读索引，用于复位
        in.markReaderIndex();

        // 2. 验证魔数
        byte[] magic = new byte[2];
        in.readBytes(magic);
        if (!Arrays.equals(magic, MAGIC_NUMBER)) {
            // 检查是否是健康检查连接
            if (isEmptyConnection(magic)) {
                log.debug("检测到可能是健康检查连接 [{}], 来自: {}",
                        Arrays.toString(magic), ctx.channel().remoteAddress());

                // 跳过剩余数据
                in.skipBytes(in.readableBytes());
                return; // 保持连接开放
            }

            // 非法连接或数据，记录日志
            log.warn("接收到无效魔数: {}, 可能是非法连接, 来自: {}",
                    Arrays.toString(magic), ctx.channel().remoteAddress());

            // 跳过剩余数据
            in.skipBytes(in.readableBytes());
            return;
        }

        // 3. 读取版本号
        byte version = in.readByte();

        // 4. 读取序列化类型
        byte serializerType = in.readByte();

        // 检查是否是心跳消息
        if (serializerType == MSG_TYPE_HEARTBEAT) {
            log.debug("收到心跳消息, channel: {}", ctx.channel());
            // 心跳消息不需要额外处理
            return;
        }

        // 5. 读取数据长度
        int dataLength = in.readInt();

        // 6. 检查数据长度是否合理
        if (dataLength < 0 || dataLength > 10 * 1024 * 1024) { // 限制10MB
            log.warn("数据长度不合理: {}, 来自: {}", dataLength, ctx.channel().remoteAddress());
            in.skipBytes(in.readableBytes());
            return;
        }

        // 7. 检查可读的数据是否完整
        if (in.readableBytes() < dataLength) {
            // 数据不完整，等待更多数据
            in.resetReaderIndex();
            return;
        }

        // 8. 读取数据
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        // 9. 根据上下文识别消息类型（请求/响应）
        try {
            Object obj;
            // 根据处理器类型识别数据类型
            if (isServer(ctx)) {
                obj = serializer.deserialize(data, RpcRequest.class);
            } else {
                obj = serializer.deserialize(data, RpcResponse.class);
            }
            out.add(obj);

            if (log.isDebugEnabled()) {
                log.debug("解码消息: 类型={}, 大小={}字节",
                        obj.getClass().getSimpleName(), dataLength);
            }
        } catch (Exception e) {
            log.error("反序列化数据时发生异常: {}", e.getMessage());
            // 不再抛出异常，避免连接关闭
        }
    }

    /**
     * 判断当前处理器是在服务端还是客户端
     * 
     * @param ctx 通道上下文
     * @return 如果是服务端返回true，客户端返回false
     */
    private boolean isServer(ChannelHandlerContext ctx) {
        // 检查pipeline中是否包含服务端特有的处理器
        return ctx.pipeline().get("serverHandler") != null;
    }

    /**
     * 判断是否是一个空连接（如健康检查连接）
     * 
     * @param magic 魔数字节数组
     * @return 如果是空连接返回true
     */
    private boolean isEmptyConnection(byte[] magic) {
        // [0,0] 很可能是健康检查连接
        return magic[0] == 0 && magic[1] == 0;
    }
}