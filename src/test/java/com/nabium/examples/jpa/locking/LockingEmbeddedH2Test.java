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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Test locking with Embedded H2 Database.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
public class LockingEmbeddedH2Test implements ConcurrentTestMixin {

    @Getter
    @Autowired
    private TransactionTemplate txTemplate;

    @Getter
    @Autowired
    private DataSource dataSource;

    @Autowired
    private StateRepository repo;

    private Resource setupStates = new ClassPathResource("/setup_states.sql");

    @Test
    public void test01_findForUpdateById_with_conflict_fails_after_2sec() {
        log.info("ENTER test01_findForUpdateById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateById(stateId));

                // With H2 Database, SELECT FOR UPDATE does not wait infinitely.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test02_findForUpdateNoWaitById_with_conflict_fails_immediately() {
        log.info("ENTER test02_findForUpdateNoWaitById_with_conflict_fails_immediately()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateNoWaitById(stateId));

                // H2 Database now suppports SELECT FOR UPDATENOWAIT.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

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

                // H2 Database now supports SELECT FOR UPDATESKIP LOCKED.

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
    public void test05_findForUpdateWithTimeoutById_with_conflict_fails_after_3seconds() {
        log.info("ENTER test05_findForUpdateWithTimeoutById_with_conflict_fails_after_3seconds()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateWithTimeoutById(stateId));

                // H2 Database now supports timeout in order of seconds, 2900 becomes 3.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(3), Duration.ofSeconds(4));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test11_findForShareById_with_conflict_fails_after_2sec() {
        log.info("ENTER test11_findForShareById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareById(stateId));

                // With H2 Database, SELECT FOR SHARE is not supported.
                // SELECT FOR UPDATE is used instead.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test12_findForShareNoWaitById_with_conflict_fails_after_2sec() {
        log.info("ENTER test12_findForShareNoWaitById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareNoWaitById(stateId));

                // With H2 Database, SELECT FOR SHARE NOWAIT is not supported.
                // SELECT FOR UPDATE is used instead.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test13_findForShareSkipLockedById_with_conflict_fails_after_2sec() {
        log.info(
                "ENTER test13_findForShareSkipLockedById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForUpdateById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareSkipLockedById(stateId));

                // With H2 Database, SELECT FOR SHARE SKIP LOCKED is not supported.
                // SELECT FOR UPDATE is used instead.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test21_findForShareById_with_conflict_fails_after_2sec() {
        log.info("ENTER test21_findForShareById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForShareById(stateId));

                // With H2 Database, SELECT FOR SHARE is not supported.
                // SELECT FOR UPDATE is used instead.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(CannotAcquireLockException.class);

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }
}
