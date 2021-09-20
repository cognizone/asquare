package zone.cogni.asquare;

public class FeatureFlag {

  public static boolean cachedDeltaResource = false;

  public static void methodIncompatibleWithCachedDeltaResourceFeature() {
    if (cachedDeltaResource) throw new RuntimeException("This method is incompatible with the CachedDeltaResource feature");
  }

}
