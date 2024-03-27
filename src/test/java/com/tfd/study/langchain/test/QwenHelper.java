package com.tfd.study.langchain.test;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.dashscope.common.Role.*;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.stream.Collectors.toList;

public class QwenHelper {

    static List<Message> toQwenMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenHelper::toQwenMessage)
//                .filter(m -> StringUtils.isNotEmpty(m.getContent()))
                .collect(toList());
    }

    static List<Message> toQwenMessages(Iterable<ChatMessage> messages) {
        LinkedList<Message> qwenMessages = new LinkedList<>();
        messages.forEach(message -> qwenMessages.add(toQwenMessage(message)));
        return qwenMessages;
    }

    static Message toQwenMessage(ChatMessage message) {
        Message.MessageBuilder<?, ?> builder = Message.builder()
                .role(roleFrom(message))
                .content(toSingleText(message));
        if (ChatMessageType.TOOL_EXECUTION_RESULT.equals(message.type())) {
            builder.toolCallId(((ToolExecutionResultMessage) message).toolName());
        } else if (ChatMessageType.AI.equals(message.type()) && ((AiMessage) message).hasToolExecutionRequests()) {
            builder.toolCalls(((AiMessage) message).toolExecutionRequests().stream()
                    .map(QwenHelper::result2CallBase)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private static ToolCallFunction result2CallBase(ToolExecutionRequest tool) {
        ToolCallFunction toolCallFunction = ToolCallFunction.builder().build();
        ToolCallFunction.CallFunction function = toolCallFunction.new CallFunction();
        function.setName(tool.name());
        function.setArguments(tool.arguments());
        toolCallFunction.setFunction(function);
        return toolCallFunction;
    }

    static String toSingleText(ChatMessage message) {
        switch (message.type()) {
            case USER:
                return ((UserMessage) message).contents()
                        .stream()
                        .filter(TextContent.class::isInstance)
                        .map(TextContent.class::cast)
                        .map(TextContent::text)
                        .collect(Collectors.joining("\n"));
            case AI:
                String text = ((AiMessage) message).text();
                return text == null ? "" : text;
            case SYSTEM:
                return ((SystemMessage) message).text();
            case TOOL_EXECUTION_RESULT:
                return ((ToolExecutionResultMessage) message).text();
            default:
                return "";
        }
    }

    static List<MultiModalMessage> toQwenMultiModalMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(QwenHelper::toQwenMultiModalMessage)
                .collect(toList());
    }

    static MultiModalMessage toQwenMultiModalMessage(ChatMessage message) {
        return MultiModalMessage.builder()
                .role(roleFrom(message))
                .content(toMultiModalContents(message))
                .build();
    }

    static List<Map<String, Object>> toMultiModalContents(ChatMessage message) {
        switch (message.type()) {
            case USER:
                return ((UserMessage) message).contents()
                        .stream()
                        .map(QwenHelper::toMultiModalContent)
                        .collect(Collectors.toList());
            case AI:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((AiMessage) message).text()));
            case SYSTEM:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((SystemMessage) message).text()));
            case TOOL_EXECUTION_RESULT:
                return Collections.singletonList(
                        Collections.singletonMap("text", ((ToolExecutionResultMessage) message).text()));
            default:
                return Collections.emptyList();
        }
    }

    static Map<String, Object> toMultiModalContent(Content content) {
        switch (content.type()) {
            case IMAGE:
                Image image = ((ImageContent) content).image();
                String imageContent;
                if (image.url() != null) {
                    imageContent = image.url().toString();
                    return Collections.singletonMap("image", imageContent);
                } else if (Utils.isNotNullOrBlank(image.base64Data())) {
                    // The dashscope sdk supports local file url: file://...
                    // Using the temporary directory for storing temporary files is a safe practice,
                    // as most operating systems will periodically clean up the contents of this directory
                    // or do so upon system reboot.
                    imageContent = saveImageAsTemporaryFile(image.base64Data(), image.mimeType());

                    // In this case, the dashscope sdk requires a mutable map.
                    HashMap<String, Object> contentMap = new HashMap<>(1);
                    contentMap.put("image", imageContent);
                    return contentMap;
                } else {
                    return Collections.emptyMap();
                }
            case TEXT:
                return Collections.singletonMap("text", ((TextContent) content).text());
            default:
                return Collections.emptyMap();
        }
    }

    private static String saveImageAsTemporaryFile(String base64Data, String mimeType) {
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String tmpImageName = UUID.randomUUID().toString();
        if (Utils.isNotNullOrBlank(mimeType)) {
            // e.g. "image/png", "image/jpeg"...
            int lastSlashIndex = mimeType.lastIndexOf("/");
            if (lastSlashIndex >= 0 && lastSlashIndex < mimeType.length() - 1) {
                String imageSuffix = mimeType.substring(lastSlashIndex + 1);
                tmpImageName = tmpImageName + "." + imageSuffix;
            }
        }

        Path tmpImagePath = Paths.get(tmpDir, tmpImageName);
        byte[] data = Base64.getDecoder().decode(base64Data);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpImagePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpImagePath.toAbsolutePath().toUri().toString();
    }

    static String roleFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ASSISTANT.getValue();
        } else if (message instanceof SystemMessage) {
            return SYSTEM.getValue();
        } else if (message instanceof ToolExecutionResultMessage) {
            return "tool";
        } else {
            return USER.getValue();
        }
    }

    static boolean hasAnswer(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .isPresent();
    }

    static String answerFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getContent)
                // Compatible with some older models.
                .orElseGet(() -> Optional.of(result)
                        .map(GenerationResult::getOutput)
                        .map(GenerationOutput::getText)
                        .orElseThrow(NullPointerException::new));
    }

    static List<ToolExecutionRequest> functionFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)

                .map(Message::getToolCalls)
                .filter(tools -> !CollectionUtils.isEmpty(tools))
                .flatMap((Function<List<ToolCallBase>, Optional<Stream<ToolCallBase>>>) toolCallBases -> Optional.of(toolCallBases.stream()))
                .orElseGet(Stream::of)
                .map(call -> ((ToolCallFunction) call).getFunction())
                .map(callFunction -> ToolExecutionRequest.builder()
                        .name(callFunction.getName())
                        .arguments(callFunction.getArguments())
                        .build())
                // Compatible with some older models.
                .collect(Collectors.toList());
    }

    static boolean hasAnswer(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .isPresent();
    }

    static String answerFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .map(contents -> contents.get(0))
                .map(content -> content.get("text"))
                .map(String.class::cast)
                .orElseThrow(NullPointerException::new);
    }

    static TokenUsage tokenUsageFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static FinishReason finishReasonFrom(GenerationResult result) {
        String finishReason = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getFinishReason)
                .orElse("");

        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            default:
                return null;
        }
    }

    static FinishReason finishReasonFrom(MultiModalConversationResult result) {
        String finishReason = Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getFinishReason)
                .orElse("");

        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            default:
                return null;
        }
    }

    public static boolean isMultimodalModel(String modelName) {
        // for now, multimodal models start with "qwen-vl"
        return modelName.startsWith("qwen-vl");
    }
}
