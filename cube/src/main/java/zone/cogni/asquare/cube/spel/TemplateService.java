package zone.cogni.asquare.cube.spel;

import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;

import java.util.function.Supplier;
import java.util.stream.Stream;

public interface TemplateService {
    static Supplier<String> fromResource(Resource templateResource) {
        return () -> ResourceHelper.toString(templateResource);
    }

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
        return processTemplate(fromResource(templateResource), root);
    }

    default String processTemplate(Supplier<String> templateSupplier, Object root) {
        return processTemplate(templateSupplier.get(), root);
    }

    default Stream<String> processTemplates(Stream<Supplier<String>> templateSuppliers, Object root) {
        return templateSuppliers.map(supplier -> processTemplate(supplier, root));
    }
}
