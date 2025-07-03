package zone.cogni.asquare.service.queryapi.filter.value;

import jakarta.annotation.Nullable;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.service.queryapi.filter.DepthFilterPipe;

import java.util.Optional;
import java.util.stream.Stream;

public interface ValueFilterPipe extends DepthFilterPipe {

  <T extends RdfValue> Stream<T> filterStream(@Nullable Attribute attribute, Stream<T> values);

  Optional<ApplicationView.AttributeMatcher> asAttributeMatcher();

}
