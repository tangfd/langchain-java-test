package dev.langchain4j.data.message;

public enum ContentType {
    TEXT(TextContent.class),
    IMAGE(ImageContent.class);

    private final Class<? extends Content> contentClass;

    private ContentType(Class<? extends Content> contentClass) {
        this.contentClass = contentClass;
    }

    public Class<? extends Content> getContentClass() {
        return this.contentClass;
    }
}
