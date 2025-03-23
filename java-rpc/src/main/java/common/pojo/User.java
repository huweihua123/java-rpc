/*
 * @Author: weihua hu
 * @Date: 2025-03-20 19:36:39
 * @LastEditTime: 2025-03-20 19:40:39
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {
    // 客户端和服务端共有的
    private Integer id;
    private String userName;
    private Boolean sex;
}
