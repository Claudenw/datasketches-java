/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.kll;

import static java.lang.Math.ceil;
import static org.apache.datasketches.kll.KllItemsHelper.intToFixedLengthString;
import static org.apache.datasketches.kll.KllItemsHelper.le;
import static org.apache.datasketches.kll.KllItemsHelper.numDigits;
import static org.apache.datasketches.quantilescommon.QuantileSearchCriteria.EXCLUSIVE;
import static org.apache.datasketches.quantilescommon.QuantileSearchCriteria.INCLUSIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Comparator;

import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.common.SketchesArgumentException;
import org.apache.datasketches.memory.DefaultMemoryRequestServer;
import org.testng.annotations.Test;

@SuppressWarnings("unused")
public class KllItemsSketchTest {
  private static final double PMF_EPS_FOR_K_8 = 0.35; // PMF rank error (epsilon) for k=8
  private static final double PMF_EPS_FOR_K_128 = 0.025; // PMF rank error (epsilon) for k=128
  private static final double PMF_EPS_FOR_K_256 = 0.013; // PMF rank error (epsilon) for k=256
  private static final double NUMERIC_NOISE_TOLERANCE = 1E-6;
  private static final DefaultMemoryRequestServer memReqSvr = new DefaultMemoryRequestServer();
  public ArrayOfStringsSerDe serDe = new ArrayOfStringsSerDe();

  @Test
  public void empty() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch.update(null); // this must not change anything
    assertTrue(sketch.isEmpty());
    assertEquals(sketch.getN(), 0);
    assertEquals(sketch.getNumRetained(), 0);
    try { sketch.getRank("", INCLUSIVE); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getMinItem(); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getMaxItem(); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getQuantile(0.5); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getQuantiles(new double[] {0}); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getPMF(new String[] {""}); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getCDF(new String[] {""}); fail(); } catch (SketchesArgumentException e) {}
    assertNotNull(sketch.toString(true, true));
    assertNotNull(sketch.toString());
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void getQuantileInvalidArg() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch.update("A");
    sketch.getQuantile(-1.0);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void getQuantilesInvalidArg() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch.update("A");
    sketch.getQuantiles(new double[] {2.0});
  }

  @Test
  public void oneValue() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch.update("A");
    assertFalse(sketch.isEmpty());
    assertEquals(sketch.getN(), 1);
    assertEquals(sketch.getNumRetained(), 1);
    assertEquals(sketch.getRank("A", EXCLUSIVE), 0.0);
    assertEquals(sketch.getRank("B", EXCLUSIVE), 1.0);
    assertEquals(sketch.getRank("A", EXCLUSIVE), 0.0);
    assertEquals(sketch.getRank("B", EXCLUSIVE), 1.0);
    assertEquals(sketch.getRank("@", INCLUSIVE), 0.0);
    assertEquals(sketch.getRank("A", INCLUSIVE), 1.0);
    assertEquals(sketch.getMinItem(),"A");
    assertEquals(sketch.getMaxItem(), "A");
    assertEquals(sketch.getQuantile(0.5, EXCLUSIVE), "A");
    assertEquals(sketch.getQuantile(0.5, INCLUSIVE), "A");
  }

  @Test
  public void tenValues() {
    final String[] tenStr = {"A","B","C","D","E","F","G","H","I","J"};
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(20, Comparator.naturalOrder(), serDe);
    for (int i = 1; i <= 10; i++) { sketch.update(tenStr[i - 1]); }
    assertFalse(sketch.isEmpty());
    assertEquals(sketch.getN(), 10);
    assertEquals(sketch.getNumRetained(), 10);
    for (int i = 1; i <= 10; i++) {
      assertEquals(sketch.getRank(tenStr[i - 1], EXCLUSIVE), (i - 1) / 10.0);
      assertEquals(sketch.getRank(tenStr[i - 1], INCLUSIVE), i / 10.0);
    }
    final String[] qArr = tenStr;
    double[] rOut = sketch.getRanks(qArr); //inclusive
    for (int i = 0; i < qArr.length; i++) {
      assertEquals(rOut[i], (i + 1) / 10.0);
    }
    rOut = sketch.getRanks(qArr, EXCLUSIVE); //exclusive
    for (int i = 0; i < qArr.length; i++) {
      assertEquals(rOut[i], i / 10.0);
    }

    for (int i = 0; i >= 10; i++) {
      double rank = i/10.0;
      String q = rank == 1.0 ? tenStr[i-1] : tenStr[i];
      assertEquals(sketch.getQuantile(rank, EXCLUSIVE), q);
      q = rank == 0 ? tenStr[i] : tenStr[i - 1];
      assertEquals(sketch.getQuantile(rank, INCLUSIVE), q);
    }

    {
      // getQuantile() and getQuantiles() equivalence EXCLUSIVE
      final String[] quantiles =
          sketch.getQuantiles(new double[] {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}, EXCLUSIVE);
      for (int i = 0; i <= 10; i++) {
        assertEquals(sketch.getQuantile(i / 10.0, EXCLUSIVE), quantiles[i]);
      }
    }
    {
      // getQuantile() and getQuantiles() equivalence INCLUSIVE
      final String[] quantiles =
          sketch.getQuantiles(new double[] {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1}, INCLUSIVE);
      for (int i = 0; i <= 10; i++) {
        assertEquals(sketch.getQuantile(i / 10.0, INCLUSIVE), quantiles[i]);
      }
    }
  }

  @Test
  public void manyValuesEstimationMode() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final int n = 1_000_000;
    final int digits = numDigits(n);

    for (int i = 1; i <= n; i++) {
      sketch.update(intToFixedLengthString(i, digits));
      assertEquals(sketch.getN(), i);
    }

    // test getRank
    for (int i = 1; i <= n; i++) {
      final double trueRank = (double) i / n;
      String s = intToFixedLengthString(i, digits);
      double r = sketch.getRank(s);
      assertEquals(sketch.getRank(s), trueRank, PMF_EPS_FOR_K_256, "for value " + s);
    }

    // test getPMF
    String s = intToFixedLengthString(n/2, digits);
    final double[] pmf = sketch.getPMF(new String[] {s}); // split at median
    assertEquals(pmf.length, 2);
    assertEquals(pmf[0], 0.5, PMF_EPS_FOR_K_256);
    assertEquals(pmf[1], 0.5, PMF_EPS_FOR_K_256);

    assertEquals(sketch.getMinItem(), intToFixedLengthString(1, digits));
    assertEquals(sketch.getMaxItem(), intToFixedLengthString(n, digits));

 // check at every 0.1 percentage point
    final double[] fractions = new double[1001];
    final double[] reverseFractions = new double[1001]; // check that ordering doesn't matter
    for (int i = 0; i <= 1000; i++) {
      fractions[i] = (double) i / 1000;
      reverseFractions[1000 - i] = fractions[i];
    }
    final String[] quantiles = sketch.getQuantiles(fractions);
    final String[] reverseQuantiles = sketch.getQuantiles(reverseFractions);
    String previousQuantile = "";
    for (int i = 0; i <= 1000; i++) {
      final String quantile = sketch.getQuantile(fractions[i]);
      assertEquals(quantile, quantiles[i]);
      assertEquals(quantile, reverseQuantiles[1000 - i]);
      assertTrue(le(previousQuantile, quantile, Comparator.naturalOrder()));
      previousQuantile = quantile;
    }
  }

  @Test
  public void getRankGetCdfGetPmfConsistency() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final int n = 1000;
    final int digits = numDigits(n);
    final String[] quantiles = new String[n];
    for (int i = 0; i < n; i++) {
      final String str = intToFixedLengthString(i, digits);
      sketch.update(str);
      quantiles[i] = str;
    }
    { //EXCLUSIVE
      final double[] ranks = sketch.getCDF(quantiles, EXCLUSIVE);
      final double[] pmf = sketch.getPMF(quantiles, EXCLUSIVE);
      double sumPmf = 0;
      for (int i = 0; i < n; i++) {
        assertEquals(ranks[i], sketch.getRank(quantiles[i], EXCLUSIVE), NUMERIC_NOISE_TOLERANCE, "rank vs CDF for value " + i);
        sumPmf += pmf[i];
        assertEquals(ranks[i], sumPmf, NUMERIC_NOISE_TOLERANCE, "CDF vs PMF for value " + i);
      }
      sumPmf += pmf[n];
      assertEquals(sumPmf, 1.0, NUMERIC_NOISE_TOLERANCE);
      assertEquals(ranks[n], 1.0, NUMERIC_NOISE_TOLERANCE);
    }
    { // INCLUSIVE (default)
      final double[] ranks = sketch.getCDF(quantiles, INCLUSIVE);
      final double[] pmf = sketch.getPMF(quantiles, INCLUSIVE);
      double sumPmf = 0;
      for (int i = 0; i < n; i++) {
        assertEquals(ranks[i], sketch.getRank(quantiles[i], INCLUSIVE), NUMERIC_NOISE_TOLERANCE,
            "rank vs CDF for value " + i);
        sumPmf += pmf[i];
        assertEquals(ranks[i], sumPmf, NUMERIC_NOISE_TOLERANCE, "CDF vs PMF for value " + i);
      }
      sumPmf += pmf[n];
      assertEquals(sumPmf, 1.0, NUMERIC_NOISE_TOLERANCE);
      assertEquals(ranks[n], 1.0, NUMERIC_NOISE_TOLERANCE);
    }
  }

  @Test
  public void merge() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final int n = 10000;
    final int digits = numDigits(2 * n);
    for (int i = 0; i < n; i++) {
      sketch1.update(intToFixedLengthString(i, digits));
      sketch2.update(intToFixedLengthString(2 * n - i - 1, digits));
    }

    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(n - 1, digits));

    assertEquals(sketch2.getMinItem(), intToFixedLengthString(n, digits));
    assertEquals(sketch2.getMaxItem(), intToFixedLengthString(2 * n - 1, digits));

    sketch1.merge(sketch2);

    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), 2L * n);
    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(2 * n - 1, digits));
    String upperBound = intToFixedLengthString(n + (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String lowerBound = intToFixedLengthString(n - (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String median = sketch1.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
  }

  @Test
  public void mergeLowerK() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(256, Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(128, Comparator.naturalOrder(), serDe);
    final int n = 10000;
    final int digits = numDigits(2 * n);
    for (int i = 0; i < n; i++) {
      sketch1.update(intToFixedLengthString(i, digits));
      sketch2.update(intToFixedLengthString(2 * n - i - 1, digits));
    }

    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(n - 1, digits));

    assertEquals(sketch2.getMinItem(), intToFixedLengthString(n, digits));
    assertEquals(sketch2.getMaxItem(), intToFixedLengthString(2 * n - 1, digits));

    assertTrue(sketch1.getNormalizedRankError(false) < sketch2.getNormalizedRankError(false));
    assertTrue(sketch1.getNormalizedRankError(true) < sketch2.getNormalizedRankError(true));
    sketch1.merge(sketch2);

    // sketch1 must get "contaminated" by the lower K in sketch2
    assertEquals(sketch1.getNormalizedRankError(false), sketch2.getNormalizedRankError(false));
    assertEquals(sketch1.getNormalizedRankError(true), sketch2.getNormalizedRankError(true));

    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), 2 * n);
    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(2 * n - 1, digits));
    String upperBound = intToFixedLengthString(n + (int)ceil(n * PMF_EPS_FOR_K_128), digits);
    String lowerBound = intToFixedLengthString(n - (int)ceil(n * PMF_EPS_FOR_K_128), digits);
    String median = sketch1.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
  }

  @Test
  public void mergeEmptyLowerK() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(256, Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(128, Comparator.naturalOrder(), serDe);
    final int n = 10000;
    final int digits = numDigits(n);
    for (int i = 0; i < n; i++) {
      sketch1.update(intToFixedLengthString(i, digits)); //sketch2 is empty
    }

    // rank error should not be affected by a merge with an empty sketch with lower K
    final double rankErrorBeforeMerge = sketch1.getNormalizedRankError(true);
    sketch1.merge(sketch2);
    assertEquals(sketch1.getNormalizedRankError(true), rankErrorBeforeMerge);
    {
    assertFalse(sketch1.isEmpty());
    assertTrue(sketch2.isEmpty());
    assertEquals(sketch1.getN(), n);
    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(n - 1, digits));
    String upperBound = intToFixedLengthString(n / 2 + (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String lowerBound = intToFixedLengthString(n / 2 - (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String median = sketch1.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
    }
    {
    //merge the other way
    sketch2.merge(sketch1);
    assertFalse(sketch1.isEmpty());
    assertFalse(sketch2.isEmpty());
    assertEquals(sketch1.getN(), n);
    assertEquals(sketch2.getN(), n);
    assertEquals(sketch1.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch1.getMaxItem(), intToFixedLengthString(n - 1, digits));
    assertEquals(sketch2.getMinItem(), intToFixedLengthString(0, digits));
    assertEquals(sketch2.getMaxItem(), intToFixedLengthString(n - 1, digits));
    String upperBound = intToFixedLengthString(n / 2 + (int)ceil(n * PMF_EPS_FOR_K_128), digits);
    String lowerBound = intToFixedLengthString(n / 2 - (int)ceil(n * PMF_EPS_FOR_K_128), digits);
    String median = sketch2.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
    }
  }

  @Test
  public void mergeExactModeLowerK() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(256, Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(128, Comparator.naturalOrder(), serDe);
    final int n = 10000;
    final int digits = numDigits(n);
    for (int i = 0; i < n; i++) {
      sketch1.update(intToFixedLengthString(i, digits));
    }
    sketch2.update(intToFixedLengthString(1, digits));

    // rank error should not be affected by a merge with a sketch in exact mode with lower K
    final double rankErrorBeforeMerge = sketch1.getNormalizedRankError(true);
    sketch1.merge(sketch2);
    assertEquals(sketch1.getNormalizedRankError(true), rankErrorBeforeMerge);
  }

  @Test
  public void mergeMinMinValueFromOther() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch1.update(intToFixedLengthString(1, 1));
    sketch2.update(intToFixedLengthString(2, 1));
    sketch2.merge(sketch1);
    assertEquals(sketch2.getMinItem(), intToFixedLengthString(1, 1));
  }

  @Test
  public void mergeMinAndMaxFromOther() {
    final KllItemsSketch<String> sketch1 = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final KllItemsSketch<String> sketch2 = KllItemsSketch.newHeapInstance(10, Comparator.naturalOrder(), serDe);
    final int n = 1_000_000;
    final int digits = numDigits(n);
    for (int i = 1; i <= 1_000_000; i++) {
      sketch1.update(intToFixedLengthString(i, digits)); //sketch2 is empty
    }
    sketch2.merge(sketch1);
    assertEquals(sketch2.getMinItem(), intToFixedLengthString(1, digits));
    assertEquals(sketch2.getMaxItem(), intToFixedLengthString(n, digits));
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void kTooSmall() {
    KllItemsSketch.newHeapInstance(KllSketch.DEFAULT_M - 1, Comparator.naturalOrder(), serDe);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void kTooLarge() {
    KllItemsSketch.newHeapInstance(KllSketch.MAX_K + 1, Comparator.naturalOrder(), serDe);
  }

  @Test
  public void minK() {
    final KllItemsSketch<String> sketch =
        KllItemsSketch.newHeapInstance(KllSketch.DEFAULT_M,Comparator.naturalOrder(), serDe);
    final int n = 1000;
    final int digits = numDigits(n);
    for (int i = 0; i < n; i++) {
      sketch.update(intToFixedLengthString(i, digits));
    }
    assertEquals(sketch.getK(), KllSketch.DEFAULT_M);
    String upperBound = intToFixedLengthString(n / 2 + (int)ceil(n * PMF_EPS_FOR_K_8), digits);
    String lowerBound = intToFixedLengthString(n / 2 - (int)ceil(n * PMF_EPS_FOR_K_8), digits);
    String median = sketch.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
  }

  @Test
  public void maxK() {
    final KllItemsSketch<String> sketch =
        KllItemsSketch.newHeapInstance(KllSketch.MAX_K,Comparator.naturalOrder(), serDe);
    final int n = 1000;
    final int digits = numDigits(n);
    for (int i = 0; i < n; i++) {
      sketch.update(intToFixedLengthString(i, digits));
    }
    assertEquals(sketch.getK(), KllSketch.MAX_K);
    String upperBound = intToFixedLengthString(n / 2 + (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String lowerBound = intToFixedLengthString(n / 2 - (int)ceil(n * PMF_EPS_FOR_K_256), digits);
    String median = sketch.getQuantile(0.5);
    assertTrue(le(median, upperBound, Comparator.naturalOrder()));
    assertTrue(le(lowerBound, median, Comparator.naturalOrder()));
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void outOfOrderSplitPoints() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    final String s0 = intToFixedLengthString(0, 1);
    final String s1 = intToFixedLengthString(1, 1);
    sketch.update(s0);
    sketch.getCDF(new String[] {s1, s0});
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void nullSplitPoint() {
    final KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(Comparator.naturalOrder(), serDe);
    sketch.update(intToFixedLengthString(0, 1));
    sketch.getCDF(new String[] {null});
  }


  private final static boolean enablePrinting = true;

  /**
   * @param format the format
   * @param args the args
   */
  private static final void printf(final String format, final Object ...args) {
    if (enablePrinting) { System.out.printf(format, args); }
  }

  /**
   * @param o the Object to println
   */
  private static final void println(final Object o) {
    if (enablePrinting) { System.out.println(o.toString()); }
  }

}
