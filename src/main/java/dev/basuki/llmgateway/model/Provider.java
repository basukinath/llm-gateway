package dev.basuki.llmgateway.model;

public enum Provider {
    CLAUDE("claude"),
    OPENAI("openai");

    private final String id;

    Provider(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Provider fromId(String id) {
        for (Provider p : values()) {
            if (p.id.equalsIgnoreCase(id)) return p;
        }
        throw new IllegalArgumentException("Unknown provider: " + id);
    }
}
