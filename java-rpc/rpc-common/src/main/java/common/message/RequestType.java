/*
 * @Author: weihua hu
 * @Date: 2025-04-01 21:25:54
 * @LastEditTime: 2025-04-02 17:36:37
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.message;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum RequestType {
    NORMAL(0), HEARTBEAT(1);

    private int code;

    public int getCode() {
        return code;
    }
}