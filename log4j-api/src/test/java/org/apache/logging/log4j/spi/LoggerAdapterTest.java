/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.simple.SimpleLoggerContext;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Created by Pavel.Sivolobtchik@uxpsystems.com on 2016-10-19.
 */
public class LoggerAdapterTest {

    private class RunnableThreadTest implements Runnable {
        private AbstractLoggerAdapter<Logger> adapter;
        private LoggerContext context;
        private CountDownLatch doneSignal;
        private int index;
        private Map<String, Logger> resultMap;

        private CountDownLatch startSignal;

        public RunnableThreadTest(int index, TestLoggerAdapter adapter, LoggerContext context,
                CountDownLatch startSignal, CountDownLatch doneSignal) {
            this.adapter = adapter;
            this.context = context;
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
            this.index = index;
        }

        public Map<String, Logger> getResultMap() {
            return resultMap;
        }

        @Override
        public void run() {
            try {
                startSignal.await();
                resultMap = adapter.getLoggersInContext(context);
                resultMap.put(String.valueOf(index), new TestLogger());
                doneSignal.countDown();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static class TestLogger extends Logger {
        public TestLogger() {
            super("test", null);
        }
    }

    private static class TestLoggerAdapter extends AbstractLoggerAdapter<Logger> {

        @Override
        protected LoggerContext getContext() {
            return null;
        }

        @Override
        protected Logger newLogger(String name, LoggerContext context) {
            return null;
        }
    }

    /**
     * Testing synchronization in the getLoggersInContext() method
     */
    @Test
    public synchronized void testGetLoggersInContextSynch() throws Exception {
        TestLoggerAdapter adapter = new TestLoggerAdapter();

        int num = 500;

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(num);

        RunnableThreadTest[] instances = new RunnableThreadTest[num];
        LoggerContext lastUsedContext = null;
        for (int i = 0; i < num; i++) {
            if (i % 2 == 0) {
                //every other time create a new context
                lastUsedContext = new SimpleLoggerContext();
            }
            RunnableThreadTest runnable = new RunnableThreadTest(i, adapter, lastUsedContext, startSignal, doneSignal);
            Thread thread = new Thread(runnable);
            thread.start();
            instances[i] = runnable;
        }

        startSignal.countDown();
        doneSignal.await();

        for (int i = 0; i < num; i = i + 2) {
            //maps for the same context should be the same instance
            Map<String, Logger> resultMap1 = instances[i].getResultMap();
            Map<String, Logger> resultMap2 = instances[i + 1].getResultMap();
            assertSame("not the same map for instances" + i + " and " + (i + 1) + ":", resultMap1, resultMap2);
            assertEquals(2, resultMap1.size());
        }
    }
}