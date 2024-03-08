package com.tfd.study.langchain.test;

import com.alibaba.dashscope.aigc.conversation.Conversation;
import com.alibaba.dashscope.aigc.conversation.ConversationParam;
import com.alibaba.dashscope.aigc.conversation.ConversationResult;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MessageManager;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import io.reactivex.Flowable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

//@SpringBootTest
class LangchainJavaTestApplicationTests {

    private static String model;

    @BeforeAll
    public static void before() {
        Constants.apiKey = "sk-ea3e6dbe81f94b4b9cad85270d83553d";
        model = Conversation.Models.QWEN_MAX;
    }

    @Test
    void contextLoads() throws Exception {
        Generation gen = new Generation();
        MessageManager msgManager = new MessageManager(10);
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("我需要在系统中开发一个插件功能，并需要将插件化的能力开放给所有用户，现在需要先定义插件的SDK标准，SDK中包含对插件的检查，业务逻辑的处理等功能。请使用java生成SDK相关的代码。")
                .build();
        msgManager.add(systemMsg);
        msgManager.add(userMsg);
        QwenParam param = QwenParam.builder()
                .model(model)
                .messages(msgManager.get())
                .resultFormat(QwenParam.ResultFormat.MESSAGE)
                .temperature(0.9f)
                .build();
        GenerationResult result = gen.call(param);
        System.out.println(result.getUsage());
        System.out.println(result.getOutput());
    }

    @Test
    public void streamTest() {
        Conversation conversation = new Conversation();
        String prompt = "就当前中国的银行存款利率太低的情况，写一份怒批银行不公的倡议书，需要有理有据地号召大家克制进入银行";
        ConversationParam param = ConversationParam
                .builder()
                .model(model)
                .temperature(0.5f)
                .prompt(prompt)
                .build();
        try {
            Flowable<ConversationResult> result = conversation.streamCall(param);
            result.blockingForEach(msg -> {
                System.out.print(msg.getOutput().getText());
            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Test
    public void basicCall() throws Exception {
        OkHttpClient client = new OkHttpClient();
        String model = "stable-diffusion-xl";
        String prompt = "请给我画一位坚强且富有现代魅力感的性感女性，她立于高楼之巅，身着彰显曲线美的优雅服饰，面部洋溢着从容不迫的自信神态，双手有力地摆出代表决心与力量的姿势，以此体现现代女性的独立与毅力。";
        String size = "1024*1024";

        ImageSynthesis is = new ImageSynthesis();
        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .model(model)
                        .n(1)
                        .size(size)
                        .prompt(prompt)
                        .negativePrompt("garfield")
                        .build();

        ImageSynthesisResult result = is.call(param);
        System.out.println(result);
        // save image to local files.
        loadImg(client, result);
    }

    @Test
    public void imageSynthesis() throws Exception {
        OkHttpClient client = new OkHttpClient();
        String prompt = "请给我画一位魅惑感的十足的性感女性，她立于高楼之巅，身着彰显曲线美的优雅服饰，面部洋溢着从容不迫的自信神态，双手有力地摆出代表决心与力量的姿势，以此体现现代女性的独立与毅力。";
        String size = "1024*1024";
        ImageSynthesis is = new ImageSynthesis();
        ImageSynthesisParam param =
                ImageSynthesisParam.builder()
                        .model(ImageSynthesis.Models.WANX_V1)
                        .n(4)
                        .size(size)
                        .prompt(prompt)
                        .build();

        ImageSynthesisResult result = is.call(param);
        System.out.println(result);
        // save image to local files.
        loadImg(client, result);
    }

    private void loadImg(OkHttpClient client, ImageSynthesisResult result) throws IOException {
        for (Map<String, String> item : result.getOutput().getResults()) {
            String paths = new URL(item.get("url")).getPath();
            String[] parts = paths.split("/");
            String fileName = parts[parts.length - 1];
            Request request = new Request.Builder()
                    .url(item.get("url"))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                Path file = Paths.get(fileName);
                Files.write(file, response.body().bytes());
            }

        }
    }

}
