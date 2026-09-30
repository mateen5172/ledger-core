package com.example.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LedgerCore API")
                .description("Double-entry ledger and payments engine. All amounts are in minor units "
                        + "(e.g. cents): 2500 means 25.00. Every money-moving call requires an Idempotency-Key header.")
                .version("1.0.0"));
    }
}
