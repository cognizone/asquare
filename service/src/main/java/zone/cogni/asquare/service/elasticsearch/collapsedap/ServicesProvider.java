package zone.cogni.asquare.service.elasticsearch.collapsedap;

import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;

import zone.cogni.asquare.service.elasticsearch.ElasticStore;

public interface ServicesProvider {

  ElasticStore getElasticStore();

  ApplicationProfileConfig getApplicationProfileConfig();

}