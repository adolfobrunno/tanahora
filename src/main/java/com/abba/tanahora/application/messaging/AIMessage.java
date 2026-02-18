package com.abba.tanahora.application.messaging;

import com.abba.tanahora.domain.model.MessageReceived;
import lombok.Data;

@Data
public class AIMessage {

    private String id;
    private String whatsappId;
    private String contactName;
    private String messageType;
    private String body;
    private String interactiveButtonId;
    private String replyToId;
    private String mediaId;
    private String mediaMimeType;
    private String mediaFilename;
    private String mediaSha256;

    public static AIMessage from(MessageReceived messageReceived) {
        AIMessage message = new AIMessage();
        message.setId(messageReceived.getId());
        message.setWhatsappId(messageReceived.getWhatsappId());
        message.setContactName(messageReceived.getContactName());
        message.setMessageType(messageReceived.getMessageType());
        message.setBody(messageReceived.getMessage());
        message.setInteractiveButtonId(messageReceived.getInteractiveButtonId());
        message.setReplyToId(messageReceived.getRepliedTo());
        message.setMediaId(messageReceived.getMediaId());
        message.setMediaMimeType(messageReceived.getMediaMimeType());
        message.setMediaFilename(messageReceived.getMediaFilename());
        message.setMediaSha256(messageReceived.getMediaSha256());
        return message;
    }
}
