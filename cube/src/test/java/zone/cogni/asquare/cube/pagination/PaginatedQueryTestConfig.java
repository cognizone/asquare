package zone.cogni.asquare.cube.pagination;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaginatedQueryTestConfig {

  @Bean
  PaginatedQuery smartPaginatedQuery() {
    return new PaginatedQuery(20, true, 10);
  }

  @Bean
  PaginatedQuery simplePaginatedQuery() {
    return new PaginatedQuery(30);
  }
}
