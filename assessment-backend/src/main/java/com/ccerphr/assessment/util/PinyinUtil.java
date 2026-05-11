package com.ccerphr.assessment.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;

public class PinyinUtil {

    /**
     * 生成用户名：姓全拼 + 名首字母
     * 如：何科技 → hekj，张三 → zhangs，欧阳明 → ouyangm
     */
    public static String generateUsername(String chineseName) {
        if (chineseName == null || chineseName.isEmpty()) {
            return "";
        }

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder sb = new StringBuilder();
        char[] chars = chineseName.toCharArray();

        try {
            // 判断姓的长度（处理复姓）
            int surnameLength = getSurnameLength(chineseName);

            // 姓取全拼
            for (int i = 0; i < surnameLength; i++) {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(chars[i], format);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    sb.append(pinyinArray[0]);
                } else {
                    sb.append(chars[i]);
                }
            }

            // 名取首字母
            for (int i = surnameLength; i < chars.length; i++) {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(chars[i], format);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    sb.append(pinyinArray[0].charAt(0));
                } else {
                    sb.append(chars[i]);
                }
            }
        } catch (Exception e) {
            return "";
        }

        return sb.toString().toLowerCase();
    }

    /**
     * 判断姓的长度（处理复姓）
     */
    private static int getSurnameLength(String name) {
        if (name.length() <= 1) return 1;
        String[] compoundSurnames = {"欧阳", "司马", "上官", "诸葛", "司徒", "东方", "独孤",
                "南宫", "万俟", "闻人", "夏侯", "皇甫", "尉迟", "公羊", "澹台", "公冶",
                "宗政", "濮阳", "淳于", "单于", "太叔", "申屠", "公孙", "仲孙", "轩辕",
                "令狐", "钟离", "宇文", "长孙", "慕容", "鲜于", "闾丘", "司空", "端木"};
        for (String cs : compoundSurnames) {
            if (name.startsWith(cs)) {
                return 2;
            }
        }
        return 1;
    }
}
