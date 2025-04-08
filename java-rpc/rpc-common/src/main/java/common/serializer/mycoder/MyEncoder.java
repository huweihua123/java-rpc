package common.serializer.mycoder;

import common.message.MessageType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.mySerializer.Serializer;
import common.trace.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor
public class MyEncoder extends MessageToByteEncoder {
    private Serializer serializer;

    /*
     * 1、写入trace消息长度
     * 2、写入trace消息体
     * 3、写入message type
     * 4、写入serializer type
     * 5、序列化message,得到序列化数组
     * 6、写入message length
     * 7、写入message
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        log.debug("Encoding message of type: {}", msg.getClass());

        // 1.写入trace消息长度
        String traceMsg = TraceContext.getTraceId() + ";" + TraceContext.getSpanId();
        byte[] traceBytes = traceMsg.getBytes();
        out.writeInt(traceBytes.length);
        // 2.写入trace消息体
        out.writeBytes(traceBytes);
        log.info("msg:{}", msg);

        if (msg instanceof RpcRequest) {
            out.writeShort(MessageType.REQUEST.getCode());
        } else if (msg instanceof RpcResponse) {
            out.writeShort(MessageType.RESPONSE.getCode());
        } else {
            throw new IllegalArgumentException("Unknown message type: " + msg.getClass());
        }

        out.writeShort(serializer.getType());

        byte[] bytes = serializer.serialize(msg);

        if (bytes == null && bytes.length == 0) {
            throw new IllegalArgumentException("Serialized message is empty");
        }

        out.writeInt(bytes.length);

        out.writeBytes(bytes);
    }
}
