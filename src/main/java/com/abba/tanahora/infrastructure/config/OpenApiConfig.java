package com.abba.tanahora.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TaNaHora API",
                version = "v1",
                description = "API de operacao e mensageria do TaNaHora. Endpoints de backoffice em /backoffice."
        ),
        tags = {
                @Tag(name = "Backoffice", description = "Operacoes administrativas para suporte")
        }
)
public class OpenApiConfig {
}
