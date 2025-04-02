package common.trace;

import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;

import java.util.Map;

@Log4j2
public class TraceContext {
    public static String getTraceId() {
        return ThreadContext.get("traceId");
    }

    public static void setTraceId(String traceId) {
        ThreadContext.put("traceId", traceId);
    }

    public static String getSpanId() {
        return ThreadContext.get("spanId");
    }

    public static void setSpanId(String spanId) {
        ThreadContext.put("spanId", spanId);
    }

    // parentSpanId
    public static String getParentSpanId() {
        return ThreadContext.get("parentSpanId");
    }

    public static void setParentSpanId(String parentSpanId) {
        ThreadContext.put("parentSpanId", parentSpanId);
    }

    public static String getStartTimestamp() {
        return ThreadContext.get("startTimestamp");
    }

    public static void setStartTimestamp(String startTimestamp) {
        ThreadContext.put("startTimestamp", startTimestamp);
    }

    public static void clear() {
        ThreadContext.clearAll();
    }

    public static void clone(Map<String, String> context) {
        for (Map.Entry<String, String> entry : context.entrySet()) {
            ThreadContext.put(entry.getKey(), entry.getValue());
        }
    }

    public static Map<String, String> getCopy() {
        return ThreadContext.getContext();
    }

}
