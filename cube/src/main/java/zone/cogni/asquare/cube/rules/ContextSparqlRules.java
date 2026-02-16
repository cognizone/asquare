package zone.cogni.asquare.cube.rules;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.NamedTemplate;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import jakarta.annotation.Nonnull;
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
    private final Map<String, Object> context;

    public ContextSparqlRules(@Nonnull SpelService spelService,
                              @Nonnull List<Resource> rules) {
        this(spelService, rules, new HashMap<>());
    }

    public ContextSparqlRules(@Nonnull SpelService spelService,
                              @Nonnull List<Resource> rules,
                              @Nonnull Map<String, ?> context) {
        this.spelService = spelService;
        this.rules = rules;
        this.context = new HashMap<>(context);
    }

    @Nonnull
    public Model convert(@Nonnull Model model, @Nonnull String uri) {
        return convert(model, uri, Map.of());
    }

    /**
     * Converts the model using the rules with additional context variables.
     * The uri is added to context as "uri", and extraContext values are merged in.
     * This allows passing user info (userUri, username, userGroups) to initialize rules.
     *
     * @param model the RDF model to transform
     * @param uri the URI to add to context as "uri"
     * @param extraContext additional context variables (e.g., userUri, userGroups)
     * @return the transformed model
     */
    @Nonnull
    public Model convert(@Nonnull Model model, @Nonnull String uri, @Nonnull Map<String, Object> extraContext) {
        HashMap<String, Object> fullContext = new HashMap<>(context);
        fullContext.put("uri", uri);
        fullContext.putAll(extraContext);

        return convert(model, fullContext);
    }

    @Nonnull
    public Model convert(@Nonnull Model model) {
        return convert(model, context);
    }

    private Model convert(@Nonnull Model model, @Nonnull Map<String, Object> fullContext) {
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

