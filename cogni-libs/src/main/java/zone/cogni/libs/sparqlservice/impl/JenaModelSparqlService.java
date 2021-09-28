package zone.cogni.libs.sparqlservice.impl;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.util.function.Function;

public class JenaModelSparqlService implements SparqlService {

    protected final Dataset dataset = DatasetFactory.create();

    protected final Boolean simulateRelaxedVirtuosoSparqlSelect;

    public JenaModelSparqlService() {
        this(false);
    }

    public JenaModelSparqlService(Boolean simulateRelaxedVirtuosoSparqlSelect) {
        this.simulateRelaxedVirtuosoSparqlSelect = simulateRelaxedVirtuosoSparqlSelect;
    }

    @Override
    public void uploadTtlFile(File file) {
        Model model = JenaUtils.read(new FileSystemResource(file));
        String name = "http://localhost:8080/local/graph/" + file.getName();
        dataset.addNamedModel(name, model);
    }

    @Override
    public Model queryForModel(String query) {
        try (QueryExecution queryExecution = QueryExecutionFactory.create(QueryFactory.create(query), dataset)) {
            return queryExecution.execConstruct();
        }
    }

    @Override
    public void executeUpdateQuery(String updateQuery) {
        UpdateRequest request = UpdateFactory.create(updateQuery);
        UpdateAction.execute(request, dataset);
    }

    @Override
    public void upload(Model model, String graphUri) {
        dataset.addNamedModel(graphUri, model);
    }

    private Dataset getDatasetForSelect() {
        if (simulateRelaxedVirtuosoSparqlSelect) {
            Dataset relaxedDataset = DatasetFactory.create(dataset);
            relaxedDataset.asDatasetGraph().setDefaultGraph(relaxedDataset.asDatasetGraph().getUnionGraph());
            return relaxedDataset;
        }
        return dataset;
    }

    @Override
    public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
        try (QueryExecution queryExecution = QueryExecutionFactory.create(QueryFactory.create(query), getDatasetForSelect())) {
            return resultHandler.apply(queryExecution.execSelect());
        }
    }

    @Override
    public boolean executeAskQuery(String query) {
        try (QueryExecution queryExecution = QueryExecutionFactory.create(QueryFactory.create(query), getDatasetForSelect())) {
            return queryExecution.execAsk();
        }
    }

    public Model executeConstructQuery(String query) {
        try (QueryExecution queryExecution = QueryExecutionFactory.create(QueryFactory.create(query), getDatasetForSelect())) {
            return queryExecution.execConstruct();
        }
    }

    @Override
    public void dropGraph(String graphUri) {
        dataset.removeNamedModel(graphUri);
    }
}
