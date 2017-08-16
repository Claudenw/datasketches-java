/*
 * Copyright 2017, Yahoo! Inc. Licensed under the terms of the
 * Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hll;

import static com.yahoo.sketches.Util.invPow2;
import static com.yahoo.sketches.hll.HllUtil.HLL_HIP_RSE_FACTOR;
import static com.yahoo.sketches.hll.HllUtil.HLL_NON_HIP_RSE_FACTOR;
import static com.yahoo.sketches.hll.HllUtil.LG_AUX_ARR_INTS;
import static com.yahoo.sketches.hll.PreambleUtil.HLL_BYTE_ARR_START;
import static com.yahoo.sketches.hll.PreambleUtil.HLL_PREINTS;

import com.yahoo.memory.Memory;

/**
 * @author Lee Rhodes
 */
abstract class AbstractHllArray extends HllSketchImpl {

  AbstractHllArray(final int lgConfigK, final TgtHllType tgtHllType, final CurMode curMode) {
    super(lgConfigK, tgtHllType, curMode);
  }

  abstract void addToHipAccum(double delta);

  abstract void decNumAtCurMin();

  abstract AuxHashMap getAuxHashMap();

  abstract PairIterator getAuxIterator();

  @Override
  int getCompactSerializationBytes() {
    final AuxHashMap auxHashMap = getAuxHashMap();
    final int auxBytes = (auxHashMap == null) ? 0 : auxHashMap.getAuxCount() << 2;
    return HLL_BYTE_ARR_START + getHllByteArrBytes() + auxBytes;
  }

  abstract int getCurMin();

  abstract double getHipAccum();

  abstract byte[] getHllByteArr();

  abstract int getHllByteArrBytes();

  //abstract void getHllBytesToMemory(WritableMemory dstWmem, int lenBytes);

  abstract double getKxQ0();

  abstract double getKxQ1();

  @Override
  int getMemArrStart() {
    return HLL_BYTE_ARR_START;
  }

  abstract Memory getMemory();

  abstract int getNumAtCurMin();

  @Override
  int getPreInts() {
    return HLL_PREINTS;
  }

  @Override
  double getRelErr(final int numStdDev) {
    HllUtil.checkNumStdDev(numStdDev);
    final int lgConfigK = getLgConfigK();
    final boolean oooFlag = isOutOfOrderFlag();
    if (lgConfigK <= 12) {
      return RelativeErrorTables.getRelErr(true, oooFlag, lgConfigK, numStdDev);
    }
    final double rseFactor =  (oooFlag) ? HLL_NON_HIP_RSE_FACTOR : HLL_HIP_RSE_FACTOR;
    return (rseFactor * numStdDev) / Math.sqrt(1 << lgConfigK);
  }

  @Override
  double getRelErrFactor(final int numStdDev) {
    HllUtil.checkNumStdDev(numStdDev);
    final int lgConfigK = getLgConfigK();
    final boolean oooFlag = isOutOfOrderFlag();
    if (lgConfigK <= 12) {
      return RelativeErrorTables.getRelErr(true, oooFlag, lgConfigK, numStdDev)
          * Math.sqrt(1 << lgConfigK);
    }
    final double rseFactor =  (oooFlag) ? HLL_NON_HIP_RSE_FACTOR : HLL_HIP_RSE_FACTOR;
    return rseFactor * numStdDev;
  }

  abstract int getSlot(int slotNo);

  @Override
  int getUpdatableSerializationBytes() {
    final AuxHashMap auxHashMap = getAuxHashMap();
    final int auxBytes = (auxHashMap == null) ? 0 : 4 << auxHashMap.getLgAuxArrInts();
    return HLL_BYTE_ARR_START + getHllByteArrBytes() + auxBytes;
  }

  abstract void putAuxHashMap(AuxHashMap auxHashMap);

  abstract void putCurMin(int curMin);

  abstract void putHipAccum(double hipAccum);

  abstract void putKxQ0(double kxq0);

  abstract void putKxQ1(double kxq1);

  abstract void putNumAtCurMin(int numAtCurMin);

  abstract void putSlot(int slotNo, int value);

  static final int getExpectedLgAuxInts(final int lgConfigK) {
    return LG_AUX_ARR_INTS[lgConfigK];
  }

  static final int hll4ArrBytes(final int lgConfigK) {
    return 1 << (lgConfigK - 1);
  }

  static final int hll6ArrBytes(final int lgConfigK) {
    final int numSlots = 1 << lgConfigK;
    return ((numSlots * 3) >>> 2) + 1;
  }

  static final int hll8ArrBytes(final int lgConfigK) {
    return 1 << lgConfigK;
  }

  static final int curMinAndNum(final AbstractHllArray hllArr) {
    int curMin = 64;
    int numAtCurMin = 0;
    final PairIterator itr = hllArr.getIterator();
    while (itr.nextAll()) {
      final int v = itr.getValue();
      if (v < curMin) {
        curMin = v;
        numAtCurMin = 1;
      }
      if (v == curMin) {
        numAtCurMin++;
      }
    }
    return HllUtil.pair(numAtCurMin, curMin);
  }

  /**
   * HIP and KxQ incremental update.
   * @param oldValue old value
   * @param newValue new value
   */
  //In C: again-two-registers.c Lines 851 to 871
  static final void hipAndKxQIncrementalUpdate(final AbstractHllArray host, final int oldValue,
      final int newValue) {
    assert newValue > oldValue;
    final int configK = 1 << host.getLgConfigK();
    //update hipAccum BEFORE updating kxq0 and kxq1
    double kxq0 = host.getKxQ0();
    double kxq1 = host.getKxQ1();
    host.addToHipAccum(configK / (kxq0 + kxq1));
    //update kxq0 and kxq1; subtract first, then add.
    if (oldValue < 32) { host.putKxQ0(kxq0 -= invPow2(oldValue)); }
    else               { host.putKxQ1(kxq1 -= invPow2(oldValue)); }
    if (newValue < 32) { host.putKxQ0(kxq0 += invPow2(newValue)); }
    else               { host.putKxQ1(kxq1 += invPow2(newValue)); }
  }


}
