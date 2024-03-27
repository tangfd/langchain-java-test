package com.tfd.study.langchain.test;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * @author TangFD 2024/3/27
 */
public class CalculateNum {
    private final int left = 0;
    private final int right = 0;

    @Tool("这是一个用于计算给定的两个数字相加的方法")
    public int calculateNumAdd(@P("被加的数字") int left, @P("需要加上的数字") int right) {
        return left + right;
    }

}
