package zone.cogni.asquare.cube.rules;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.NamedTemplate;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ContextSparqlRules {

    private static boolean DEBUGGER_FAILED = false;

    private final SpelService spelService;
    private final List<Resource> rules;
    private final Map<String, String> context;

    public ContextSparqlRules(@Nonnull SpelService spelService,
                              @Nonnull List<Resource> rules) {
        this(spelService, rules, new HashMap<>());
    }

    public ContextSparqlRules(@Nonnull SpelService spelService,
                              @Nonnull List<Resource> rules,
                              @Nonnull Map<String, String> context) {
        this.spelService = spelService;
        this.rules = rules;
        this.context = context;
    }

    @Nonnull
    public Model convert(@Nonnull Model model, @Nonnull String uri) {
        HashMap<String, String> fullContext = new HashMap<>(context);
        fullContext.put("uri", uri);

        return convert(model, fullContext);
    }

    @Nonnull
    public Model convert(@Nonnull Model model) {
        return convert(model, context);
    }

    private Model convert(@Nonnull Model model, @Nonnull Map<String, String> fullContext) {
        InternalRdfStoreService rdfStore = getRdfStore(model);
        asSortedNamedTemplates()
                .forEach(namedTemplate -> {
                    Model modelCopy = log.isTraceEnabled() ? getModelCopy(rdfStore) : null;

                    log.debug("        {}", namedTemplate.getName());
                    NamedTemplate queryTemplate = spelService.processTemplate(namedTemplate, fullContext);
                    String updateQuery = queryTemplate.getResult();
                    if (log.isDebugEnabled()) logSelectInfo(rdfStore, updateQuery);

                    rdfStore.executeUpdateQuery(updateQuery);

                    if (log.isTraceEnabled()) logModelDifferences(modelCopy, rdfStore.getModel());
                });

        return model;
    }


    @Nonnull
    private InternalRdfStoreService getRdfStore(@Nonnull Model model) {
        return new InternalRdfStoreService(model);
    }

    @Nonnull
    private List<NamedTemplate> asSortedNamedTemplates() {
        return rules.stream()
                .map(NamedTemplate::fromResource)
                .sorted(Comparator.comparing(NamedTemplate::getName))
                .collect(Collectors.toList());
    }

    private Model getModelCopy(InternalRdfStoreService rdfStore) {
        Model result = ModelFactory.createDefaultModel();
        result.add(rdfStore.getModel());
        return result;
    }

    private void logSelectInfo(InternalRdfStoreService rdfStore, String updateQuery) {
        try {
            new SparqlRulesDebugger(rdfStore, updateQuery).run();
        }
        catch (RuntimeException e) {
            // log only once
            if (DEBUGGER_FAILED) return;

            log.info("debugger failed", e);
            DEBUGGER_FAILED = true;
        }
    }

    private void logModelDifferences(Model oldModel, Model newModel) {
        Model removedData = oldModel.difference(newModel);
        Model addedData = newModel.difference(oldModel);

        if (!removedData.isEmpty())
            log.trace("        removed  \n{}", toString(removedData));
        if (!addedData.isEmpty())
            log.trace("        added \n{}", toString(addedData));
    }

    private String toString(Model model) {
        StringWriter out = new StringWriter();
        model.write(out, "N-Triples");
        return out.toString();
    }
}

