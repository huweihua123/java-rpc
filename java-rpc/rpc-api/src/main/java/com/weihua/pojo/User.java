/*
 * @Author: weihua hu
 * @Date: 2025-03-20 19:36:39
 * @LastEditTime: 2025-03-24 16:03:51
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.pojo;

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
