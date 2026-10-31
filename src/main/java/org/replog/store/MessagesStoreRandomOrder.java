package org.replog.store;

import org.replog.dto.Message;
import org.replog.store.Interface.MessagesStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessagesStoreRandomOrder implements MessagesStore {
    private final CopyOnWriteArrayList<Message> messages = new CopyOnWriteArrayList<>();

    @Override
    public Integer append(String content, int writeConcern) {
        Integer id = generateId();
        Message message = new Message(id, content, writeConcern);
        messages.add(message);
        return id;
    }

    @Override
    public Integer generateId() {
        return new Random().nextInt();
    }

    @Override
    public void appendMsg(Message message) {
        messages.add(message);
    }

    @Override
    public List<Message> getAll() {
        return new ArrayList<>(messages);
    }

    @Override
    public int size() {
        return messages.size();
    }

    @Override
    public void clear() {
        messages.clear();
    }
}
