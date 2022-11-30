package zone.cogni.asquare.cube.hash;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import zone.cogni.asquare.cube.sort.SortedStatementsString;
import zone.cogni.asquare.cube.sort.StringRdfVisitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

public class ModelHasher implements Function<Model, byte[]> {

    @Override
    public byte[] apply(Model model) {
        return hasBlankNode(model) ? hashFromSortedTurtle(model) : hashFromStatements(model);
    }

    private boolean hasBlankNode(Model model) {
        return model.listStatements().toList().stream()
                    .map(this::hasBlankNode)
                    .reduce(Boolean::logicalOr)
                    .orElse(false);
    }

    private boolean hasBlankNode(Statement stmt) {
        return stmt.getSubject().isAnon() || stmt.getObject().isAnon();
    }

    private static byte[] hashFromSortedTurtle(Model model) {
        SortedStatementsString sortedStatementsString = new SortedStatementsString();
        String sortedString = sortedStatementsString.apply(model);

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] bytes = sortedString.trim().getBytes(StandardCharsets.UTF_8);

            return sha256.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hashFromStatements(Model model) {
        try {
            StringRdfVisitor visitor = new StringRdfVisitor();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return model.listStatements().toList().stream()
                        .map(s -> visitor.visitStmt(s.getSubject(), s))
                        .map(String::getBytes)
                        .map(sha256::digest)
                        .reduce(ModelHasher::elementWiseXOr)
                        .orElse(new byte[32]);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] elementWiseXOr(byte[] arr1, byte[] arr2) {
        if (arr1.length != arr2.length) throw new IllegalArgumentException("Byte arrays should have same length");

        byte[] result = new byte[arr1.length];
        for(int i = 0; i < result.length; i++) {
            result[i] = (byte) (arr1[i] ^ arr2[i]);
        }
        return result;
    }

}
