package com.banking.transactionservice.config;

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
    public OpenAPI transactionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Transaction Service API")
                        .version("1.0")
                        .description("Processes account transfers, transaction lookup, transfer history, and OTP verification."))
                .servers(List.of(new Server().url("http://localhost:8080").description("API Gateway")))
                .tags(List.of(new Tag()
                        .name("TransactionController")
                        .description("Execute and track financial transactions, retrieve history, and verify OTP codes.")));
    }

    @Bean
    public OpenApiCustomizer transactionOperationCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            operation.setTags(List.of("TransactionController"));
            operation.setDescription(transactionOperationDescription(path, operation.getOperationId()));

            if (path.endsWith("/{transactionId}")) {
                operation.setParameters(java.util.List.of(
                        new io.swagger.v3.oas.models.parameters.Parameter()
                                .name("transactionId")
                                .in("path")
                                .required(true)
                                .description("Enter the transaction identifier to retrieve its current status and details.")
                                .schema(new io.swagger.v3.oas.models.media.Schema<>().type("string"))));
            }
        }));
    }

    private String transactionOperationDescription(String path, String operationId) {
        if (path.endsWith("/transfer")) return "Initiate a transfer between bank accounts.";
        if (path.contains("/account/")) return "Retrieve transaction history for an account.";
        if (path.endsWith("/verify")) return "Verify the OTP associated with a transaction.";
        if (operationId != null && operationId.contains("getTransaction")) return "Retrieve transaction details by transaction identifier.";
        return "Transaction service operation: " + path;
    }
}
