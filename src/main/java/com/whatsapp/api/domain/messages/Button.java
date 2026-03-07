package com.whatsapp.api.domain.messages;

import com.whatsapp.api.domain.messages.type.ButtonType;
import lombok.Getter;

@Getter
public class Button {

    private ButtonType type;
    private Reply reply;

    public Button setType(ButtonType type) {
        this.type = type;
        return this;
    }

    public Button setReply(Reply reply) {
        this.reply = reply;
        return this;
    }
}
