package com.reforma.domain.ml;

import com.reforma.domain.config.ReformaProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Configura el {@link RestClient} hacia api-ml con timeouts cortos (RNF-PERF-004: anomalías < 1 s). */
@Configuration
public class MlClientConfig {

    private static final Duration TIMEOUT = Duration.ofMillis(800);

    @Bean
    RestClient mlRestClient(RestClient.Builder builder, ReformaProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        // clone(): no mutar el builder compartido (conserva los message converters de la app).
        return builder.clone()
                .baseUrl(properties.ml().baseUrl())
                .requestFactory(factory)
                .build();
    }
}
