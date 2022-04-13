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

import static org.apache.datasketches.kll.KllPreambleUtil.DATA_START_ADR;
import static org.apache.datasketches.kll.KllPreambleUtil.DATA_START_ADR_SINGLE_ITEM;

import org.apache.datasketches.memory.Memory;

/**
 * This class implements an on-heap floats KllSketch.
 *
 * <p>Please refer to the documentation in the package-info:<br>
 * {@link org.apache.datasketches.kll}</p>
 *
 * @author Lee Rhodes, Kevin Lang
 */
final class KllHeapFloatsSketch extends KllFloatsSketch {
  private final int k_;    // configured value of K.
  private final int m_;    // configured value of M.
  private long n_;        // number of items input into this sketch.
  private int minK_;    // dynamic minK for error estimation after merging with different k.
  private int numLevels_; // one-based number of current levels.
  private boolean isLevelZeroSorted_;
  private float minFloatValue_;
  private float maxFloatValue_;
  private float[] floatItems_;

  /**
   * New instance heap constructor with a given parameters <em>k</em> and <em>m</em>.
   *
   * @param k parameter that controls size of the sketch and accuracy of estimates.
   * <em>k</em> can be any value between <em>m</em> and 65535, inclusive.
   * The default <em>k</em> = 200 results in a normalized rank error of about 1.65%.
   * Higher values of <em>k</em> will have smaller error but the sketch will be larger (and slower).
   * @param m parameter that controls the minimum level width in items. It can be 2, 4, 6 or 8.
   * The DEFAULT_M, which is 8 is recommended. Other values of <em>m</em> should be considered
   * experimental as they have not been as well characterized.
   */
  KllHeapFloatsSketch(final int k, final int m) {
    super(null, null);
    KllHelper.checkM(m);
    KllHelper.checkK(k, m);
    this.k_ = k;
    this.m_ = m;
    n_ = 0;
    minK_ = k;
    numLevels_ = 1;
    isLevelZeroSorted_ = false;
    levelsArr = new int[] {k, k};
    minFloatValue_ = Float.NaN;
    maxFloatValue_ = Float.NaN;
    floatItems_ = new float[k];
  }

  /**
   * Heapify constructor.
   * @param srcMem Memory object that contains data serialized by this sketch.
   * @param memVal the MemoryCheck object
   */
  KllHeapFloatsSketch(final Memory srcMem, final KllMemoryValidate memVal) {
    super(null, null);
    k_ = memVal.k;
    m_ = memVal.m;
    n_ = memVal.n;
    minK_ = memVal.minK;
    numLevels_ = memVal.numLevels;
    isLevelZeroSorted_ = memVal.level0Sorted;
    final boolean updatableMemFormat = memVal.updatableMemFormat;

    if (memVal.empty && !updatableMemFormat) {
      levelsArr = memVal.levelsArr;
      minFloatValue_ = Float.NaN;
      maxFloatValue_ = Float.NaN;
      floatItems_ = new float[k_];
    }
    else if (memVal.singleItem && !updatableMemFormat) {
      levelsArr = memVal.levelsArr;
      final float value = srcMem.getFloat(DATA_START_ADR_SINGLE_ITEM);
      minFloatValue_ = maxFloatValue_ = value;
      floatItems_ = new float[k_];
      floatItems_[k_ - 1] = value;
    }
    else { //Full or updatableMemFormat
      int offsetBytes = DATA_START_ADR;
      levelsArr = memVal.levelsArr;
      offsetBytes += (updatableMemFormat ? levelsArr.length * Integer.BYTES : (levelsArr.length - 1) * Integer.BYTES);
      minFloatValue_ = srcMem.getFloat(offsetBytes);
      offsetBytes += Float.BYTES;
      maxFloatValue_ = srcMem.getFloat(offsetBytes);
      offsetBytes += Float.BYTES;
      final int capacityItems = levelsArr[numLevels_];
      final int retainedItems = capacityItems - levelsArr[0];
      floatItems_ = new float[capacityItems];
      final int shift = levelsArr[0];
      if (updatableMemFormat) {
        offsetBytes += shift * Float.BYTES;
        srcMem.getFloatArray(offsetBytes, floatItems_, shift, retainedItems);
      } else {
        srcMem.getFloatArray(offsetBytes, floatItems_, shift, retainedItems);
      }
    }
  }

  @Override
  public int getK() {
    return k_;
  }

  @Override
  public long getN() {
    return n_;
  }

  @Override
  float[] getFloatItemsArray() { return floatItems_; }

  @Override
  float getFloatItemsArrayAt(final int index) { return floatItems_[index]; }

  @Override
  int getM() {
    return m_;
  }

  @Override
  float getMaxFloatValue() { return maxFloatValue_; }

  @Override
  float getMinFloatValue() { return minFloatValue_; }

  @Override
  int getMinK() {
    return minK_;
  }

  @Override
  int getNumLevels() {
    return numLevels_;
  }

  @Override
  void incN() {
    n_++;
  }

  @Override
  void incNumLevels() {
    numLevels_++;
  }

  @Override
  boolean isLevelZeroSorted() {
    return isLevelZeroSorted_;
  }

  @Override
  void setFloatItemsArray(final float[] floatItems) { floatItems_ = floatItems; }

  @Override
  void setFloatItemsArrayAt(final int index, final float value) { floatItems_[index] = value; }

  @Override
  void setLevelZeroSorted(final boolean sorted) {
    this.isLevelZeroSorted_ = sorted;
  }

  @Override
  void setMaxFloatValue(final float value) { maxFloatValue_ = value; }

  @Override
  void setMinFloatValue(final float value) { minFloatValue_ = value; }

  @Override
  void setMinK(final int minK) {
    minK_ = minK;
  }

  @Override
  void setN(final long n) {
    n_ = n;
  }

  @Override
  void setNumLevels(final int numLevels) {
    numLevels_ = numLevels;
  }

}
