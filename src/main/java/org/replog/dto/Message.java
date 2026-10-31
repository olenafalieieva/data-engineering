package org.replog.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private final Integer id;
    private final String content;
    private final int writeConcern;

    @JsonCreator
    public Message(@JsonProperty("id") Integer id,
                   @JsonProperty("content") String content,
                   @JsonProperty("writeConcern") int writeConcern) {
        this.id = id;
        this.content = content;
        this.writeConcern = writeConcern;
    }

    public Integer getId() { return id; }
    public String getContent() { return content; }
    public int getWriteConcern() { return writeConcern; }

    @Override
    public String toString() {
        return "Message{id='" + id + "', content='" + content + "', write concern=" + writeConcern + '}';
    }
}