package zone.cogni.asquare.service.dataextraction;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.List;

public class ApplicationProfileDataExtractionService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationProfileDataExtractionService.class);

  private final ApplicationProfileDataExtractionConfiguration applicationProfileDataExtractionConfiguration;

  public ApplicationProfileDataExtractionService(ApplicationProfileDataExtractionConfiguration applicationProfileDataExtractionConfiguration) {
    this.applicationProfileDataExtractionConfiguration = applicationProfileDataExtractionConfiguration;
  }

  public Model extractApplicationProfileData(ApplicationProfile applicationProfile,
                                             RdfStoreService rdfStoreService) {
    AccessService accessService = applicationProfileDataExtractionConfiguration.getAccessService(() -> rdfStoreService);
    return processApplicationProfile(applicationProfile, accessService);
  }

  private Model processApplicationProfile(ApplicationProfile applicationProfile, AccessService accessService) {
    TypedResourceModelBuilder modelBuilder = new TypedResourceModelBuilder();

    applicationProfile.getTypes().values().forEach(type -> {
      processType(modelBuilder, type, accessService);
    });

    return modelBuilder.get();
  }

  private void processType(TypedResourceModelBuilder modelBuilder, ApplicationProfile.Type type, AccessService accessService) {
    List<? extends TypedResource> all = accessService.findAll(type);
    log.info("Count for type {} : {}", type.getDescription(), all.size());

    int percentageDone = 0;
    for (int i = 0; i < all.size(); i++) {
      TypedResource instance = all.get(i);

      int currentPercent = i * 100 / all.size();
      if (currentPercent >= percentageDone) {
        log.info("Processed {}% ({} resources)", percentageDone, i);
        percentageDone += 1;
      }

      log.trace("Processing instance: {}", instance.getResource());
      modelBuilder.addInstance(type, instance);
    }

    log.info("Done processing type {}.", String.join(",", type.getClassIds()));
  }

}

