package org.replog.dto;

import java.util.logging.Logger;

public class AppendResponse {
    private static final Logger logger = Logger.getLogger(AppendResponse.class.getName());

    private final boolean success;
    private final Integer messageId;
    private final int confirmations;
    private final String error;

    public AppendResponse(boolean success, Integer messageId, int confirmations, String error) {
        this.success = success;
        this.messageId = messageId;
        this.confirmations = confirmations;
        this.error = error;
    }

    public static AppendResponse success(Integer messageId, int confirmations) {
        logger.info("[AppendResponse] success: messageId=" + messageId
                + ", confirmations=" + confirmations);
        return new AppendResponse(true, messageId, confirmations, null);
    }

    public static AppendResponse failure(String error) {
        logger.warning("[AppendResponse] failure: " + error);
        return new AppendResponse(false, null, 0, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public String getError() {
        return error;
    }
}
