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

package org.apache.datasketches.filters.bloomfilter;

import org.apache.datasketches.common.SketchesArgumentException;
import org.apache.datasketches.memory.WritableMemory;

final class DirectBitArray extends DirectBitArrayR {

  DirectBitArray(final int dataLength, final long storedNumBitsSet, final WritableMemory mem) {
    super(dataLength, 0, mem); // we'll set numBitsSet_ ourselves so pass 0

    // can recompute later if needed
    numBitsSet_ = storedNumBitsSet;
  }

  static DirectBitArray wrap(final WritableMemory mem) {
    final int arrayLength = mem.getInt(0);
    final long storedNumBitsSet = mem.getLong(NUM_BITS_OFFSET);

    if (arrayLength * (long) Long.SIZE > MAX_BITS) {
      throw new SketchesArgumentException("Possible corruption: Serialized image indicates array beyond maximum filter capacity");
    }

    // if empty cannot wrap as writable
    if (storedNumBitsSet == 0) {
      throw new SketchesArgumentException("Cannot wrap an empty filter for writing as there is no backing data array");
    }

    // required capacity is arrayLength plus room for
    // arrayLength (in longs) and numBitsSet
    if (storedNumBitsSet > 0 && mem.getCapacity() < arrayLength + 2) {
      throw new SketchesArgumentException("Memory capacity insufficient for Bloom Filter. Needed: "
        + (arrayLength + 2) + " , found: " + mem.getCapacity());
    }
    return new DirectBitArray(arrayLength, storedNumBitsSet, mem);
  }

  @Override
  long getNumBitsSet() {
    // update numBitsSet and store in array
    if (isDirty()) {
      numBitsSet_ = 0;
      for (int i = 0; i < dataLength_; ++i) {
        numBitsSet_ += Long.bitCount(getLong(i));
      }
      wmem_.putLong(NUM_BITS_OFFSET, numBitsSet_);
    }

    return numBitsSet_;
  }

  @Override
  protected boolean isDirty() {
    return numBitsSet_ == -1;
  }

  @Override
  boolean getBit(final long index) {
    return (wmem_.getByte(DATA_OFFSET + ((int) index >>> 6)) & (1L << index)) != 0 ? true : false;
  }

  @Override
  protected long getLong(final int arrayIndex) {
    return wmem_.getLong(DATA_OFFSET + arrayIndex);
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  void reset() {
    setNumBitsSet(0);
    wmem_.clear(DATA_OFFSET, dataLength_ * Long.BYTES);
  }

  @Override
  void setBit(final long index) {
    final int idx = (int) index >>> 6;
    final long val = getLong(idx);
    setLong(idx, val | 1L << index);
    setNumBitsSet(-1); // mark dirty
  }

  @Override
  boolean getAndSetBit(final long index) {
    final int offset = (int) index >>> 6;
    final long mask = 1L << index;
    final long val = getLong(offset);
    if ((val & mask) != 0) {
      return true; // already seen
    } else {
      setLong(offset, val | mask);
      if (!isDirty()) { setNumBitsSet(numBitsSet_ + 1); }
      return false; // new set
    }
  }

  @Override
  void intersect(final BitArray other) {
    if (getCapacity() != other.getCapacity()) {
      throw new SketchesArgumentException("Cannot intersect bit arrays with unequal lengths");
    }

    numBitsSet_ = 0;
    for (int i = 0; i < dataLength_; ++i) {
      final long val = getLong(i) & other.getLong(i);
      numBitsSet_ += Long.bitCount(val);
      setLong(i, val);
    }
    wmem_.putLong(NUM_BITS_OFFSET, numBitsSet_);
  }

  @Override
  void union(final BitArray other) {
    if (getCapacity() != other.getCapacity()) {
      throw new SketchesArgumentException("Cannot intersect bit arrays with unequal lengths");
    }

    numBitsSet_ = 0;
    for (int i = 0; i < dataLength_; ++i) {
      final long val = getLong(i) | other.getLong(i);
      numBitsSet_ += Long.bitCount(val);
      setLong(i, val);
    }
    wmem_.putLong(NUM_BITS_OFFSET, numBitsSet_);
  }

  @Override
  void invert() {
    if (isDirty()) {
      numBitsSet_ = 0;
      for (int i = 0; i < dataLength_; ++i) {
        final long val = ~getLong(i);
        setLong(i, val);
        numBitsSet_ += Long.bitCount(val);
      }
    } else {
      for (int i = 0; i < dataLength_; ++i) {
        setLong(i, ~getLong(i));
      }
      numBitsSet_ = getCapacity() - numBitsSet_;
    }
    wmem_.putLong(NUM_BITS_OFFSET, numBitsSet_);
  }

  @Override
  protected void setLong(final int arrayIndex, final long value) {
    wmem_.putLong(DATA_OFFSET + arrayIndex, value);
  }

  private final void setNumBitsSet(final long numBitsSet) {
    numBitsSet_ = 0;
    wmem_.putLong(NUM_BITS_OFFSET, numBitsSet);
  }
}
