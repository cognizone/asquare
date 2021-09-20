package zone.cogni.asquare.service.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({AsyncAspect.class})
public class AsyncConfig {
}
