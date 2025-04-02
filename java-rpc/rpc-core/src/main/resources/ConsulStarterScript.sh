#!/bin/bash
###
 # @Author: weihua hu
 # @Date: 2025-04-02 13:52:41
 # @LastEditTime: 2025-04-02 13:52:43
 # @LastEditors: weihua hu
 # @Description: 
### 

# Consul启动脚本
# 此脚本用于正确启动单节点Consul服务器
# 适用于开发和测试环境

# 检查Consul是否已安装
if ! command -v consul &> /dev/null; then
    echo "Consul未安装，请先安装Consul"
    echo "可以从https://developer.hashicorp.com/consul/downloads下载"
    exit 1
fi

# 创建数据目录
mkdir -p /tmp/consul-data

# 启动Consul服务器
# -dev: 开发模式，适合本地测试
# -client: 允许从任何IP访问API
# -ui: 启用Web界面
# -bind: 绑定到本机IP
# -node: 节点名称
# -data-dir: 数据目录
consul agent -dev -client=0.0.0.0 -ui -bind=127.0.0.1 -node=dev-node -data-dir=/tmp/consul-data

# 如果需要生产环境配置，请使用以下配置替代上述命令:
# consul agent -server -bootstrap-expect=1 -data-dir=/tmp/consul-data -node=server-1 -bind=YOUR_SERVER_IP -client=0.0.0.0 -ui
