package common.serializer.mycoder;

import common.message.MessageType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MyEncoder extends MessageToByteEncoder {
    private Serializer serializer;

    /*
     * 1、写入message type
     * 2、写入serializer type
     * 3、序列化message,得到序列化数组
     * 3、写入message length
     * 4、写入message
     * */
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
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
