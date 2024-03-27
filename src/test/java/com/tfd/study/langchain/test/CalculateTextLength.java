package com.tfd.study.langchain.test;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.lang3.StringUtils;

/**
 * @author TangFD 2024/3/27
 */
public class CalculateTextLength {
    private final String content = null;

    @Tool("这是一个用于计算给定的文本内容具有多少个字（文本长度）的方法")
    public int calculateTextLength(@P("需要计算长度的文本内容") String content) {
        return StringUtils.isEmpty(content) ? 0 : content.length();
    }

}
