package common.serializer.mycoder;

import common.exception.SerializeException;
import common.message.MessageType;
import common.serializer.mySerializer.Serializer;
import common.trace.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;

@Log4j2
public class MyDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        /*
         * 1、读取trace消息长度
         * 2、读取trace消息体
         * 3、先读取字节长度，不够6字节，直接return。不是标准的数据
         * 4、读取前2字节，查看消息类型，目前只支持RpcRequest,RpcResponse
         * 5、验证消息类型，不符合直接return
         * 6、读取序列化类型
         * 7、获取序列化器
         * 8、读取数据长度
         * 9、验证数据完整性
         * 10、读取实际数据
         * 11、反序列化成Object
         * */
        if (in.readableBytes() < 6) {
            return;
        }

        int traceLength = in.readInt();
        byte[] traceBytes = new byte[traceLength];
        log.info("traceLength: {}", traceLength);
        in.readBytes(traceBytes);
        serializeTraceMsg(traceBytes);

        short messageType = in.readShort();
        if (messageType != MessageType.REQUEST.getCode() && messageType != MessageType.RESPONSE.getCode()) {
            log.warn("暂不支持此种数据, messageType: {}", messageType);

            return;
        }

        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByType(serializerType);
        if (serializer == null) {
            log.error("不存在对应的序列化器, serializerType: {}", serializerType);
            throw new SerializeException("序列化器获取失败");
        }
        int length = in.readInt();
        if (length > in.readableBytes()) {
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        log.debug("Received bytes: {}", Arrays.toString(bytes));
        Object deSerialize = serializer.deSerialize(bytes, messageType);
        out.add(deSerialize);

    }

    private void serializeTraceMsg(byte[] traceBytes) {
        String traceMsg = new String(traceBytes);
        String[] msgs = traceMsg.split(";");

        if (!msgs[0].equals("")) {
            TraceContext.setTraceId(msgs[0]);
        }

        if (!msgs[1].equals("")) {
            TraceContext.setSpanId(msgs[1]);
        }
    }
}
