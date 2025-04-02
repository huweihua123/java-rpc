/*
 * @Author: weihua hu
 * @Date: 2025-03-21 01:14:21
 * @LastEditTime: 2025-04-02 23:02:59
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
        return RpcRequest.builder().type(RequestType.HEARTBEAT).build();
    }
}
