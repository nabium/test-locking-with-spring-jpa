package com.nabium.examples.jpa.locking;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Test locking with MySQL(InnoDB).
 */
@Slf4j
@Testcontainers
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
public class LockingMariaTest implements ConcurrentTestMixin {

    @Getter
    @Autowired
    private TransactionTemplate txTemplate;

    @Getter
    @Autowired
    private DataSource dataSource;

    @Autowired
    private StateRepository repo;

    private Resource setupStates = new ClassPathResource("/setup_states.sql");

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:10.7");

    @DynamicPropertySource
    static void marialProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        // mariadb from Testcontainers is not embedded,
        // we need to explicitly set create-drop
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // to minimize delay at end of tests
        registry.add("spring.datasource.hikari.connection-timeout", () -> "250");
    }

    @Test
    public void test01_findForUpdateById_with_conflict_fails_after_50seconds() {
        log.info("ENTER test01_findForUpdateById_with_conflict_fails_after_50seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateById(stateId));

                // With MariaDB, SELECT FOR UPDATE does not wait infinitely.
                // It waits 50 seconds to acquire the lock then throws error.
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test01_findForUpdateById_with_conflict_fails_after_50seconds()");

                // and timeouts after 50 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(51));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test02_findForUpdateNoWaitById_with_conflict_returns_immediately_with_error() {
        log.info("ENTER test02_findForUpdateNoWaitById_with_conflict_returns_immediately_with_error()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateNoWaitById(stateId));

                // SELECT FOR UPDATE NOWAIT should fail if the record is locked already
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test02_findForUpdateNoWaitById_with_conflict_returns_immediately_with_error()");

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test03_findForUpdateSkipLockedById_with_conflict_fails_after_50seconds() {
        log.info("ENTER test03_findForUpdateSkipLockedById_with_conflict_fails_after_50seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor,
                        () -> repo.findForUpdateSkipLockedById(stateId));

                // With MariaDB, SELECT FOR UPDATE SKIP LOCKED is not supporeted
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test03_findForUpdateSkipLockedById_with_conflict_fails_after_50seconds()");

                // and timeouts after 50 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(51));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test05_findForUpdateWithTimeoutById_with_conflict_fails_after_2seconds() {
        log.info("ENTER test05_findForUpdateWithTimeoutById_with_conflict_fails_after_2seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateWithTimeoutById(stateId));

                // With MariaDB, timeout in millis is truncated to seconds.
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test05_findForUpdateWithTimeoutById_with_conflict_fails_after_2seconds()");

                // and timeouts after 2 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test11_findForShareById_with_conflict_fails_after_50seconds() {
        log.info("ENTER test11_findForShareById_with_conflict_fails_after_50seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareById(stateId));

                // MariaDB uses SELECT LOCK IN SHARE MODE instead of SELECT FOR SHARE.
                // SELECT LOCK IN SHARE MODE does not wait infinitely.
                // It waits 50 seconds to acquire the lock then throws error.
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test11_findForShareById_with_conflict_fails_after_50seconds()");

                // and timeouts after 50 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(51));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test12_findForShareNoWaitById_with_conflict_fails_after_50seconds() {
        log.info("ENTER test12_findForShareNoWaitById_with_conflict_fails_after_50seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareNoWaitById(stateId));

                // With MariaDB, SELECT FOR SHARE NOWAIT is not supporeted
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test12_findForShareNoWaitById_with_conflict_fails_after_50seconds()");

                // and timeouts after 50 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(51));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test13_findForShareSkipLockedById_with_conflict_fails_after_50seconds() {
        log.info("ENTER test13_findForShareSkipLockedById_with_conflict_fails_after_50seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor,
                        () -> repo.findForShareSkipLockedById(stateId));

                // With MariaDB, SELECT FOR SHARE SKIP LOCKED is not supporeted
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);
                log.info("TIMEOUT test13_findForShareSkipLockedById_with_conflict_fails_after_50seconds()");

                // and timeouts after 50 seconds
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(51));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test21_findForShareById_with_conflict_returns_locked_record_immediately() {
        log.info("ENTER test21_findForShareById_with_conflict_returns_locked_record_immediately()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<Optional<State>> future = submitWithTran(executor, () -> repo.findForShareById(stateId));

                // MariaDB uses SELECT LOCK IN SHARE MODE instead of SELECT FOR SHARE
                // SELECT FOR SHARE should return record locked by SELECT FOR SHARE
                Optional<State> result = future.get(5, TimeUnit.SECONDS);
                assertThat(result).isNotEmpty();
                assertThat(result.get().getId()).isEqualTo(stateId);

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }
}
