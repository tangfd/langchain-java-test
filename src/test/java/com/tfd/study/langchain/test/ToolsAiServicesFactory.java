package com.tfd.study.langchain.test;

import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.services.AiServicesFactory;

/**
 * @author TangFD 2024/3/27
 */
public class ToolsAiServicesFactory implements AiServicesFactory {
    @Override
    public <T> AiServices<T> create(AiServiceContext context) {
        return new ToolsAiServices<>(context);
    }
}
