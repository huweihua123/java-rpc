package common.serializer.mySerializer;

import com.alibaba.fastjson.JSONObject;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class JsonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object object) {
        byte[] bytes = JSONObject.toJSONBytes(object);
        return bytes;
    }

    @Override
    public Object deSerialize(byte[] bytes, int messageType) {
        Object obj = null;

        switch (messageType) {
            case 0: // 处理RpcRequest
                RpcRequest request = JSONObject.parseObject(bytes, RpcRequest.class);
                if (request.getParamTypes() != null) {
                    Object[] params = request.getParams();
                    for (int i = 0; i < params.length; i++) {
                        if (params[i] instanceof JSONObject
                                && !request.getParamTypes()[i].isAssignableFrom(params[i].getClass())) {
                            params[i] = JSONObject.toJavaObject((JSONObject) params[i], request.getParamTypes()[i]);
                        }
                    }
                }
                obj = request;
                break;
            case 1: // 处理RpcResponse
                RpcResponse response = JSONObject.parseObject(bytes, RpcResponse.class);

                // 记录接收到的响应信息，帮助调试
                log.debug("反序列化RpcResponse: requestId={}, code={}, message={}",
                        response.getRequestId(), response.getCode(),
                        response.getMessage() != null && response.getMessage().length() > 50
                                ? response.getMessage().substring(0, 50) + "..."
                                : response.getMessage());

                // 移除错误的datatype判断，datatype为null是合法的
                // 例如：错误响应、void方法、限流响应等不需要返回数据

                // 只有在需要处理响应数据时才进行类型转换
                if (response.getData() != null && response.getDatatype() != null &&
                        !response.getData().getClass().isAssignableFrom(response.getDatatype())) {
                    response.setData(JSONObject.toJavaObject((JSONObject) response.getData(), response.getDatatype()));
                }

                obj = response;
                break;
            default:
                // System.out.println("暂不支持此种类型消息");
                log.error("暂不支持此种类型消息");
                throw new RuntimeException();
        }

        return obj;
    }

    @Override
    public int getType() {
        return 1;
    }

    @Override
    public String toString() {
        return "Json Serializer";
    }

}
