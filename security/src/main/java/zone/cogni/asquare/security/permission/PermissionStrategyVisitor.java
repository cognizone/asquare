package zone.cogni.asquare.security.permission;

public interface PermissionStrategyVisitor<IN, OUT> {

  OUT visitAll(IN in);

  OUT visitAny(IN in);
}
