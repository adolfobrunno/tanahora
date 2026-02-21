package com.abba.tanahora.application.messaging.classifier;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.messaging.AIMessage;

public interface MessageClassifier {

    AiMessageProcessorDto classify(AIMessage message);
}
