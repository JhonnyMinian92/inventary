package com.ecuapp.inventary.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
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
  // El esquema expone solo lecturas del catálogo público. La profundidad no limita el fan-out por alias,
  // por eso también se acota la complejidad total (cada campo cuenta 1).
  return builder -> builder.configureGraphQl(graphQl ->
   graphQl.instrumentation(new ChainedInstrumentation(List.of(new MaxQueryDepthInstrumentation(5),new MaxQueryComplexityInstrumentation(200)))));
 }
}
