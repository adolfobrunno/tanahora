package com.abba.tanahora.support;

import org.bson.Document;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

public class MongoCollectionsCleanupExtension implements BeforeEachCallback, AfterEachCallback {

    private static final List<String> COLLECTIONS = List.of("reminder_events", "reminders", "users");

    @Override
    public void beforeEach(ExtensionContext context) {
        cleanup(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cleanup(context);
    }

    private void cleanup(ExtensionContext context) {
        MongoTemplate mongoTemplate = SpringExtension.getApplicationContext(context).getBean(MongoTemplate.class);
        for (String collection : COLLECTIONS) {
            mongoTemplate.getCollection(collection).deleteMany(new Document());
        }
    }
}
