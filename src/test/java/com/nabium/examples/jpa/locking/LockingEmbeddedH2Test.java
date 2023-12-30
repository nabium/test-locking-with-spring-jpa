package com.nabium.examples.jpa.locking;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.hibernate.TransactionException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.orm.jpa.JpaSystemException;
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
                // It waits 2000 milliseconds to acquire the lock and on failure,
                // **closes** connection and throws PessimisticLockingFailureException.
                // @Transactional annotaion tries to rollback the transaction but fails with the
                // closed connection and throws JpaSystemException, overriding
                // PessimisticLockingFailureException.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test02_findForUpdateNoWaitById_with_conflict_fails_after_2sec() {
        log.info("ENTER test02_findForUpdateNoWaitById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateNoWaitById(stateId));

                // With H2 Database, SELECT FOR UPDATE NOWAIT is not supported.
                // It's behaviour is same as SELECT FOR UPDATE.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test03_findForUpdateSkipLockedById_with_conflict_fails_after_2sec() {
        log.info(
                "ENTER test03_findForUpdateSkipLockedById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateSkipLockedById(stateId));

                // With H2 Database, SELECT FOR UPDATE SKIP LOCKED is not supported.
                // It's behaviour is same as SELECT FOR UPDATE.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }

    @Test
    public void test05_findForUpdateWithTimeoutById_with_conflict_fails_after_2sec() {
        log.info("ENTER test05_findForUpdateWithTimeoutById_with_conflict_fails_after_2sec()");

        final String stateId = "AL";
        testWithTran(() -> {
            runSqlScripts(setupStates);
        }, () -> {
            repo.findForShareById(stateId).orElseThrow();

            withSingleThreadExecutor(executor -> {
                Instant started = Instant.now();
                Future<?> future = submitWithTran(executor, () -> repo.findForUpdateWithTimeoutById(stateId));

                // With H2 Database, value of the timeout is ignored.
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                        .isExactlyInstanceOf(ExecutionException.class)
                        .cause()
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
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
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

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
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

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
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

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
                        .isExactlyInstanceOf(JpaSystemException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(TransactionException.class)
                        .hasMessage("Unable to rollback against JDBC Connection")
                        .cause()
                        .isExactlyInstanceOf(SQLException.class)
                        .hasMessage("Connection is closed")
                        .hasNoCause();

                // timeouts after 2 seconds, sometimes more
                Duration duration = Duration.between(started, Instant.now());
                assertThat(duration).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
            });
        }, () -> {
            runSqlStatements("DELETE FROM state");
        });
    }
}
