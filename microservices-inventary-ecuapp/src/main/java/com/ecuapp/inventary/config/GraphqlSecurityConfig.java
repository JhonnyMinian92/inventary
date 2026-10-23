package com.ecuapp.inventary.config;

import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class GraphqlSecurityConfig {
 @Bean
 GraphQlSourceBuilderCustomizer graphQlDepthLimit() {
  // El esquema expone solo lecturas del catálogo público; el límite protege futuras extensiones accidentales.
  return builder -> builder.configureGraphQl(graphQl ->
   graphQl.instrumentation(new ChainedInstrumentation(List.of(new MaxQueryDepthInstrumentation(5)))));
 }
}
