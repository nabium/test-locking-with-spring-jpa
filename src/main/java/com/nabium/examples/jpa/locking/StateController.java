package com.nabium.examples.jpa.locking;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/state")
public class StateController {

    @Autowired
    private StateService service;

    @GetMapping
    public List<State> listStates() {
        return service.listStates();
    }

    @GetMapping("/{id}")
    public State getState(@PathVariable String id) {
        return service.getState(id);
    }

    @PutMapping("/{id}")
    public State updateState(@PathVariable String id, @RequestBody State state) {
        return service.updateState(id, state);
    }

    @DeleteMapping("/{id}")
    public void deleteState(@PathVariable String id, @RequestParam(defaultValue = "") String noWait) {
        if (BooleanUtils.toBoolean(noWait)) {
            service.deleteStateNoWait(id);
        } else {
            service.deleteState(id);
        }
    }

    @DeleteMapping
    public void deleteAllStates() {
        service.deleteAllStates();
    }
}
