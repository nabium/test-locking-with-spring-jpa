package com.nabium.examples.jpa.locking;

import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface StateRepository extends JpaRepository<State, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<State> findForUpdateById(String id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<State> findForShareById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "0"))
    Optional<State> findForUpdateNoWaitById(String id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "0"))
    Optional<State> findForShareNoWaitById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "-2"))
    Optional<State> findForUpdateSkipLockedById(String id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "-2"))
    Optional<State> findForShareSkipLockedById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "2900"))
    Optional<State> findForUpdateWithTimeoutById(String id);

    int countByCensusRegion(CensusRegion censusRegion);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints
    Optional<State> findFirst1ForShareByCensusRegion(CensusRegion censusRegion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "-2"))
    List<State> findForUpdateSkipLockedByCensusRegion(CensusRegion censusRegion);
}
