package com.abba.tanahora.application.messaging.classifier;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.application.service.OpenAiApiService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenAiMessageClassifier implements MessageClassifier {

    private static final String CLASSIFICATION_KEY = "classification";

    private final OpenAiApiService openAiApiService;
    private final Map<String, AiMessageProcessorDto> classificationCache = new ConcurrentHashMap<>();

    public OpenAiMessageClassifier(OpenAiApiService openAiApiService) {
        this.openAiApiService = openAiApiService;
    }

    @Override
    public AiMessageProcessorDto classify(AIMessage message, FlowState state) {
        String messageHash = this.messageHash(message.getBody());
        AiMessageProcessorDto cachedByHash = classificationCache.get(messageHash);
        if (cachedByHash != null) {
            state.getContext().put(CLASSIFICATION_KEY, cachedByHash);
            return cachedByHash;
        }

        AiMessageProcessorDto dto = this.iaClassify(message);
        if (dto != null) {
            AiMessageProcessorDto existing = classificationCache.putIfAbsent(messageHash, dto);
            AiMessageProcessorDto valueToUse = existing != null ? existing : dto;
            state.getContext().put(CLASSIFICATION_KEY, valueToUse);
            return valueToUse;
        }
        return null;
    }

    private String messageHash(String body) {
        String normalizedBody = Objects.toString(body, "").trim().toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private AiMessageProcessorDto iaClassify(AIMessage message) {
        String prompt = """
                Voce é um parser de agendamentos de medicamentos que entende mensagens contendo medicamento, dose,
                frequencia e data de inicio e fim.
                
                Analise a seguinte mensagem:
                
                %s
                
                Retorne seguindo o padrão indicado.
                Para o campo 'dosage', informe a quantidade do medicamento a ser tomada,
                se não houver essa informacao na mensagem, retorne 'não informado'.
                Para o campo 'patientName', informe o nome do paciente quando houver
                (ex: "para Maria"), caso contrario retorne 'não informado'.
                
                O type do retorno deve ser inferido de acordo com a mensagem recebida;
                Por exemplo:
                 - se a mensagem for uma saudação, o type deve ser WELCOME
                 - se a mensagem for um lembrete de medicamento, o type deve ser REMINDER_CREATION
                 - se a mensagem for uma resposta positiva de um lembrete de medicamento (tomei, ok, tudo certo, etc), o type deve ser REMINDER_RESPONSE_TAKEN
                 - se a mensagem for uma resposta negativa de um lembrete de medicamento (não tomei, não vou tomar, esqueci, etc), o type deve ser REMINDER_RESPONSE_SKIPPED
                 - se a mensagem for um adiamento de lembrete (adiar, depois, mais tarde, etc), o type deve ser REMINDER_RESPONSE_SNOOZED
                 - se a mensagem for um cancelamento de um lembrete de medicamento, o type deve ser REMINDER_CANCEL
                 - se a mensagem for uma pergunta sobre quando é o próximo lembrete, o type deve ser CHECK_NEXT_DISPATCH
                 - se a mensagem for uma mensagem de suporte, o type deve ser SUPPORT
                 - se a mensagem for solicitando upgrade ou downgrade do plano, o type deve ser PLAN_UPGRADE ou PLAN_DOWNGRADE
                 - se a mensagem for para consultar o status/informacoes do plano, o type deve ser PLAN_INFO
                
                A RRULE deve seguir o padrão iCalendar. Por exemplo: FREQ=DAILY;INTERVAL=1;UNTIL=20260213T000000Z
                
                Quando a frequencia mencionar N vezes ao dia, crie uma frequencia ideal.
                Quando a frequencia mencionar 'após as refeições', utilize os horários 7:30, 13:00 e 20:00, repetindo todos os dias.
                
                Hojé é %s.
                
                """;
        return openAiApiService.sendPrompt(String.format(prompt, message.getBody(), OffsetDateTime.now()), AiMessageProcessorDto.class);
    }
}
