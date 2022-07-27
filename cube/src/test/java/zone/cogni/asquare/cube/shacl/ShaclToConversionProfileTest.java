package zone.cogni.asquare.cube.shacl;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;
import zone.cogni.libs.jena.utils.JenaUtils;

import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;

public class ShaclToConversionProfileTest {

  private final ShaclToConversionProfile shaclToConversionProfile = new ShaclToConversionProfile();

  @Test
  public void simple_person_shacl_conversion() {
    // given
    Model personShacl = getShaclModel("simple-person.shacl.ttl");

    // when
    CompactConversionProfile profile = shaclToConversionProfile.apply(personShacl);

    // then
    assertThat(profile.getContext().getPrefixes()).hasSize(6);
    assertThat(profile.getTypes()).hasSize(1);

    CompactConversionProfile.Type person = profile.getById("ex:Person");
    assertThat(person).isNotNull();
    assertThat(person.getId()).isEqualTo("ex:Person");
    assertThat(person.getType()).isEqualTo("http://example.org/person#Person");
    assertThat(person.getSuperClasses()).isEmpty();
    assertThat(person.getAttributes()).hasSize(5);

    CompactConversionProfile.Attribute firstName = person.getById("ex:firstName");
    assertThat(firstName).isNotNull();
    assertThat(firstName.getProperty()).isEqualTo("http://example.org/person#firstName");
    assertThat(firstName.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.mix);
    assertThat(firstName.isSingle()).isTrue();
  }

  @Test
  public void attribute_type_shacl_conversion() {
    // given
    Model personShacl = getShaclModel("attribute-type-person.shacl.ttl");

    // when
    CompactConversionProfile profile = shaclToConversionProfile.apply(personShacl);

    // then
    CompactConversionProfile.Type personType = profile.getById("ex:Person");
    assertThat(personType.getAttributes()).hasSize(6);

    CompactConversionProfile.Attribute spouse = personType.getById("ex:spouse");
    assertThat(spouse).isNotNull();
    assertThat(spouse.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.object);

    CompactConversionProfile.Attribute firstName = personType.getById("ex:firstName");
    assertThat(firstName).isNotNull();
    assertThat(firstName.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);

    CompactConversionProfile.Attribute lastName = personType.getById("ex:lastName");
    assertThat(lastName).isNotNull();
    assertThat(firstName.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);
  }

  @Test
  public void attribute_cardinality_shacl_conversion() {
    // given
    Model personShacl = getShaclModel("attribute-cardinality-person.shacl.ttl");

    // when
    CompactConversionProfile profile = shaclToConversionProfile.apply(personShacl);

    // then
    CompactConversionProfile.Type personType = profile.getById("ex:Person");
    assertThat(personType.getAttributes()).hasSize(8);

    CompactConversionProfile.Attribute sons = personType.getById("ex:sons");
    assertThat(sons).isNotNull();
    assertThat(sons.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.object);
    assertThat(sons.isSingle()).isFalse();

    CompactConversionProfile.Attribute daughters = personType.getById("ex:daughters");
    assertThat(daughters).isNotNull();
    assertThat(daughters.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.object);
    assertThat(daughters.isSingle()).isFalse();
  }

  @Test
  public void iri_resource_shacl_conversion() {
    // given
    Model personShacl = getShaclModel("iri-resource-person.shacl.ttl");

    // when
    CompactConversionProfile profile = shaclToConversionProfile.apply(personShacl);

    // then
    CompactConversionProfile.Type personType = profile.getById("ex:Person");
    assertThat(personType.getAttributes()).hasSize(4);

    CompactConversionProfile.Attribute spouse = personType.getById("ex:spouse");
    assertThat(spouse).isNotNull();
    assertThat(spouse.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.object);
    assertThat(spouse.isSingle()).isTrue();

    CompactConversionProfile.Attribute homepage = personType.getById("ex:homepage");
    assertThat(homepage).isNotNull();
    assertThat(homepage.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);
    assertThat(homepage.isSingle()).isFalse();
  }

  @Nonnull
  private Model getShaclModel(String file) {
    Resource resource = new ClassPathResource("shacl/" + file);
    return JenaUtils.read(resource);
  }
}
