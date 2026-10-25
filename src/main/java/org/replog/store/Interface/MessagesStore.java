package org.replog.store.Interface;

import org.replog.dto.Message;

import java.util.List;


public interface MessagesStore {
    Integer append(String content, int writeConcern);

    void appendMsg(Message message);

    List<Message> getAll();

    int size();

    void clear();

    Integer generateId();

}
