package org.replog.dto;

public class Message {
    private final Integer id;
    private final String content;
    private final int writeConcern;

    public Message(Integer id, String content, int writeConcern) {
        this.id = id;
        this.content = content;
        this.writeConcern = writeConcern;
    }

    public Integer getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", write concern=" + writeConcern +
                '}';
    }
}
