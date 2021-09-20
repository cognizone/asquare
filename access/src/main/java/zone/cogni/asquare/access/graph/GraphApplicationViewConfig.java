package zone.cogni.asquare.access.graph;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({GraphApplicationViewFactory.class})
public class GraphApplicationViewConfig {
}
