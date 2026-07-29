package com.hs.notification.service.attachment;

public record AttachmentResult(boolean success, byte[] bytes, String filename, String contentType, String failureReason) {
    public static AttachmentResult success(byte[] bytes, String filename, String contentType) {
        return new AttachmentResult(true, bytes, filename, contentType, null);
    }

    public static AttachmentResult failure(String reason) {
        return new AttachmentResult(false, null, null, null, reason);
    }
}
