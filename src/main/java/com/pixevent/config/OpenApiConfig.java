package com.pixevent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Value("${app.base-url:http://localhost:3001}")
    private String baseUrl;

    @Bean
    public OpenAPI pixEventOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PixEvent API")
                        .version("1.0.0")
                        .description("""
                                API REST para venda de ingressos e reserva de mesas com pagamento via PIX, \
                                ingresso digital com QR Code e validação na portaria.

                                **Autenticação do admin:** faça `POST /api/admin/login` com a senha, copie o \
                                `token` retornado e clique em **Authorize** informando apenas o token \
                                (o prefixo `Bearer` é adicionado automaticamente).""")
                        .contact(new Contact().name("PixEvent")))
                .addServersItem(new Server().url(baseUrl).description("Servidor da aplicação"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("HMAC-SHA256 (token próprio)")
                                .description("Token obtido em POST /api/admin/login")));
    }
}
