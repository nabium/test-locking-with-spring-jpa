package com.nabium.examples.jpa.locking;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Test locking with PostgreSQL.
 */
@Slf4j
@Testcontainers
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
public class LockingPostgresTest implements ConcurrentTestMixin {

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
    static PostgreSQLContainer<?> pgsql = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void pgsqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pgsql::getJdbcUrl);
        registry.add("spring.datasource.username", pgsql::getUsername);
        registry.add("spring.datasource.password", pgsql::getPassword);
        // pgsql from Testcontainers is not embedded,
        // we need to explicitly set create-drop
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // to minimize delay at end of tests
        registry.add("spring.datasource.hikari.connection-timeout", () -> "250");
    }

    @Test
    public void test01_findForUpdateById_with_conflict_waits_infinitely() {
        log.info("ENTER test01_findForUpdateById_with_conflict_waits_infinitely()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateById(stateId));

                // SELECT FOR UPDATE tries to lock the record
                // locked by SELECT FOR SHARE infinitely, or at least for 3 minutes
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
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

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test03_findForUpdateSkipLockedById_with_conflict_returns_immediately_without_data() {
        log.info(
                "ENTER test03_findForUpdateSkipLockedById_with_conflict_returns_immediately_without_data()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<Optional<State>> future = submitWithTran(executor,
                        () -> repo.findForUpdateSkipLockedById(stateId));

                // SELECT FOR UPDATE SKIP LOCKED should return without result
                // if the record is locked already
                Optional<State> result = future.get(5, TimeUnit.SECONDS);
                assertThat(result).isEmpty();

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test04_findForUpdateSkipLockedByCensusRegion_with_conflict_returns_immediately_without_locked_record() {
        log.info(
                "ENTER test04_findForUpdateSkipLockedByCensusRegion_with_conflict_returns_immediately_without_locked_record()");

        final CensusRegion region = CensusRegion.NORTHEAST;
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            // # of states per region
            final int count = repo.countByCensusRegion(region);

            // lock one of the states in the region
            repo.findFirst1ForShareByCensusRegion(region).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                // find states in the region
                Future<List<State>> future = submitWithTran(executor,
                        () -> repo.findForUpdateSkipLockedByCensusRegion(region));

                // SELECT FOR UPDATE SKIP LOCKED should return without the locked record
                List<State> result = future.get(5, TimeUnit.SECONDS);
                assertThat(result.size()).isEqualTo(count - 1);

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test05_findForUpdateWithTimeoutById_with_conflict_waits_infinitely() {
        log.info("ENTER test05_findForUpdateWithTimeoutById_with_conflict_waits_infinitely()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateWithTimeoutById(stateId));

                // With PostgreSQL, value of the timeout is ignored, and waits infinitely.
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test11_findForShareById_with_conflict_waits_infinitely() {
        log.info("ENTER test11_findForShareById_with_conflict_waits_infinitely()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Future<?> future = submitWithTran(executor, () -> repo.findForShareById(stateId));

                // SELECT FOR SHARE tries to lock the record
                // locked by SELECT FOR UPDATE infinitely, or at least for 3 minutes
                assertThatThrownBy(() -> future.get(60, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(TimeoutException.class);
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test12_findForShareNoWaitById_with_conflict_returns_immediately_with_error() {
        log.info("ENTER test12_findForShareNoWaitById_with_conflict_returns_immediately_with_error()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareNoWaitById(stateId));

                // SELECT FOR SHARE NOWAIT should fail if the record is locked already
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .getCause()
                        .isExactlyInstanceOf(PessimisticLockingFailureException.class);

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test13_findForShareSkipLockedById_with_conflict_returns_immediately_without_data() {
        log.info(
                "ENTER test13_findForShareSkipLockedById_with_conflict_returns_immediately_without_data()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<Optional<State>> future = submitWithTran(executor,
                        () -> repo.findForShareSkipLockedById(stateId));

                // SELECT FOR SHARE SKIP LOCKED should return without result
                // if the record is locked already
                Optional<State> result = future.get(5, TimeUnit.SECONDS);
                assertThat(result).isEmpty();

                // and should return immediately
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isLessThan(Duration.ofSeconds(1));
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
