package zone.cogni.asquare.service.queryapi.filter.attribute;

import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.service.queryapi.filter.DepthFilterPipe;

import java.util.function.Predicate;

public interface AttributeFilterPipe extends DepthFilterPipe {

  Predicate<Attribute> getFilter();
}
