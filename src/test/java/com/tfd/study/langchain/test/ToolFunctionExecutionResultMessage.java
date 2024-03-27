package com.tfd.study.langchain.test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.Getter;

/**
 * @author TangFD 2024/3/27
 */
@Getter
public class ToolFunctionExecutionResultMessage extends ToolExecutionResultMessage {

    private final ToolExecutionRequest request;

    /**
     * Creates a {@link ToolExecutionResultMessage}.
     *
     * @param id       the id of the tool.
     * @param toolName the name of the tool.
     * @param text     the result of the tool execution.
     */
    public ToolFunctionExecutionResultMessage(String id, String toolName, String text, ToolExecutionRequest request) {
        super(id, toolName, text);
        this.request = request;
    }

    public static ToolExecutionResultMessage from(ToolExecutionRequest request, String toolExecutionResult) {
        return new ToolFunctionExecutionResultMessage(request.id(), request.name(), toolExecutionResult, request);
    }

}
