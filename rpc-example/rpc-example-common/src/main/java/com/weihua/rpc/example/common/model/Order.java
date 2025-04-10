/*
 * @Author: weihua hu
 * @Date: 2025-04-10 15:03:58
 * @LastEditTime: 2025-04-10 15:03:59
 * @LastEditors: weihua hu
 * @Description: 
 */
package com.weihua.rpc.example.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    private String id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 支付状态（0-未支付，1-已支付）
     */
    private Integer payStatus;

    /**
     * 订单状态（0-已取消，1-待支付，2-已支付待发货，3-已发货，4-已完成）
     */
    private Integer orderStatus;

    /**
     * 支付ID
     */
    private String paymentId;

    /**
     * 订单创建时间
     */
    private Date createTime;

    /**
     * 订单支付时间
     */
    private Date payTime;

    /**
     * 订单更新时间
     */
    private Date updateTime;
}
