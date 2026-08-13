package pl.dybcio.ordered.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservationConfig {

  @Bean
  public ObservationPredicate noActuatorTraces() {
    return (name, context) -> {
      if (context instanceof ServerRequestObservationContext serverContext) {
        return !serverContext.getCarrier().getRequestURI().startsWith("/actuator");
      }
      return true;
    };
  }
}
