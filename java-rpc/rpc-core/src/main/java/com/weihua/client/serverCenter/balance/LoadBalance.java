/*
 * @Author: weihua hu
 * @Date: 2025-03-21 19:20:10
 * @LastEditTime: 2025-04-03 22:21:43
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.client.serverCenter.balance;

import java.util.List;

import common.spi.annotation.SPI;

@SPI("consistentHash")
public interface LoadBalance {
    String balance(List<String> addressList);
}
