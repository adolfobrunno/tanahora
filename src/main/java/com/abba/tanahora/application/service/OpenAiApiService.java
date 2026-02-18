package com.abba.tanahora.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiApiService {

    private final OpenAiChatModel openAiChatModel;

    public <T> T sendPrompt(String stringPrompt, Class<T> schema) {

        log.debug("Sending prompt to OpenAI: {}", stringPrompt);

        var converter = new BeanOutputConverter<>(schema);
        String jsonSchema = converter.getJsonSchema();

        var prompt = new Prompt(
                stringPrompt,
                OpenAiChatOptions.builder()
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                        .build()
        );

        var response = openAiChatModel.call(prompt);

        String responseText = response.getResult().getOutput().getText();

        log.debug("Response: {}", responseText);

        return converter.convert(Objects.requireNonNull(responseText));
    }

    public <T> T sendPromptWithMedia(String stringPrompt, byte[] mediaBytes, String mimeType, Class<T> schema) {
        if (mediaBytes == null || mediaBytes.length == 0) {
            throw new IllegalArgumentException("mediaBytes cannot be empty");
        }

        var converter = new BeanOutputConverter<>(schema);
        String jsonSchema = converter.getJsonSchema();

        MimeType mediaMimeType = parseMimeType(mimeType);
        UserMessage userMessage = UserMessage.builder()
                .text(stringPrompt)
                .media(new Media(mediaMimeType, new ByteArrayResource(mediaBytes)))
                .build();

        var prompt = new Prompt(
                List.of(userMessage),
                OpenAiChatOptions.builder()
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                        .build()
        );

        var response = openAiChatModel.call(prompt);
        String responseText = response.getResult().getOutput().getText();
        return converter.convert(Objects.requireNonNull(responseText));
    }

    private MimeType parseMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        try {
            return MimeTypeUtils.parseMimeType(mimeType);
        } catch (Exception ignored) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
    }


}

