package zone.cogni.asquare.cube.spel;

import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;

public interface TemplateService {

    String processTemplate(String template, Object root);

    default NamedTemplate processTemplate(NamedTemplate namedTemplate, Object root) {
        String result = processTemplate(namedTemplate.getTemplate(), root);
        return getNewNamedTemplateCopy(namedTemplate, root, result);
    }

    default NamedTemplate getNewNamedTemplateCopy(NamedTemplate namedTemplate, Object root, String result) {
        NamedTemplate copy = namedTemplate.copy();
        copy.setRoot(root);
        copy.setResult(result);
        return copy;
    }

    default String processTemplate(Resource templateResource, Object root) {
        String template = ResourceHelper.toString(templateResource);
        return processTemplate(template, root);
    }
}
