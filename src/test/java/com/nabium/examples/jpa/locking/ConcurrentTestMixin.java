package com.nabium.examples.jpa.locking;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A mixin which provides utilities for concurrent tests.
 *
 * <p>
 * Concurrent tests require multiple threads with transactions.
 * Setup and tear down of the fixtures must be committed in isolated
 * transactions, to let the inserted rows visible to all the transactions in the
 * test.
 * </p>
 *
 * <ul>
 * <li>To run test with setup and tear down each in their own newly created
 * transaction:
 * {@link #testWithTran(ThrowingRunnable, ThrowingRunnable, ThrowingRunnable)}</li>
 * <li>To run test in enclosing test-transaction with setup and tear down each
 * in their own newly created transaction:
 * {@link #testUsingTestTran(ThrowingRunnable, ThrowingRunnable, ThrowingRunnable)}</li>
 * <li>To execute SQL scripts and statemetns:
 * {@link #runSqlScripts(Resource...)} and
 * {@link #runSqlStatements(String...)}</li>
 * <li>To use an {@code ExecutorService} to run test codes in separate thread:
 * {@link #withSingleThreadExecutor(ThrowingConsumer)}</li>
 * <li>To submit a task to the {@code ExecutorService} with newly created
 * transaction:
 * {@link #submitWithTran(ExecutorService, Callable)}
 * </ul>
 *
 * <p>
 * To use the mixin, implement this interface:
 * </p>
 *
 * <pre class="code">
 * &#064;SpringBootTest
 * public class SomeTest implements ConcurrentTestMixin {
 *     &#064;Getter
 *     &#064;Autowired
 *     private TransactionTemplate txTemplate;
 *
 *     &#064;Getter
 *     &#064;Autowired
 *     private DataSource dataSource;
 * }
 * </pre>
 */
public interface ConcurrentTestMixin {

    /**
     * @return {@code TransactionTemplate} used by the mixin to manage transacions
     */
    TransactionTemplate getTxTemplate();

    /**
     * @return {@code DataSource} used by the mixin to execute SQL
     */
    DataSource getDataSource();

    default void runSqlScripts(Resource... scripts) {
        Connection con = DataSourceUtils.getConnection(getDataSource());
        try {
            for (Resource res : scripts) {
                ScriptUtils.executeSqlScript(con, res);
            }
        } finally {
            DataSourceUtils.releaseConnection(con, getDataSource());
        }
    }

    default void runSqlStatements(String... statements) {
        Connection con = DataSourceUtils.getConnection(getDataSource());
        try (Statement stmt = con.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            DataSourceUtils.releaseConnection(con, getDataSource());
        }
    }

    default void runWithTran(ThrowingRunnable task) throws TransactionException {
        runWithTran(task, true);
    }

    default void runWithTran(ThrowingRunnable task, boolean rollback) throws TransactionException {
        getTxTemplate().executeWithoutResult(tx -> {
            try {
                if (rollback) {
                    tx.setRollbackOnly();
                }
                task.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    default <T> T callWithTran(Callable<T> task) throws TransactionException {
        return callWithTran(task, true);
    }

    default <T> T callWithTran(Callable<T> task, boolean rollback) throws TransactionException {
        return getTxTemplate().execute(tx -> {
            try {
                if (rollback) {
                    tx.setRollbackOnly();
                }
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Runs test in newly created transaction.
     *
     * <p>
     * This method takes
     * three functions as parameter, {@code setUp}, {@code test}, {@code tearDown}.
     * Each function runs in their own newly created transaction.
     * </p>
     *
     * <p>
     * {@code setUp} and {@code tearDown} are committed at the end.
     * {@code test} is rolled back at the end as {@code &#064;Transactional} does.
     * </p>
     *
     * <p>
     * {@code tearDown} will be ran even if {@code setUp} or {@code test} throws an
     * exception.
     * </p>
     *
     * @param setUp    function ran before test
     * @param test     function with test code
     * @param tearDown function ran after test
     */
    default void testWithTran(ThrowingRunnable setUp, ThrowingRunnable test, ThrowingRunnable tearDown) {
        try {
            getTxTemplate().setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            runWithTran(setUp, false);
            runWithTran(test, true);
        } finally {
            runWithTran(tearDown, false);
            getTxTemplate().setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        }
    }

    default void testUsingTestTran(ThrowingRunnable setUp, ThrowingRunnable test, ThrowingRunnable tearDown) {
        assertThat(TestTransaction.isActive()).isTrue();
        try {
            getTxTemplate().setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            runWithTran(setUp, false);

            try {
                test.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            runWithTran(tearDown, false);
            getTxTemplate().setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        }
    }

    default void withSingleThreadExecutor(ThrowingConsumer<ExecutorService> body) throws InterruptedException {
        withSingleThreadExecutor(body, 30, TimeUnit.SECONDS);
    }

    default void withSingleThreadExecutor(ThrowingConsumer<ExecutorService> body, long timeout, TimeUnit unit)
            throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            body.accept(executor);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(timeout, unit);
        }
    }

    default <T> Future<T> submitWithTran(ExecutorService executor, Callable<T> task) {
        return executor.submit(() -> callWithTran(task));
    }
}
