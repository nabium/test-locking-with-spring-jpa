Test pessimistic locking with Spring DATA JPA
============================================================

Objective
------------------------------------------------------------

1. Test behaviour of `@Lock` with Spring Data JPA.
2. Create utilities for testing concurrent transactions.


Summary of `@Lock`
------------------------------------------------------------

### Embedded H2

| LockModeType      | javax.persistence.lock.timeout | SQL                   | On conflict          |
|-------------------|--------------------------------|-----------------------|----------------------|
| PESSIMISTIC_WRITE | n/a                            | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_WRITE | 0                              | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_WRITE | -2                             | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_WRITE | 2900                           | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_READ  | n/a                            | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_READ  | 0                              | SELECT ... FOR UPDATE | timeout after 4 msec |
| PESSIMISTIC_READ  | -2                             | SELECT ... FOR UPDATE | timeout after 4 msec |


### PostgreSQL 14

| LockModeType      | javax.persistence.lock.timeout | SQL                               | On conflict                              |
|-------------------|--------------------------------|-----------------------------------|------------------------------------------|
| PESSIMISTIC_WRITE | n/a                            | SELECT ... FOR UPDATE             | waits infinitely                         |
| PESSIMISTIC_WRITE | 0                              | SELECT ... FOR UPDATE NOWAIT      | fails immediately                        |
| PESSIMISTIC_WRITE | -2                             | SELECT ... FOR UPDATE SKIP LOCKED | return without locked record immediately |
| PESSIMISTIC_WRITE | 2900                           | SELECT ... FOR UPDATE             | waits infinitely                         |
| PESSIMISTIC_READ  | n/a                            | SELECT ... FOR SHARE              | waits infinitely                         |
| PESSIMISTIC_READ  | 0                              | SELECT ... FOR SHARE NOWAIT       | fails immediately                        |
| PESSIMISTIC_READ  | -2                             | SELECT ... FOR SHARE SKIP LOCKED  | return without locked record immediately |


### MySQL 8(InnoDB)

| LockModeType      | javax.persistence.lock.timeout | SQL                               | On conflict                              |
|-------------------|--------------------------------|-----------------------------------|------------------------------------------|
| PESSIMISTIC_WRITE | n/a                            | SELECT ... FOR UPDATE             | timeout after 50 seconcds                |
| PESSIMISTIC_WRITE | 0                              | SELECT ... FOR UPDATE NOWAIT      | fails immediately                        |
| PESSIMISTIC_WRITE | -2                             | SELECT ... FOR UPDATE SKIP LOCKED | return without locked record immediately |
| PESSIMISTIC_WRITE | 2900                           | SELECT ... FOR UPDATE             | timeout after 50 seconcds                |
| PESSIMISTIC_READ  | n/a                            | SELECT ... FOR SHARE              | timeout after 50 seconcds                |
| PESSIMISTIC_READ  | 0                              | SELECT ... FOR SHARE NOWAIT       | fails immediately                        |
| PESSIMISTIC_READ  | -2                             | SELECT ... FOR SHARE SKIP LOCKED  | return without locked record immediately |


### MySQL 5.7(InnoDB)

| LockModeType      | javax.persistence.lock.timeout | SQL                           | On conflict               |
|-------------------|--------------------------------|-------------------------------|---------------------------|
| PESSIMISTIC_WRITE | n/a                            | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_WRITE | 0                              | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_WRITE | -2                             | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_WRITE | 2900                           | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_READ  | n/a                            | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |
| PESSIMISTIC_READ  | 0                              | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |
| PESSIMISTIC_READ  | -2                             | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |


### MariaDB 10.7(InnoDB)

| LockModeType      | javax.persistence.lock.timeout | SQL                           | On conflict               |
|-------------------|--------------------------------|-------------------------------|---------------------------|
| PESSIMISTIC_WRITE | n/a                            | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_WRITE | 0                              | SELECT ... FOR UPDATE NOWAIT  | fails immediately         |
| PESSIMISTIC_WRITE | -2                             | SELECT ... FOR UPDATE         | timeout after 50 seconcds |
| PESSIMISTIC_WRITE | 2900                           | SELECT ... FOR UPDATE WAIT 2  | timeout after 2 seconcds  |
| PESSIMISTIC_READ  | n/a                            | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |
| PESSIMISTIC_READ  | 0                              | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |
| PESSIMISTIC_READ  | -2                             | SELECT ... LOCK IN SHARE MODE | timeout after 50 seconcds |
