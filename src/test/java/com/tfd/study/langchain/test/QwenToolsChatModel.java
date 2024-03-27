package com.tfd.study.langchain.test;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.dashscope.utils.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenModelName;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class QwenToolsChatModel implements ChatLanguageModel {
    private final String apiKey;
    private final String modelName;
    private final Double topP;
    private final Integer topK;
    private final Boolean enableSearch;
    private final Integer seed;
    private final Float repetitionPenalty;
    private final Float temperature;
    private final List<String> stops;
    private final Integer maxTokens;
    private final Generation generation;
    private final MultiModalConversation conv;
    private final boolean isMultimodalModel;
    private final Map<ToolSpecification, Class<?>> specificationClassMap = new HashMap<>();
    private final Map<ToolSpecification, ToolFunction> toolFunctionMap = new HashMap<>();
    private static final SchemaGenerator generator = new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                    .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                    .without(Option.FLATTENED_ENUMS_FROM_TOSTRING).build());

    @Builder
    protected QwenToolsChatModel(String baseUrl,
                                 String apiKey,
                                 String modelName,
                                 Double topP,
                                 Integer topK,
                                 Boolean enableSearch,
                                 Integer seed,
                                 Float repetitionPenalty,
                                 Float temperature,
                                 List<String> stops,
                                 Integer maxTokens) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_PLUS : modelName;
        this.enableSearch = enableSearch != null && enableSearch;
        this.apiKey = apiKey;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.repetitionPenalty = repetitionPenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.maxTokens = maxTokens;
        this.isMultimodalModel = QwenHelper.isMultimodalModel(modelName);

        if (Utils.isNullOrBlank(baseUrl)) {
            this.conv = isMultimodalModel ? new MultiModalConversation() : null;
            this.generation = isMultimodalModel ? null : new Generation();
        } else if (baseUrl.startsWith("wss://")) {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.HTTP.getValue(), baseUrl);
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return isMultimodalModel ? generateByMultimodalModel(messages) : generateByNonMultimodalModel(messages);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, Collections.singletonList(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        if (CollectionUtils.isEmpty(toolSpecifications)) {
            return generate(messages);
        }

        List<ToolBase> tools = new ArrayList<>(toolSpecifications.size());
        for (ToolSpecification toolSpecification : toolSpecifications) {
            ToolFunction toolFunction = toolFunctionMap.computeIfAbsent(toolSpecification, this::toToolFunction);
            if (toolFunction != null) {
                tools.add(toolFunction);
            }
        }

        if (CollectionUtils.isEmpty(tools)) {
            return generateByNonMultimodalModel(messages);
        }

        return generateByNonMultimodalModel(messages, tools);
    }

    private Response<AiMessage> generateByNonMultimodalModel(List<ChatMessage> messages, List<ToolBase> tools) {
        try {
            GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .repetitionPenalty(repetitionPenalty)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .messages(QwenHelper.toQwenMessages(messages))
                    .tools(tools)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            GenerationResult generationResult = generation.call(builder.build());
            List<ToolExecutionRequest> toolExecutionRequests = QwenHelper.functionFrom(generationResult);
            if (!CollectionUtils.isEmpty(toolExecutionRequests)) {
                return Response.from(AiMessage.from(toolExecutionRequests),
                        QwenHelper.tokenUsageFrom(generationResult),
                        QwenHelper.finishReasonFrom(generationResult));
            }

            String answer = QwenHelper.answerFrom(generationResult);
            return Response.from(AiMessage.from(answer),
                    QwenHelper.tokenUsageFrom(generationResult),
                    QwenHelper.finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    private Response<AiMessage> generateByNonMultimodalModel(List<ChatMessage> messages) {
        try {
            GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .repetitionPenalty(repetitionPenalty)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .messages(QwenHelper.toQwenMessages(messages))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            GenerationResult generationResult = generation.call(builder.build());
            String answer = QwenHelper.answerFrom(generationResult);

            return Response.from(AiMessage.from(answer),
                    QwenHelper.tokenUsageFrom(generationResult),
                    QwenHelper.finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    private Response<AiMessage> generateByMultimodalModel(List<ChatMessage> messages) {
        try {
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .temperature(temperature)
                    .maxLength(maxTokens)
                    .messages(QwenHelper.toQwenMultiModalMessages(messages))
                    .build();

            MultiModalConversationResult result = conv.call(param);
            String answer = QwenHelper.answerFrom(result);

            return Response.from(AiMessage.from(answer),
                    QwenHelper.tokenUsageFrom(result),
                    QwenHelper.finishReasonFrom(result));
        } catch (NoApiKeyException | UploadFileException e) {
            throw new RuntimeException(e);
        }
    }

    private ToolFunction toToolFunction(ToolSpecification toolSpecification) {

        Class<?> aClass = specificationClassMap.get(toolSpecification);
        if (aClass == null) {
            return null;
        }
        // generate jsonSchema of function.
        ObjectNode jsonSchema = generator.generateSchema(aClass);
        // call with tools of function call, jsonSchema.toString() is jsonschema String.
        FunctionDefinition definition = FunctionDefinition.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(JsonUtils.parseString(jsonSchema.toString()).getAsJsonObject())
                .build();
        return ToolFunction.builder()
                .function(definition)
                .build();
    }

    public void addSpecificationClass(ToolSpecification tool, Class<?> clazz) {
        specificationClassMap.put(tool, clazz);
    }
}
