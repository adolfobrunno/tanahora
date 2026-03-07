package com.whatsapp.api.domain.messages;

public class Reply {

    private String id;
    private String title;

    public String getId() {
        return id;
    }

    public Reply setId(String id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Reply setTitle(String title) {
        this.title = title;
        return this;
    }
}
