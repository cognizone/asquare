package zone.cogni.asquare.graphcomposer;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({GraphComposerProcessor.class, GraphComposerService.class})
public class GraphComposerConfig {
}
