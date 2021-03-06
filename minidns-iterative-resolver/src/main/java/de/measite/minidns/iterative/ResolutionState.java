/*
 * Copyright 2015-2016 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package de.measite.minidns.iterative;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.iterative.IterativeClientException.LoopDetected;
import de.measite.minidns.iterative.IterativeClientException.MaxIterativeStepsReached;

public class ResolutionState {

    private final IterativeDNSClient recursiveDnsClient;
    private final HashMap<InetAddress, Set<Question>> map = new HashMap<>();
    private int steps;

    ResolutionState(IterativeDNSClient recursiveDnsClient) {
        this.recursiveDnsClient = recursiveDnsClient;
    }

    void recurse(InetAddress address, DNSMessage query) throws LoopDetected, MaxIterativeStepsReached {
        Question question = query.getQuestion();
        if (!map.containsKey(address)) {
            map.put(address, new HashSet<Question>());
        } else if (map.get(address).contains(question)) {
            throw new IterativeClientException.LoopDetected();
        }

        if (++steps > recursiveDnsClient.maxSteps) {
            throw new IterativeClientException.MaxIterativeStepsReached(); 
        }

        boolean isNew = map.get(address).add(question);
        assert(isNew);
    }

    void decrementSteps() {
        steps--;
    }

}
