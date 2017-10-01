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

package org.apache.hyracks.storage.am.lsm.btree;

import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleReference;
import org.apache.hyracks.dataflow.common.data.accessors.ITupleReference;
import org.apache.hyracks.dataflow.common.utils.SerdeUtils;
import org.apache.hyracks.dataflow.common.utils.TupleUtils;
import org.apache.hyracks.storage.am.btree.AbstractOperationCallbackTest;
import org.apache.hyracks.storage.am.common.api.IBTreeIndexTupleReference;
import org.apache.hyracks.storage.am.common.impls.NoOpOperationCallback;
import org.apache.hyracks.storage.am.config.AccessMethodTestsConfig;
import org.apache.hyracks.storage.am.lsm.btree.util.LSMBTreeTestHarness;
import org.apache.hyracks.storage.am.lsm.btree.utils.LSMBTreeUtil;
import org.apache.hyracks.storage.am.lsm.common.api.ILSMIndexAccessor;
import org.apache.hyracks.storage.am.lsm.common.impls.BlockingIOOperationCallbackWrapper;
import org.apache.hyracks.storage.am.lsm.common.impls.NoOpIOOperationCallbackFactory;
import org.apache.hyracks.storage.am.lsm.common.impls.NoOpOperationTrackerFactory;
import org.apache.hyracks.storage.common.IIndexAccessor;
import org.apache.hyracks.storage.common.IModificationOperationCallback;
import org.apache.hyracks.util.trace.Tracer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LSMBTreeUpdateInPlaceTest extends AbstractOperationCallbackTest {
    private final LSMBTreeTestHarness harness;
    private final BlockingIOOperationCallbackWrapper ioOpCallback;
    private final ArrayTupleBuilder builder;
    private final ArrayTupleReference tuple;
    private final IModificationOperationCallback cb;

    private IIndexAccessor accessor;
    private boolean isUpdated;
    private boolean isFoundNull;

    public LSMBTreeUpdateInPlaceTest() {
        this.builder = new ArrayTupleBuilder(NUM_KEY_FIELDS);
        this.tuple = new ArrayTupleReference();
        this.cb = new VerifyingUpdateModificationCallback(tuple);
        this.ioOpCallback =
                new BlockingIOOperationCallbackWrapper(NoOpIOOperationCallbackFactory.INSTANCE.createIoOpCallback());
        this.harness = new LSMBTreeTestHarness();
        this.isUpdated = false;
        this.isFoundNull = true;
    }

    @Override
    protected void createIndexInstance() throws Exception {
        index = LSMBTreeUtil.createLSMTree(harness.getIOManager(), harness.getVirtualBufferCaches(),
                harness.getFileReference(), harness.getDiskBufferCache(), SerdeUtils.serdesToTypeTraits(keySerdes),
                SerdeUtils.serdesToComparatorFactories(keySerdes, keySerdes.length), bloomFilterKeyFields,
                harness.getBoomFilterFalsePositiveRate(), harness.getMergePolicy(),
                NoOpOperationTrackerFactory.INSTANCE.getOperationTracker(null), harness.getIOScheduler(),
                harness.getIOOperationCallback(), true, null, null, null, null, true,
                harness.getMetadataPageManagerFactory(), true, Tracer.all());
    }

    @Override
    @Before
    public void setup() throws Exception {
        harness.setUp();
        super.setup();
        accessor = index.createAccessor(cb, NoOpOperationCallback.INSTANCE);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        harness.tearDown();
    }

    interface IndexModification {
        void modify(IIndexAccessor accessor) throws HyracksDataException;
    }

    private void test(IndexModification op1, IndexModification op2) throws Exception {
        ILSMIndexAccessor lsmAccessor = (ILSMIndexAccessor) accessor;
        for (int j = 0; j < 2; j++) {
            index.clear();
            isFoundNull = true;
            isUpdated = false;
            for (int i = 0; i < AccessMethodTestsConfig.BTREE_NUM_TUPLES_TO_INSERT; i++) {
                TupleUtils.createIntegerTuple(builder, tuple, i);
                op1.modify(lsmAccessor);
            }

            if (j == 1) {
                lsmAccessor.scheduleFlush(ioOpCallback);
                ioOpCallback.waitForIO();
                isFoundNull = true;
                isUpdated = false;
            } else {
                isFoundNull = false;
                isUpdated = true;
            }

            for (int i = 0; i < AccessMethodTestsConfig.BTREE_NUM_TUPLES_TO_INSERT; i++) {
                TupleUtils.createIntegerTuple(builder, tuple, i);
                op2.modify(lsmAccessor);
            }

            if (j == 1) {
                lsmAccessor.scheduleFlush(ioOpCallback);
                ioOpCallback.waitForIO();
            } else {
                isFoundNull = false;
            }
        }
    }

    @Test
    public void insertDeleteTest() throws Exception {
        test((IIndexAccessor a) -> a.insert(tuple), (IIndexAccessor a) -> a.delete(tuple));
    }

    @Test
    public void upsertDeleteTest() throws Exception {
        test((IIndexAccessor a) -> a.upsert(tuple), (IIndexAccessor a) -> a.delete(tuple));
    }

    @Test
    public void insertUpsertTest() throws Exception {
        test((IIndexAccessor a) -> a.insert(tuple), (IIndexAccessor a) -> a.upsert(tuple));
    }

    @Test
    public void upsertUpsertTest() throws Exception {
        test((IIndexAccessor a) -> a.upsert(tuple), (IIndexAccessor a) -> a.upsert(tuple));
    }

    private class VerifyingUpdateModificationCallback implements IModificationOperationCallback {

        private final ITupleReference tuple;

        public VerifyingUpdateModificationCallback(ITupleReference tuple) {
            this.tuple = tuple;
        }

        @Override
        public void before(ITupleReference tuple) throws HyracksDataException {
            Assert.assertEquals(0, cmp.compare(this.tuple, tuple));
        }

        @Override
        public void found(ITupleReference before, ITupleReference after) throws HyracksDataException {
            if (isFoundNull) {
                Assert.assertEquals(null, before);
            } else {
                Assert.assertEquals(0, cmp.compare(this.tuple, before));
                Assert.assertEquals(isUpdated, ((IBTreeIndexTupleReference) before).isUpdated());
            }
            Assert.assertEquals(0, cmp.compare(this.tuple, after));
        }
    }

}