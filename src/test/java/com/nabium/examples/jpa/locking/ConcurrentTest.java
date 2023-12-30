package com.nabium.examples.jpa.locking;

import static org.assertj.core.api.Assertions.*;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Example usage of {@link ConcurrentTestMixin}.
 */
@Slf4j
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        // We need to start container before WebMVC
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:14:///sjedb",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureTestEntityManager
public class ConcurrentTest implements ConcurrentTestMixin {

    @Getter
    @Autowired
    private TransactionTemplate txTemplate;

    @Getter
    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StateRepository repo;

    @Autowired
    private StateService service;

    private Resource setupStates = new ClassPathResource("/setup_states.sql");

    @Test
    public void test_transactional_method() {
        log.info("ENTER test_transactional_method()");

        testWithTran(() -> {
            // Setup must be run in isolated transaction and committed
            // for service.deleteState() to see the insereted rows.
            runSqlScripts(setupStates);
        }, () -> {
            // New transaction is started by testWithTran().

            // Lock record in current thread/transaction.
            repo.findForShareById("AL").orElseThrow();
            entityManager.clear();

            // Run method which requires lock in separate thread/transaction to make
            // conflict.
            withSingleThreadExecutor(executor -> {
                Future<?> future = executor.submit(() -> service.deleteState("AL"));
                executor.shutdown();

                // service.deleteState() will be waiting for the lock inifinitely.
                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
            });

            // Transaction will be rolled back and lock will be released at the end of the
            // function.
        }, () -> {
            // isolated transaction for cleaning up
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test_non_transactional_repository_method() {
        log.info("ENTER test_non_transactional_repository_method()");

        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById("AL").orElseThrow();
            entityManager.clear();

            withSingleThreadExecutor(executor -> {
                // Methods without transaction enabled cannot be run in separate thread.
                // Use submitWithTran() to wrap those with new transaction and submit.
                Future<Optional<State>> future = submitWithTran(executor, () -> repo.findForUpdateById("AL"));
                executor.shutdown();

                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test_transactional_webmvc_request() {
        log.info("ENTER test_transactional_webmvc_request()");

        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById("AL").orElseThrow();
            entityManager.clear();

            record Problem(String timestamp, int status, String error, String path) {
            }

            // WebMVC server is run in separate thread/transaction,
            // so we do not need withSingleThreadExecutor() to create another thread.
            ResponseEntity<Problem> response = restTemplate.exchange("/state/{id}?noWait=true", HttpMethod.DELETE, null,
                    Problem.class, "AL");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            Problem problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.status()).isEqualTo(500);
            assertThat(problem.error()).isEqualTo("Internal Server Error");
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test_transactional_webmvc_request_with_timeout() {
        log.info("ENTER test_transactional_webmvc_request_with_timeout()");

        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById("AL").orElseThrow();
            entityManager.clear();

            // But, to timeout the request, we need another thread.
            withSingleThreadExecutor(executor -> {
                // Request from other thread.
                Future<?> future = executor
                        .submit(() -> restTemplate.exchange("/state/{id}", HttpMethod.DELETE, null, Void.class, "AL"));
                executor.shutdown();

                // Timeout the request from current thread.
                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    @Transactional
    public void end_transaction_in_middle_of_the_test() {
        log.info("ENTER end_transaction_in_middle_of_the_test()");

        testUsingTestTran(() -> {
            // isolated newly created transaction for setup
            runSqlScripts(setupStates);
        }, () -> {
            // This function is run in test-transaction
            // managed by the @Transactional annotation of the test method.
            // We can use TestTransaction to manage the transaction within.

            repo.findForShareById("AL").orElseThrow();
            entityManager.clear();

            withSingleThreadExecutor(executor -> {

                Future<?> future = executor.submit(() -> service.deleteState("AL"));
                executor.shutdown();

                // Should not return.
                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);

                // sevice.delete() should still be waiting.
                assertThat(future.isDone()).isFalse();

                // End transaction and release the lock.
                TestTransaction.flagForRollback();
                TestTransaction.end();

                // Should return immediately after release.
                future.get(1, TimeUnit.SECONDS);

                // And the data should be deleted.
                TestTransaction.start();
                assertThat(repo.existsById("AL")).isFalse();
            });
        }, () -> {
            // isolated newly created transaction for cleaning up
            runSqlStatements("DELETE FROM state");
        });
    }
}
