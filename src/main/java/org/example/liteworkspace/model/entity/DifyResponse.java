package org.example.liteworkspace.model.entity;

public class DifyResponse {
    public String event;
    public String answer;
    public String conversation_id;
    public String message_id;
    public long created_at;
    public String task_id;
    public String id;
    public Metadata metadata;
    public Object files;

    public static class Metadata {
        public Usage usage;
    }

    public String getEvent() {
        return event;
    }

    public String getAnswer() {
        return answer;
    }

    public String getConversation_id() {
        return conversation_id;
    }

    public String getMessage_id() {
        return message_id;
    }

    public long getCreated_at() {
        return created_at;
    }

    public String getTask_id() {
        return task_id;
    }

    public String getId() {
        return id;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Object getFiles() {
        return files;
    }

    public static class Usage {
        public int prompt_tokens;
        public String prompt_unit_price;
        public String prompt_price_unit;
        public String prompt_price;
        public int completion_tokens;
        public String completion_unit_price;
        public String completion_price_unit;
        public String completion_price;
        public int total_tokens;
        public String total_price;
        public String currency;
        public double latency;
    }
}

