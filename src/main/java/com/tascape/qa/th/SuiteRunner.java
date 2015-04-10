/*
 * Copyright 2015.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tascape.qa.th;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tascape.qa.th.db.DbHandler;
import com.tascape.qa.th.db.TestCase;
import com.tascape.qa.th.db.TestResult;
import com.tascape.qa.th.suite.AbstractSuite;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import javax.xml.stream.XMLStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public class SuiteRunner {
    private static final Logger LOG = LoggerFactory.getLogger(SuiteRunner.class);

    private TestSuite ts = null;

    private DbHandler db = null;

    private final SystemConfiguration sysConfig = SystemConfiguration.getInstance();

    private final String execId = sysConfig.getExecId();

    private static final Map<String, TestCase> UNSUPPORTED_TESTS = new HashMap<>();

    public synchronized static void addUnspportedTestCase(TestCase tc) {
        UNSUPPORTED_TESTS.put(tc.format(), tc);
    }

    public SuiteRunner(TestSuite testSuite) throws Exception {
        LOG.info("Run suite with execution id {}", execId);
        this.ts = testSuite;

        this.db = DbHandler.getInstance();
        db.queueSuiteExecution(ts, this.execId);
    }

    public int startExecution() throws IOException, InterruptedException, SQLException, XMLStreamException {
        File dir = sysConfig.getLogPath().resolve(execId).toFile();
        LOG.info("Create suite execution log directory {}", dir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory " + dir);
        }

        int threadCount = sysConfig.getExecutionThreadCount();
        LOG.info("Start execution engine with {} thread(s)", threadCount);
        int len = (threadCount + "").length();
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("t%0" + len + "d").build();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount, namedThreadFactory);
        CompletionService<TestResult> completionService = new ExecutorCompletionService<>(executorService);

        LOG.info("Start to acquire test cases to execute");
        int numberOfFailures = 0;
        try {
            List<TestResult> tcrs = this.filter(this.db.getQueuedTestCaseResults(this.execId, 100));
            while (!tcrs.isEmpty()) {
                List<Future<TestResult>> futures = new ArrayList<>();

                for (TestResult tcr : tcrs) {
                    LOG.info("Submit test case {}", tcr.getTestCase().format());
                    futures.add(completionService.submit(new TestRunnerJUnit4(db, tcr)));
                }
                LOG.debug("Total {} test cases submitted", futures.size());

                for (Future<TestResult> f : futures) {
                    try {
                        Future<TestResult> future = completionService.take();
                        TestResult tcr = future.get();
                        if (tcr == null) {
                            continue;
                        }
                        String result = tcr.getResult().result();
                        LOG.info("Get result of test case {} - {}", tcr.getTestCase().format(), result);
                        if (!ExecutionResult.PASS.name().equals(result) && !result.endsWith("/0")) {
                            numberOfFailures++;
                        }
                    } catch (Throwable ex) {
                        LOG.error("Error executing test thread", ex);
                        numberOfFailures++;
                    }
                }

                tcrs = this.filter(this.db.getQueuedTestCaseResults(this.execId, 100));
            }
        } finally {
            AbstractSuite.getSuites().stream().forEach((suite) -> {
                try {
                    suite.tearDown();
                } catch (Exception ex) {
                    LOG.warn("Error tearing down suite {} -  {}", suite.getClass(), ex.getMessage());
                }
            });
        }
        executorService.shutdown();

        LOG.info("No more test case to run on this host, updating suite execution result");
        this.db.updateSuiteExecutionResult(this.execId);
        this.db.saveJunitXml(this.execId);
        return numberOfFailures;
    }

    private List<TestResult> filter(List<TestResult> tcrs) {
        List<TestResult> tcrs0 = new ArrayList<>();
        tcrs.stream().filter((tcr) -> (UNSUPPORTED_TESTS.get(tcr.getTestCase().format()) == null)).forEach((tcr) -> {
            tcrs0.add(tcr);
        });
        return tcrs0;
    }
}