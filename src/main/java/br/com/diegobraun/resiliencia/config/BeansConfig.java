package br.com.diegobraun.resiliencia.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import br.com.diegobraun.resiliencia.padroes.Dormidor;
import br.com.diegobraun.resiliencia.padroes.RetryBudget;
import br.com.diegobraun.resiliencia.padroes.RetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HttpClient httpClient(AppProperties props) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(props.core().connectTimeoutMs()))
                .build();
    }

    @Bean
    RetryBudget retryBudget(AppProperties props) {
        return new RetryBudget(props.retry().budgetRazao(), props.retry().budgetMaximo());
    }

    @Bean
    RetryPolicy retryPolicy(AppProperties props, RetryBudget budget) {
        AppProperties.Retry r = props.retry();
        return new RetryPolicy(
                r.maxTentativas(),
                r.backoffBaseMs(),
                r.backoffTetoMs(),
                budget,
                limite -> ThreadLocalRandom.current().nextLong(limite),
                Dormidor.real());
    }
}
