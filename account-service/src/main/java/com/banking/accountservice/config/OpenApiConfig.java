package com.banking.accountservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Account Service API")
                        .version("1.0")
                        .description("Manages customer accounts, balances, blocking, deposits and compensating credit/deduct operations."))
                .servers(List.of(new Server().url("http://localhost:8080").description("API Gateway")))
                .tags(List.of(new Tag()
                        .name("AccountController")
                        .description("Create and manage bank accounts, balances, account blocking, and SAGA balance operations.")));
    }

    @Bean
    public OpenApiCustomizer accountOperationCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            operation.setTags(List.of("AccountController"));
            if (operation.getDescription() == null) {
                operation.setDescription(accountOperationDescription(path, operation.getOperationId()));
            }
        }));
    }

    private String accountOperationDescription(String path, String operationId) {
        if (path.endsWith("/balance")) return "Retrieve the current balance for an account.";
        if (path.endsWith("/block")) return "Block an account and prevent further account operations.";
        if (path.endsWith("/deduct")) return "SAGA step: deduct an amount from the sender/receiver account.";
        if (path.endsWith("/credit")) return "SAGA compensation: credit an amount back to or into an account.";
        if (operationId != null && operationId.contains("createAccount")) return "Create a new bank account.";
        if (operationId != null && operationId.contains("getAccount")) return "Retrieve account details by account number.";
        return "Account service operation: " + path;
    }
}
