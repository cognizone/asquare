package zone.cogni.asquare.applicationprofile;

import com.google.common.collect.Sets;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import java.util.Set;

public interface Constants {

  String timeNS = "http://www.w3.org/timeNS/2006#";

  Set<String> datatypes = Sets.newHashSet(
          OWL.NS + "rational",
          OWL.NS + "real",
          RDF.uri + "PlainLiteral",
          RDF.uri + "XMLLiteral",
          RDFS.Literal.getURI(),
          RDFS.Resource.getURI(),
          RDFLangString.rdfLangString.getURI(),
          timeNS + "generalDay",
          timeNS + "generalMonth",
          timeNS + "generalYear",
          XSD.anyURI.getURI(),
          XSD.base64Binary.getURI(),
          XSD.xboolean.getURI(),
          XSD.xbyte.getURI(),
          XSD.date.getURI(),
          XSD.dateTime.getURI(),
          XSD.dateTimeStamp.getURI(),
          XSD.decimal.getURI(),
          XSD.xdouble.getURI(),
          XSD.duration.getURI(),
          XSD.xfloat.getURI(),
          XSD.gDay.getURI(),
          XSD.gMonth.getURI(),
          XSD.gYear.getURI(),
          XSD.gYearMonth.getURI(),
          XSD.hexBinary.getURI(),
          XSD.xint.getURI(),
          XSD.integer.getURI(),
          XSD.language.getURI(),
          XSD.xlong.getURI(),
          XSD.Name.getURI(),
          XSD.NCName.getURI(),
          XSD.negativeInteger.getURI(),
          XSD.NMTOKEN.getURI(),
          XSD.nonNegativeInteger.getURI(),
          XSD.nonPositiveInteger.getURI(),
          XSD.normalizedString.getURI(),
          XSD.positiveInteger.getURI(),
          XSD.xshort.getURI(),
          XSD.xstring.getURI(),
          XSD.token.getURI(),
          XSD.unsignedByte.getURI(),
          XSD.unsignedInt.getURI(),
          XSD.unsignedLong.getURI(),
          XSD.unsignedShort.getURI()

  );
}
