package com.weihua.rpc.common.utils;

/**
 * 字符串工具类
 */
public class StringUtils {

    /**
     * 判断字符串是否为空或null
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空且不为null
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白字符或null
     */
    public static boolean isBlank(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否不为空白字符且不为null
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 将字符串首字母小写
     */
    public static String toLowerCaseFirstChar(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (Character.isLowerCase(str.charAt(0))) {
            return str;
        }
        char[] chars = str.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * 将字符串首字母大写
     */
    public static String toUpperCaseFirstChar(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (Character.isUpperCase(str.charAt(0))) {
            return str;
        }
        char[] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /**
     * 去除两端空格，如果为null则返回空字符串
     */
    public static String trim(String str) {
        return str == null ? "" : str.trim();
    }

    /**
     * 将驼峰格式转为下划线分隔格式
     * 例如：helloWorld -> hello_world
     */
    public static String camelToUnderscore(String str) {
        if (isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length() + 5);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 将下划线分隔格式转为驼峰格式
     * 例如：hello_world -> helloWorld
     */
    public static String underscoreToCamel(String str) {
        if (isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        boolean nextUpperCase = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    sb.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
