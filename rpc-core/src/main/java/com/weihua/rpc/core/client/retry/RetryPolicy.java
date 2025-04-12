// package com.weihua.rpc.core.client.retry;

// import com.weihua.rpc.common.exception.RpcException;
// import com.weihua.rpc.common.model.RpcRequest;
// import com.weihua.rpc.core.client.registry.ServiceCenter;
// import lombok.extern.slf4j.Slf4j;

// import java.util.Arrays;
// import java.util.HashSet;
// import java.util.Map;
// import java.util.Set;
// import java.util.concurrent.ConcurrentHashMap;

// /**
// * RPC重试策略
// * 根据方法签名决定是否重试和如何重试
// */
// @Slf4j
// public class RetryPolicy {

// private final ServiceCenter serviceCenter;

// // 缓存方法的重试配置
// private final Map<String, RetryConfig> retryConfigCache = new
// ConcurrentHashMap<>();

// public RetryPolicy(ServiceCenter serviceCenter) {
// this.serviceCenter = serviceCenter;
// }

// /**
// * 判断是否应该重试
// *
// * @param request 请求
// * @param exception 异常
// * @param attemptCount 当前尝试次数
// * @return 是否应该重试
// */
// public boolean shouldRetry(RpcRequest request, Throwable exception, int
// attemptCount) {
// String methodSignature = buildMethodSignature(request);

// // 获取重试配置
// RetryConfig config = getRetryConfig(methodSignature);
// if (config == null || !config.isRetryable()) {
// return false;
// }

// // 检查重试次数
// if (attemptCount >= config.getMaxRetries()) {
// log.debug("方法[{}]已达到最大重试次数: {}", methodSignature, config.getMaxRetries());
// return false;
// }

// // 检查异常类型是否可重试
// if (!isExceptionRetryable(exception, config)) {
// log.debug("方法[{}]的异常不可重试: {}", methodSignature,
// exception.getClass().getName());
// return false;
// }

// return true;
// }

// /**
// * 获取重试间隔时间
// *
// * @param methodSignature 方法签名
// * @param attemptCount 当前尝试次数
// * @return 下次重试前等待时间(毫秒)
// */
// public long getRetryInterval(String methodSignature, int attemptCount) {
// RetryConfig config = getRetryConfig(methodSignature);
// if (config == null) {
// return 1000; // 默认1秒
// }

// // 如果使用退避策略，则每次重试增加等待时间
// if (config.isBackoff()) {
// return config.getRetryInterval() * (attemptCount + 1);
// } else {
// return config.getRetryInterval();
// }
// }

// /**
// * 获取方法的重试配置
// */
// private RetryConfig getRetryConfig(String methodSignature) {
// return retryConfigCache.computeIfAbsent(methodSignature,
// this::fetchRetryConfig);
// }

// /**
// * 从服务中心获取重试配置
// */
// private RetryConfig fetchRetryConfig(String methodSignature) {
// // 检查方法是否可重试
// boolean isRetryable = serviceCenter.isMethodRetryable(methodSignature);
// if (!isRetryable) {
// return new RetryConfig(false, 0, 0, false, new HashSet<>(), new HashSet<>());
// }

// // 尝试获取详细配置
// Map<String, String> retryMeta = null;
// if (serviceCenter instanceof ConsulServiceCenter) {
// retryMeta = ((ConsulServiceCenter)
// serviceCenter).getMethodRetryConfig(methodSignature);
// }

// // 如果没有详细配置，使用默认值
// if (retryMeta == null || retryMeta.isEmpty()) {
// return new RetryConfig(true, 3, 1000, true,
// new HashSet<>(Arrays.asList(Exception.class)), new HashSet<>());
// }

// // 解析配置
// int maxRetries = Integer.parseInt(retryMeta.getOrDefault("maxRetries", "3"));
// long retryInterval = Long.parseLong(retryMeta.getOrDefault("retryInterval",
// "1000"));
// boolean backoff = Boolean.parseBoolean(retryMeta.getOrDefault("backoff",
// "true"));

// // 异常类型暂时使用默认值
// Set<Class<? extends Throwable>> retryFor = new
// HashSet<>(Arrays.asList(Exception.class));
// Set<Class<? extends Throwable>> noRetryFor = new HashSet<>();

// return new RetryConfig(true, maxRetries, retryInterval, backoff, retryFor,
// noRetryFor);
// }

// /**
// * 检查异常是否可重试
// */
// private boolean isExceptionRetryable(Throwable exception, RetryConfig config)
// {
// // 优先检查不可重试异常
// for (Class<? extends Throwable> exClass : config.getNoRetryFor()) {
// if (exClass.isInstance(exception)) {
// return false;
// }
// }

// // 再检查可重试异常
// for (Class<? extends Throwable> exClass : config.getRetryFor()) {
// if (exClass.isInstance(exception)) {
// return true;
// }
// }

// // 默认根据异常类型判断
// return !(exception instanceof RpcException &&
// !((RpcException) exception).isRetryable());
// }

// /**
// * 构建方法签名
// */
// private String buildMethodSignature(RpcRequest request) {
// StringBuilder sb = new StringBuilder();
// sb.append(request.getInterfaceName())
// .append('#')
// .append(request.getMethodName())
// .append('(');

// if (request.getParameterTypes() != null) {
// for (int i = 0; i < request.getParameterTypes().length; i++) {
// if (i > 0) {
// sb.append(',');
// }
// sb.append(request.getParameterTypes()[i].getName());
// }
// }

// sb.append(')');
// return sb.toString();
// }

// /**
// * 重试配置类
// */
// public static class RetryConfig {
// private final boolean retryable;
// private final int maxRetries;
// private final long retryInterval;
// private final boolean backoff;
// private final Set<Class<? extends Throwable>> retryFor;
// private final Set<Class<? extends Throwable>> noRetryFor;

// public RetryConfig(boolean retryable, int maxRetries, long retryInterval,
// boolean backoff, Set<Class<? extends Throwable>> retryFor,
// Set<Class<? extends Throwable>> noRetryFor) {
// this.retryable = retryable;
// this.maxRetries = maxRetries;
// this.retryInterval = retryInterval;
// this.backoff = backoff;
// this.retryFor = retryFor;
// this.noRetryFor = noRetryFor;
// }

// public boolean isRetryable() {
// return retryable;
// }

// public int getMaxRetries() {
// return maxRetries;
// }

// public long getRetryInterval() {
// return retryInterval;
// }

// public boolean isBackoff() {
// return backoff;
// }

// public Set<Class<? extends Throwable>> getRetryFor() {
// return retryFor;
// }

// public Set<Class<? extends Throwable>> getNoRetryFor() {
// return noRetryFor;
// }
// }
// }
