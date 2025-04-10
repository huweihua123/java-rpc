/*
 * @Author: weihua hu
 * @Date: 2025-04-10 02:07:46
 * @LastEditTime: 2025-04-10 02:07:48
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.core.protocol.codec;

import com.weihua.rpc.core.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC编码器
 * 将Java对象编码为二进制格式发送到网络
 */
@Slf4j
public class RpcEncoder extends MessageToByteEncoder<Object> {

    private final Serializer serializer;

    // 协议魔数，用于快速识别协议包
    private static final byte[] MAGIC_NUMBER = { (byte) 0xAB, (byte) 0xBA };

    // 协议版本
    private static final byte VERSION = 0x01;

    public RpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        try {
            // 1. 写入魔数 (2字节)
            out.writeBytes(MAGIC_NUMBER);

            // 2. 写入版本号 (1字节)
            out.writeByte(VERSION);

            // 3. 写入序列化类型 (1字节)
            out.writeByte(serializer.getType());

            // 4. 序列化对象为字节数组
            byte[] data = serializer.serialize(msg);

            // 5. 写入数据长度 (4字节)
            out.writeInt(data.length);

            // 6. 写入序列化后的数据
            out.writeBytes(data);

            if (log.isDebugEnabled()) {
                log.debug("编码消息: 类型={}, 大小={}字节",
                        msg.getClass().getSimpleName(), data.length);
            }
        } catch (Exception e) {
            log.error("编码消息时发生异常", e);
            throw e;
        }
    }
}
