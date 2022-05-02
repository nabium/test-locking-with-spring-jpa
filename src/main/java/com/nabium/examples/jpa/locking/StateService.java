package com.nabium.examples.jpa.locking;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StateService {

    @Autowired
    private StateRepository repo;

    public List<State> listStates() {
        return repo.findAll();
    }

    public State getState(String id) {
        return repo.getById(id);
    }

    @Transactional
    public State updateState(String id, State state) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(state);
        if (!Objects.equals(id, state.getId())) {
            throw new IllegalArgumentException();
        }

        State entity = repo.findForUpdateById(id).orElseThrow();

        entity.setName(state.getName());
        entity.setCode(state.getCode());
        entity.setAbbr(state.getAbbr());

        return entity;
    }

    @Transactional
    public void deleteState(String id) {
        State entity = repo.findForUpdateById(id).orElseThrow();
        repo.delete(entity);
    }

    @Transactional
    public void deleteStateNoWait(String id) {
        State entity = repo.findForUpdateNoWaitById(id).orElseThrow();
        repo.delete(entity);
    }

    public void deleteAllStates() {
        repo.deleteAll();
    }
}
