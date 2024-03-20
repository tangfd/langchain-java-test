package com.tfd.study.langchain.test;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

/**
 * @author TangFD 2024/3/11
 */
public class RagWithQueryCompressionWithDashScopeTest {
    private static ChatLanguageModel model;

    @BeforeAll
    public static void before() {
        model = QwenChatModel.builder()
                .apiKey("sk-ea3e6dbe81f94b4b9cad85270d83553d")
                .modelName("qwen-72b-chat")
                .build();
    }

    @Test
    public void testRag() {
        Biographer biographer = createBiographer();

        // First, ask "What is the legacy of John Doe?"
        // Then, ask "When was he born?"
        // Now, review the logs:
        // The first query was not compressed as there was no preceding context to compress.
        // The second query, however, was compressed into something like "When was John Doe born?"

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n\n\n\n==================================================");
                System.out.print("User: ");
                String userQuery = scanner.nextLine();
                System.out.println("==================================================");

                if ("exit".equalsIgnoreCase(userQuery)) {
                    break;
                }

                String biographerAnswer = biographer.answer(userQuery);
                System.out.println("==================================================\n\n");
                System.out.println("Biographer: " + biographerAnswer);
            }
        }
    }

    public static final PromptTemplate TRANSFORMER_PROMPT_TEMPLATE = PromptTemplate.from(
            "阅读并理解用户(User)和人工智能(AI)之间的对话。然后，分析用户的新查询。从对话和新查询中识别所有相关的细节、术语和上下文。" +
                    "将此查询重新格式化为适用于信息检索的清晰、简洁和自包含的格式.\n" +
                    "\n" +
                    "用户(User)和人工智能(AI)之间的对话:\n" +
                    "{{chatMemory}}\n" +
                    "\n" +
                    "用户(User)的新查询: {{query}}\n" +
                    "\n" +
                    "非常重要的是，您只提供重新制定的查询，而不提供其他查询！不要在查询前添加任何其它内容!"
    );

    public static final PromptTemplate CONTENTINJECTOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "{{userMessage}}\n" +
                    "\n" +
                    "优先使用以下信息进行回答:\n" +
                    "{{contents}}"
    );

    private static Biographer createBiographer() {

        // Check _01_Naive_RAG if you need more details on what is going on here

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        Path documentPath = toPath("ragTest.txt");
        EmbeddingStore<TextSegment> embeddingStore = embed(documentPath, embeddingModel);

        // We will create a CompressingQueryTransformer, which is responsible for compressing
        // the user's query and the preceding conversation into a single, stand-alone query.
        // This should significantly improve the quality of the retrieval process.
        QueryTransformer queryTransformer = new CompressingQueryTransformer(model, TRANSFORMER_PROMPT_TEMPLATE);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();

        // The RetrievalAugmentor serves as the entry point into the RAG flow in LangChain4j.
        // It can be configured to customize the RAG behavior according to your requirements.
        // In subsequent examples, we will explore more customizations.
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .contentInjector(new DefaultContentInjector(CONTENTINJECTOR_PROMPT_TEMPLATE))
                .build();

        return AiServices.builder(Biographer.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private static EmbeddingStore<TextSegment> embed(Path documentPath, EmbeddingModel embeddingModel) {
        DocumentParser documentParser = new TextDocumentParser();
        Document document = FileSystemDocumentLoader.loadDocument(documentPath, documentParser);

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);
        return embeddingStore;
    }

    interface Biographer {

        String answer(String query);
    }

    private static Path toPath(String fileName) {
        return new File("src\\test\\resources\\" + fileName).toPath();
    }

}
