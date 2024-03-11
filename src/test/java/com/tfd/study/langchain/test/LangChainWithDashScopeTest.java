package com.tfd.study.langchain.test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

/**
 * @author TangFD 2024/3/11
 */
public class LangChainWithDashScopeTest {
    private static ChatLanguageModel model;

    @BeforeAll
    public static void before() {
        model = QwenChatModel.builder()
                .apiKey("sk-ea3e6dbe81f94b4b9cad85270d83553d")
                .modelName("qwen-72b-chat")
//                .temperature(1.9f)
                .build();
    }

    @Test
    public void testChatLanguageModel() {
        UserMessage firstUserMessage = UserMessage.from("你是一个非常资深的系统架构师。我需要在系统中开发一个插件功能，并需要将插件化的能力开放给所有用户，现在需要先定义插件的SDK标准，SDK中包含对插件的检查，业务逻辑的处理等功能。请使用java生成SDK相关的代码。");
        Response<AiMessage> generate = model.generate(firstUserMessage);
        System.out.println(generate);
        AiMessage firstAiMessage = generate.content();
        UserMessage secondUserMessage = UserMessage.from("对于以上内容，再次进行优化重构。");
        Response<AiMessage> response = model.generate(firstUserMessage, firstAiMessage, secondUserMessage);
        System.out.println(response);
    }


    @Test
    public void testTools() {
        UserMessage firstUserMessage = UserMessage.from("你是一个计算天才，现在请计算一下“离开多数据库法律上JFK介绍了地方”和“开始了地方还是老地方海上分列式警方很快”这两句话中共有多少个字？");
        Response<AiMessage> generate = model.generate(firstUserMessage);
        System.out.println(generate);
    }

    interface Assistant {
        String chat(String message);
    }

    @Test
    public void testChatMemory() {
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();

        Scanner scanner = new Scanner(System.in);
        System.out.print("User: ");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String answer = assistant.chat(line);
            System.out.print("Answer: ");
            System.out.println(answer);
            System.out.println();
            System.out.print("User: ");
        }
    }

}
