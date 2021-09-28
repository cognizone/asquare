package zone.cogni.asquareroot;

import zone.cogni.asquare.service.elasticsearch.v7.HttpElasticsearch7Store;
import zone.cogni.asquare.virtuoso.VirtuosoRdfStoreService;
import zone.cogni.libs.services.extfolder.ExtFolderServiceFactory;
import zone.cogni.libs.sparqlservice.impl.VirtuosoSparqlService;

interface EmbeddedEnvironment {

  default Object embedBefore(EmbeddedContext context, Object bean, String beanName) {
    if (bean instanceof HttpElasticsearch7Store && context.getEmbeddedElastic()) {
      return context.mockElasticsearch7Store(bean, beanName);
    }
    else if (bean instanceof ExtFolderServiceFactory) {
      return context.mockExtFolderServiceFactory(bean, beanName);
    }
    else if (bean instanceof VirtuosoSparqlService) {
      return context.mockVirtuosoSparqlService(bean, beanName);
    }
    else if (bean instanceof VirtuosoRdfStoreService) {
      return context.mockVirtuosoRdfStoreService(bean, beanName);
    }
    return bean;
  }

  default Object embedAfter(EmbeddedContext context, Object bean, String beanName) {
    return bean;
  }
}
