/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:14:21
 * @LastEditTime: 2025-04-07 19:00:00
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {
    private RequestType type = RequestType.NORMAL;
    private String interfaceName;
    private String requestId;
    private String methodName;
    private Object[] parameters; // 改名使更符合标准命名规范
    private Class<?>[] paramTypes;
    private long timestamp; // 添加时间戳字段，用于性能监控
    private String serviceVersion; // 添加服务版本字段，支持多版本
    private String group; // 添加分组字段，支持服务分组
    private Map<String, String> attachments; // 附加信息，用于传递自定义数据

    // 链路追踪相关字段
    private String traceId; // 全局追踪ID
    private String spanId; // 当前调用的spanId
    private String parentSpanId; // 父级调用的spanId

    /**
     * 创建心跳请求
     */
    public static RpcRequest heartBeat() {
        // 使用更短的心跳ID格式，以区分普通请求
        String heartbeatId = "heartbeat-" + System.currentTimeMillis();
        return RpcRequest.builder()
                .type(RequestType.HEARTBEAT)
                .requestId(heartbeatId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 判断请求是否为心跳请求
     */
    public boolean isHeartBeat() {
        return RequestType.HEARTBEAT.equals(this.type);
    }

    /**
     * 获取方法签名
     * 格式：接口名#方法名(参数类型列表)
     */
    public String getMethodSignature() {
        if (interfaceName == null || methodName == null) {
            return null;
        }

        StringBuilder signature = new StringBuilder();
        signature.append(interfaceName).append("#").append(methodName).append("(");

        if (paramTypes != null && paramTypes.length > 0) {
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) {
                    signature.append(",");
                }
                signature.append(paramTypes[i].getSimpleName());
            }
        }

        signature.append(")");
        return signature.toString();
    }

    /**
     * 设置追踪信息
     */
    public RpcRequest withTrace(String traceId, String spanId, String parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        return this;
    }

    /**
     * 添加附加信息
     */
    public RpcRequest addAttachment(String key, String value) {
        if (this.attachments == null) {
            this.attachments = new java.util.HashMap<>();
        }
        this.attachments.put(key, value);
        return this;
    }

    /**
     * 兼容旧代码，保留params getter和setter
     */
    public Object[] getParams() {
        return parameters;
    }

    public void setParams(Object[] params) {
        this.parameters = params;
    }
}