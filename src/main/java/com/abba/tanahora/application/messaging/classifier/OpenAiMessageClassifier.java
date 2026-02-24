package com.abba.tanahora.application.messaging.classifier;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.service.OpenAiApiService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OpenAiMessageClassifier implements MessageClassifier {

    private final OpenAiApiService openAiApiService;
    private final Map<String, AiMessageProcessorDto> classificationCache = new ConcurrentHashMap<>();

    @Override
    public AiMessageProcessorDto classify(AIMessage message) {
        String messageHash = this.messageHash(message.getBody());
        AiMessageProcessorDto cachedByHash = classificationCache.get(messageHash);
        if (cachedByHash != null) {
            return cachedByHash;
        }

        AiMessageProcessorDto dto = this.iaClassify(message);
        if (dto != null) {
            AiMessageProcessorDto existing = classificationCache.putIfAbsent(messageHash, dto);
            return existing != null ? existing : dto;
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
                Voce e um parser de agendamentos de medicamentos que entende mensagens contendo medicamento, dose,
                frequencia e data de inicio e fim.
                
                Analise a seguinte mensagem.
                
                %s
                
                Retorne seguindo o padrao indicado.
                Para o campo 'dosage', informe a quantidade do medicamento a ser tomada,
                se não houver essa informacao na mensagem, retorne 'não informado'.
                Para o campo 'patientName', informe o nome do paciente quando houver
                (ex: "para Maria"), caso contrario retorne 'não informado'.
                
                O type do retorno deve ser inferido de acordo com a mensagem recebida.
                Por exemplo:
                 - se a mensagem for uma saudacao, o type deve ser WELCOME
                 - se a mensagem for um lembrete de medicamento, o type deve ser REMINDER_CREATION
                 - se a mensagem for uma resposta positiva de um lembrete de medicamento (tomei, ok, tudo certo, etc), o type deve ser REMINDER_RESPONSE_TAKEN
                 - se a mensagem for uma resposta negativa de um lembrete de medicamento (não tomei, não vou tomar, esqueci, etc), o type deve ser REMINDER_RESPONSE_SKIPPED
                 - se a mensagem for um adiamento de lembrete (adiar, depois, mais tarde, etc), o type deve ser REMINDER_RESPONSE_SNOOZED
                 - se a mensagem for um cancelamento de um lembrete de medicamento, o type deve ser REMINDER_CANCEL
                 - se a mensagem for uma pergunta sobre quando e o proximo lembrete, o type deve ser CHECK_NEXT_DISPATCH
                 - se a mensagem for uma mensagem de suporte, o type deve ser SUPPORT
                 - se a mensagem for solicitando upgrade ou downgrade do plano, o type deve ser PLAN_UPGRADE ou PLAN_DOWNGRADE
                 - se a mensagem for para consultar o status/informacoes do plano, o type deve ser PLAN_INFO
                 - se a mensagem for um pedido para checar o histórico de lembretes, o type deve ser CHECK_HISTORY
                
                A RRULE deve seguir o padrao iCalendar. Exemplo: FREQ=DAILY;INTERVAL=1;UNTIL=20260213T000000Z
                Regras obrigatorias para RRULE valida:
                - Retorne somente o conteudo da regra, sem prefixo "RRULE:"
                - BYHOUR aceita apenas valores de 0 a 23 (nunca use 24)
                - BYMINUTE aceita apenas 0 a 59
                - BYSECOND aceita apenas 0 a 59
                - Se usar FREQ=HOURLY;INTERVAL=N, não use BYHOUR, BYMINUTE ou BYSECOND na mesma regra
                - Se for "a cada 8 horas", use FREQ=HOURLY;INTERVAL=8 (sem BYHOUR/BYMINUTE/BYSECOND)
                - Se for "a cada 12 horas", use FREQ=HOURLY;INTERVAL=12 (sem BYHOUR/BYMINUTE/BYSECOND)
                - Se quiser horarios fixos no dia (ex: 00:00, 08:00, 16:00), use FREQ=DAILY com BYHOUR/BYMINUTE/BYSECOND e não use FREQ=HOURLY
                - Se usar UNTIL, use formato UTC basico: yyyyMMdd'T'HHmmss'Z'
                Exemplos seguros:
                - FREQ=HOURLY;INTERVAL=8;UNTIL=20260221T235959Z
                - FREQ=DAILY;BYHOUR=0,8,16;BYMINUTE=0;BYSECOND=0;UNTIL=20260221T235959Z
                
                Quando a frequencia mencionar N vezes ao dia, crie uma frequencia ideal.
                Quando a frequencia mencionar 'apos as refeicoes', utilize os horarios 7:30, 13:00 e 20:00, repetindo todos os dias.
                
                Hoje e %s.
                
                """;
        return openAiApiService.sendPrompt(String.format(prompt, message.getBody(), OffsetDateTime.now()), AiMessageProcessorDto.class);
    }
}
