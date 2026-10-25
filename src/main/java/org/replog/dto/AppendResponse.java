package org.replog.dto;

public class AppendResponse {
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
        System.out.println("[AppendResponse] success: messageId=" + messageId
                + ", confirmations=" + confirmations);
        return new AppendResponse(true, messageId, confirmations, null);
    }

    public static AppendResponse failure(String error) {
        System.err.println("[AppendResponse] failure: " + error);
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
