Test pessimistic locking with Spring DATA JPA
============================================================

Objective
------------------------------------------------------------

1. Test behaviour of `@Lock` with Spring Data JPA.
2. Create utilities for testing concurrent transactions.


Tested with
------------------------------------------------------------

- Spring Boot 4.1.1
- PostgreSQL 18
- MySQL 9.7
- MariaDB 13.0


Summary of `@Lock`
------------------------------------------------------------

### Embedded H2

| LockModeType      | jakarta.persistence.lock.timeout | SQL                              | On conflict                               |
|-------------------|----------------------------------|----------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATE            | timeout after 2 seconds                   |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATENOWAIT      | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATESKIP LOCKED | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATEWAIT 3      | timeout after 3 seconds                   |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR UPDATE            | timeout after 2 seconds                   |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR UPDATE            | timeout after 2 seconds                   |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR UPDATE            | timeout after 2 seconds                   |


### PostgreSQL 18

| LockModeType      | jakarta.persistence.lock.timeout | SQL                                      | On conflict                               |
|-------------------|----------------------------------|------------------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR NO KEY UPDATE             | waits infinitely                          |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR NO KEY UPDATE NOWAIT      | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR NO KEY UPDATE SKIP LOCKED | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SET LOCAL LOCK_TIMEOUT = 2900; SELECT ... FOR NO KEY UPDATE | timeout after 2.9 seconds |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR SHARE                     | waits infinitely                          |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR SHARE NOWAIT              | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR SHARE SKIP LOCKED         | returns immediately without locked record |


### MySQL 9.7(InnoDB)

| LockModeType      | jakarta.persistence.lock.timeout | SQL                                 | On conflict                               |
|-------------------|----------------------------------|-------------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATEOF             | timeout after 50 seconcds                 |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATEOF NOWAIT      | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATEOF SKIP LOCKED | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATEOF             | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR SHARE                | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR SHARE NOWAIT         | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR SHARE SKIP LOCKED    | returns immediately without locked record |


### MariaDB 13.0(InnoDB)

| LockModeType      | jakarta.persistence.lock.timeout | SQL                                       | On conflict                               |
|-------------------|----------------------------------|-------------------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATE                     | timeout after 50 seconcds                 |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATENOWAIT               | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATESKIP LOCKED          | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATEWAIT 3               | timeout after 3 seconcds                  |
| PESSIMISTIC_WRITE | 3100                             | SELECT ... FOR UPDATEWAIT 3               | timeout after 3 seconcds                  |
| PESSIMISTIC_READ  | n/a                              | SELECT ... LOCK IN SHARE MODE             | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | 0                                | SELECT ... LOCK IN SHARE MODE NOWAIT      | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... LOCK IN SHARE MODE SKIP LOCKED | returns immediately without locked record |
