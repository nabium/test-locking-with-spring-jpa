Test pessimistic locking with Spring DATA JPA
============================================================

Objective
------------------------------------------------------------

1. Test behaviour of `@Lock` with Spring Data JPA.
2. Create utilities for testing concurrent transactions.


Tested with
------------------------------------------------------------

- Spring Boot 3.2.1
- PostgreSQL 16
- MySQL 8.2
- MariaDB 11.2


Summary of `@Lock`
------------------------------------------------------------

### Embedded H2

| LockModeType      | jakarta.persistence.lock.timeout | SQL                   | On conflict             |
|-------------------|----------------------------------|-----------------------|-------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR UPDATE | timeout after 2 seconds |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR UPDATE | timeout after 2 seconds |


### PostgreSQL 16

| LockModeType      | jakarta.persistence.lock.timeout | SQL                                      | On conflict                               |
|-------------------|----------------------------------|------------------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR NO KEY UPDATE             | waits infinitely                          |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR NO KEY UPDATE NOWAIT      | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR NO KEY UPDATE SKIP LOCKED | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR NO KEY UPDATE             | waits infinitely                          |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR SHARE                     | waits infinitely                          |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR SHARE NOWAIT              | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR SHARE SKIP LOCKED         | returns immediately without locked record |


### MySQL 8.2(InnoDB)

| LockModeType      | jakarta.persistence.lock.timeout | SQL                               | On conflict                               |
|-------------------|----------------------------------|-----------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATE             | timeout after 50 seconcds                 |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATE NOWAIT      | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATE SKIP LOCKED | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATE             | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | n/a                              | SELECT ... FOR SHARE              | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | 0                                | SELECT ... FOR SHARE NOWAIT       | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... FOR SHARE SKIP LOCKED  | returns immediately without locked record |


### MariaDB 11.2(InnoDB)

| LockModeType      | jakarta.persistence.lock.timeout | SQL                                       | On conflict                               |
|-------------------|----------------------------------|-------------------------------------------|-------------------------------------------|
| PESSIMISTIC_WRITE | n/a                              | SELECT ... FOR UPDATE                     | timeout after 50 seconcds                 |
| PESSIMISTIC_WRITE | 0                                | SELECT ... FOR UPDATE NOWAIT              | fails immediately                         |
| PESSIMISTIC_WRITE | -2                               | SELECT ... FOR UPDATE SKIP LOCKED         | returns immediately without locked record |
| PESSIMISTIC_WRITE | 2900                             | SELECT ... FOR UPDATE WAIT 3              | timeout after 3 seconcds                  |
| PESSIMISTIC_WRITE | 3100                             | SELECT ... FOR UPDATE WAIT 3              | timeout after 3 seconcds                  |
| PESSIMISTIC_READ  | n/a                              | SELECT ... LOCK IN SHARE MODE             | timeout after 50 seconcds                 |
| PESSIMISTIC_READ  | 0                                | SELECT ... LOCK IN SHARE MODE NO WAIT     | fails immediately                         |
| PESSIMISTIC_READ  | -2                               | SELECT ... LOCK IN SHARE MODE SKIP LOCKED | returns immediately without locked record |
