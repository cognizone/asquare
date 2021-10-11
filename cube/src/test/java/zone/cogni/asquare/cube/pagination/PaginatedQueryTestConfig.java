package zone.cogni.asquare.cube.pagination;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaginatedQueryTestConfig {

  @Bean
  PaginatedQuery paginatedQuery() {
    return new PaginatedQuery(20);
  }
}
