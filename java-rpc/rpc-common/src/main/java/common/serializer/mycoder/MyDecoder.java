package common.serializer.mycoder;

import common.exception.SerializeException;
import common.message.MessageType;
import common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class MyDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        /*
         * 1、先读取字节长度，不够6字节，直接return。不是标准的数据
         * 2、读取前2字节，查看消息类型，目前只支持RpcRequest,RpcResponse
         * 3、验证消息类型，不符合直接return
         * 4、读取序列化类型
         * 5、获取序列化器
         * 6、读取数据长度
         * 7、验证数据完整性
         * 8、读取实际数据
         * 9、反序列化成Object
         * */
        if (in.readableBytes() < 6) {
            return;
        }

        short messageType = in.readShort();
        if (messageType != MessageType.REQUEST.getCode() && messageType != MessageType.RESPONSE.getCode()) {
            return;
        }

        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByType(serializerType);
        if (serializer == null) {
            throw new SerializeException("序列化器获取失败");
        }
        int length = in.readInt();
        if (length > in.readableBytes()) {
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);

        Object deSerialize = serializer.deSerialize(bytes, messageType);
        out.add(deSerialize);

    }
}
