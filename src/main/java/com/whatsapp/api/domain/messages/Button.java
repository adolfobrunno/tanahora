package com.whatsapp.api.domain.messages;

import com.whatsapp.api.domain.messages.type.ButtonType;

public class Button {

    private ButtonType type;
    private Reply reply;

    public ButtonType getType() {
        return type;
    }

    public Button setType(ButtonType type) {
        this.type = type;
        return this;
    }

    public Reply getReply() {
        return reply;
    }

    public Button setReply(Reply reply) {
        this.reply = reply;
        return this;
    }
}
