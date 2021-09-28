package com.carrotsearch.randomizedtesting;

import java.util.Random;

/**
 * This class is overriding seeded pseudo random feature required by mocked elastic server.
 * <p>
 * Elasticsearch 7 is using pseudo randomized testing features.
 * Unfortunately it is implemented only for JUnit 4 and causes dramatic performance impact.
 * <p>
 * Currently it is not possible to override pseudo random generator because it is carefully hidden under the hood.
 * Probably in later versions of elasticsearch this class should be replaced with better solution.
 */
public class RandomizedContext {
  public Random getRandom() {
    return new Random();
  }
}
