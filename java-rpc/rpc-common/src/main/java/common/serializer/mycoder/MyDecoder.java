/*
 * @Author: weihua hu
 * @Date: 2025-03-25 14:36:35
 * @LastEditTime: 2025-04-03 02:08:09
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.serializer.mycoder;

import common.exception.SerializeException;
import common.message.MessageType;
import common.serializer.mySerializer.Serializer;
import common.trace.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.log4j.Log4j2;
import java.util.List;

@Log4j2
public class MyDecoder extends ByteToMessageDecoder {
    // @Override
    // protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object>
    // out) throws Exception {
    // /*
    // * 1、读取trace消息长度
    // * 2、读取trace消息体
    // * 3、先读取字节长度，不够6字节，直接return。不是标准的数据
    // * 4、读取前2字节，查看消息类型，目前只支持RpcRequest,RpcResponse
    // * 5、验证消息类型，不符合直接return
    // * 6、读取序列化类型
    // * 7、获取序列化器
    // * 8、读取数据长度
    // * 9、验证数据完整性
    // * 10、读取实际数据
    // * 11、反序列化成Object
    // * */
    // if (in.readableBytes() < 6) {
    // return;
    // }

    // int traceLength = in.readInt();
    // byte[] traceBytes = new byte[traceLength];
    // log.info("traceLength: {}", traceLength);
    // in.readBytes(traceBytes);
    // serializeTraceMsg(traceBytes);

    // short messageType = in.readShort();
    // if (messageType != MessageType.REQUEST.getCode() && messageType !=
    // MessageType.RESPONSE.getCode()) {
    // log.warn("暂不支持此种数据, messageType: {}", messageType);

    // return;
    // }

    // short serializerType = in.readShort();
    // Serializer serializer = Serializer.getSerializerByType(serializerType);
    // if (serializer == null) {
    // log.error("不存在对应的序列化器, serializerType: {}", serializerType);
    // throw new SerializeException("序列化器获取失败");
    // }
    // int length = in.readInt();
    // if (length > in.readableBytes()) {
    // return;
    // }

    // byte[] bytes = new byte[length];
    // in.readBytes(bytes);
    // log.debug("Received bytes: {}", Arrays.toString(bytes));
    // Object deSerialize = serializer.deSerialize(bytes, messageType);
    // out.add(deSerialize);

    // }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 标记当前读位置，以便需要时可以回退
        in.markReaderIndex();

        // 1. 检查是否有足够的字节读取基础头部
        if (in.readableBytes() < 4) {
            return; // 不够读取traceLength，等待更多数据
        }

        // 2. 读取trace长度
        int traceLength = in.readInt();

        // 3. 验证traceLength的合理性
        if (traceLength <= 0 || traceLength > 8192) { // 设置合理上限，如8KB
            log.error("非法的trace长度: {}", traceLength);
            // 丢弃当前数据并等待新的帧
            in.skipBytes(in.readableBytes());
            return;
        }

        // 4. 确保有足够的字节读取trace内容
        if (in.readableBytes() < traceLength) {
            // 数据不完整，回退读取位置，等待数据到齐
            in.resetReaderIndex();

            return;
        }

        byte[] traceBytes = new byte[traceLength];
        log.debug("traceLength: {}", traceLength);
        in.readBytes(traceBytes);

        try {
            serializeTraceMsg(traceBytes);
        } catch (Exception e) {
            log.error("解析trace消息失败", e);
            return;
        }

        // 5. 检查是否有足够字节继续读取消息类型和序列化类型
        if (in.readableBytes() < 4) { // 2字节messageType + 2字节serializerType
            in.resetReaderIndex();
            return;
        }

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

        // 6. 检查是否有足够字节读取消息长度
        if (in.readableBytes() < 4) {
            in.resetReaderIndex();
            return;
        }

        int length = in.readInt();

        // 7. 验证消息长度合理性
        if (length <= 0 || length > 1048576) { // 限制消息最大1MB
            log.error("非法的消息长度: {}", length);
            return;
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);
//        log.debug("Received bytes: {}", Arrays.toString(bytes));
        Object deSerialize = serializer.deSerialize(bytes, messageType);
        log.info("msg:{}",deSerialize);
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
