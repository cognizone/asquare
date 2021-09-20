/**
 * Introduction of two classes focusing on converting from JSON to RDF and vice versa.
 *
 * <ul>
 *   <li>
 *     CompactConversionProfile: a simple JSON representation for a ConversionProfile.
 *     They follow the same rules as an ConversionProfile but won't repeat properties of a superclass.
 *   </li>
 *   <li>
 *     ConversionProfile: a simple representation which expands properties of superclasses,
 *     similar to the expanded application profile class.
 *   </li>
 * </ul>
 *
 * <p>
 * Other classes are there to convert one set of data into another
 * <ul>
 *   <li>ApplicationProfileToCompactConversionProfile: keeps subClassOf relations for compactness</li>
 *   <li>ApplicationProfileToConversionProfile: duplicates all properties of subClassOf relations</li>
 *   <li>CompactConversionProfileToConversionProfile: duplicates all properties of superclasses</li>
 * </ul>
 */
package zone.cogni.asquare.cube.convertor.json;