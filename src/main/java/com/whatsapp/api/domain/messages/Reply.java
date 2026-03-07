package com.whatsapp.api.domain.messages;

import lombok.Getter;

@Getter
public class Reply {

    private String id;
    private String title;

    public Reply setId(String id) {
        this.id = id;
        return this;
    }

    public Reply setTitle(String title) {
        this.title = title;
        return this;
    }
}
