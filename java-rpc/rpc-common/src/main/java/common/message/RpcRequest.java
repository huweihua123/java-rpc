/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:14:21
 * @LastEditTime: 2025-04-04 20:42:09
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {
    private RequestType type = RequestType.NORMAL;
    private String interfaceName;
    private String requestId;
    private String methodName;
    private Object[] params;
    private Class<?>[] paramTypes;

    public static RpcRequest heartBeat() {
        // 使用更短的心跳ID格式，以区分普通请求
        String heartbeatId = "heartbeat-" + System.currentTimeMillis();
        return RpcRequest.builder()
                .type(RequestType.HEARTBEAT)
                .requestId(heartbeatId)
                .build();
    }

    /**
     * 判断请求是否为心跳请求
     */
    public boolean isHeartBeat() {
        return RequestType.HEARTBEAT.equals(this.type);
    }
}