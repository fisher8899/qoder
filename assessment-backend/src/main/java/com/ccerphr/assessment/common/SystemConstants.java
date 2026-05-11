package com.ccerphr.assessment.common;

import java.security.SecureRandom;
import java.util.stream.Collectors;

/**
 * 系统常量定义
 */
public final class SystemConstants {

    private SystemConstants() {
        // 防止实例化
    }

    /**
     * 密码生成配置
     */
    public static final class Password {
        private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
        private static final String DIGITS = "23456789";
        private static final String ALL_CHARS = UPPER + LOWER + DIGITS;
        private static final int DEFAULT_LENGTH = 8;
        private static final SecureRandom RANDOM = new SecureRandom();

        private Password() {}

        /**
         * 生成随机密码
         * @return 随机生成的8位密码，包含大小写字母和数字
         */
        public static String generateRandom() {
            return RANDOM.ints(DEFAULT_LENGTH, 0, ALL_CHARS.length())
                    .mapToObj(i -> String.valueOf(ALL_CHARS.charAt(i)))
                    .collect(Collectors.joining());
        }

        /**
         * 生成指定长度的随机密码
         * @param length 密码长度
         * @return 随机生成的密码
         */
        public static String generateRandom(int length) {
            if (length < 6) {
                length = 6;
            }
            return RANDOM.ints(length, 0, ALL_CHARS.length())
                    .mapToObj(i -> String.valueOf(ALL_CHARS.charAt(i)))
                    .collect(Collectors.joining());
        }
    }

    /**
     * 用户状态常量
     */
    public static final class UserStatus {
        public static final int ENABLED = 1;
        public static final int DISABLED = 0;

        private UserStatus() {}
    }

    /**
     * 删除标记常量
     */
    public static final class DeleteFlag {
        public static final int NOT_DELETED = 0;
        public static final int DELETED = 1;

        private DeleteFlag() {}
    }
}
