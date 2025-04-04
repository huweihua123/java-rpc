/*
 * @Author: weihua hu
 * 
 * @Date: 2025-04-03 23:02:20
 * 
 * @LastEditTime: 2025-04-04 21:24:05
 * 
 * @LastEditors: weihua hu
 * 
 * @Description:
 */
package common.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * URL类，作为配置传递载体
 */
public class URL {
    private final String protocol;
    private final String host;
    private final int port;
    private final String path;
    private final Map<String, String> parameters;

    public URL(String protocol, String host, int port, String path) {
        this(protocol, host, port, path, new HashMap<>());
    }

    public URL(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        return value != null ? value : defaultValue;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public URL addParameter(String key, String value) {
        Map<String, String> map = new HashMap<>(parameters);
        map.put(key, value);
        return new URL(protocol, host, port, path, map);
    }

    public URL addParameters(Map<String, String> parameters) {
        Map<String, String> map = new HashMap<>(this.parameters);
        map.putAll(parameters);
        return new URL(protocol, host, port, path, map);
    }

    @Override
    public String toString() {
        // 组装URL字符串
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host).append(':').append(port);
        if (path != null && path.length() > 0) {
            sb.append('/').append(path);
        }
        if (!parameters.isEmpty()) {
            sb.append('?');
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public static URL valueOf(String url) {
        // 解析URL字符串
        // 这里简化实现，实际应该有完整的解析逻辑
        int protocolIndex = url.indexOf("://");
        String protocol = url.substring(0, protocolIndex);

        int hostEnd = url.indexOf(":", protocolIndex + 3);
        String host = url.substring(protocolIndex + 3, hostEnd);

        int portEnd = url.indexOf("/", hostEnd);
        if (portEnd == -1) {
            portEnd = url.indexOf("?", hostEnd);
            if (portEnd == -1) {
                portEnd = url.length();
            }
        }
        int port = Integer.parseInt(url.substring(hostEnd + 1, portEnd));

        String path = "";
        int pathEnd = url.indexOf("?");
        if (portEnd < url.length() && url.charAt(portEnd) == '/') {
            if (pathEnd == -1) {
                path = url.substring(portEnd + 1);
            } else {
                path = url.substring(portEnd + 1, pathEnd);
            }
        }

        Map<String, String> parameters = new HashMap<>();
        if (pathEnd != -1) {
            String query = url.substring(pathEnd + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    parameters.put(kv[0], kv[1]);
                }
            }
        }

        return new URL(protocol, host, port, path, parameters);
    }
}