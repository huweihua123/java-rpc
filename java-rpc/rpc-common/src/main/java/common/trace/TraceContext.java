package common.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * 跟踪上下文，用于存储当前请求的跟踪信息
 * 支持多级服务调用链
 */
public class TraceContext {
    private static final ThreadLocal<Map<String, String>> CONTEXT = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Stack<String>> SPAN_ID_STACK = ThreadLocal.withInitial(Stack::new);

    // 基本跟踪字段
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String PARENT_SPAN_ID = "parentSpanId";
    private static final String START_TIMESTAMP = "startTimestamp";
    private static final String SERVICE_NAME = "serviceName";

    // 获取当前上下文的所有值
    public static Map<String, String> getAll() {
        return new HashMap<>(CONTEXT.get());
    }

    // 设置值
    public static void set(String key, String value) {
        CONTEXT.get().put(key, value);
    }

    // 获取值
    public static String get(String key) {
        return CONTEXT.get().get(key);
    }

    // 清除所有上下文
    public static void clear() {
        CONTEXT.get().clear();
        SPAN_ID_STACK.get().clear();
    }

    // TraceId 相关方法
    public static void setTraceId(String traceId) {
        set(TRACE_ID, traceId);
    }

    public static String getTraceId() {
        return get(TRACE_ID);
    }

    // SpanId 相关方法，支持多级调用
    public static void setSpanId(String spanId) {
        SPAN_ID_STACK.get().push(spanId); // 保存当前spanId到栈
        set(SPAN_ID, spanId);
    }

    public static String getSpanId() {
        return get(SPAN_ID);
    }

    // 恢复上一级的spanId（用于服务调用返回时）
    public static void restorePreviousSpanId() {
        Stack<String> stack = SPAN_ID_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop(); // 移除当前spanId
            if (!stack.isEmpty()) {
                set(SPAN_ID, stack.peek()); // 恢复上一级spanId
            }
        }
    }

    // 父SpanId 相关方法
    public static void setParentSpanId(String parentSpanId) {
        set(PARENT_SPAN_ID, parentSpanId);
    }

    public static String getParentSpanId() {
        return get(PARENT_SPAN_ID);
    }

    // 开始时间戳
    public static void setStartTimestamp(String timestamp) {
        set(START_TIMESTAMP, timestamp);
    }

    public static String getStartTimestamp() {
        return get(START_TIMESTAMP);
    }

    // 服务名称
    public static void setServiceName(String serviceName) {
        set(SERVICE_NAME, serviceName);
    }

    public static String getServiceName() {
        return get(SERVICE_NAME);
    }

    // 将上下文转为Map，用于在服务间传递
    public static Map<String, String> toMap() {
        return getAll();
    }

    // 从Map恢复上下文，用于服务接收时
    public static void fromMap(Map<String, String> contextMap) {
        if (contextMap != null) {
            CONTEXT.get().putAll(contextMap);
        }
    }
}