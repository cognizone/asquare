package zone.cogni.asquare.security.permission;

public enum PermissionStrategy {
  Any {
    @Override
    public <IN, OUT> OUT accept(PermissionStrategyVisitor<IN, OUT> visitor, IN in) {
      return visitor.visitAny(in);
    }
  },
  All {
    @Override
    public <IN, OUT> OUT accept(PermissionStrategyVisitor<IN, OUT> visitor, IN in) {
      return visitor.visitAll(in);
    }
  };

  public abstract <IN, OUT> OUT accept(PermissionStrategyVisitor<IN, OUT> visitor, IN in);
}
