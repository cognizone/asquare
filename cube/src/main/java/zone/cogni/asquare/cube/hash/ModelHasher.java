package zone.cogni.asquare.cube.hash;

import org.apache.jena.rdf.model.Model;
import zone.cogni.asquare.cube.sort.SortedStatementsString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

public class ModelHasher implements Function<Model, byte[]> {

    @Override
    public byte[] apply(Model model) {
        SortedStatementsString sortedStatementsString = new SortedStatementsString();
        String sortedString = sortedStatementsString.apply(model);

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(sortedString.trim().getBytes(StandardCharsets.UTF_8));
            return sha256.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
