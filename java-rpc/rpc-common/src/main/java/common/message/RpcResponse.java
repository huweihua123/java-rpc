/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:14:21
 * @LastEditTime: 2025-04-02 23:47:05
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
public class RpcResponse implements Serializable {
    private int code;
    private String requestId;
    private String message;
    private Object data;
    private Class<?> datatype;

    public static RpcResponse success(Object data) {
        return RpcResponse.builder().code(200).datatype(data.getClass()).data(data).build();
    }

    public static RpcResponse success(Object data, String requestId) {
        return RpcResponse.builder().code(200).requestId(requestId).datatype(data.getClass()).data(data).build();
    }

    public static RpcResponse fail() {
        return RpcResponse.builder().code(500).message("服务器发送内部错误").build();
    }

    public static RpcResponse fail(String msg) {
        return RpcResponse.builder().code(500).message(msg).build();
    }
}
