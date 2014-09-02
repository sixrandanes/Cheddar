/*
 * Copyright 2014 Click Travel Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.clicktravel.cheddar.infrastructure.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.clicktravel.cheddar.infrastructure.messaging.MessageListener;
import com.clicktravel.cheddar.request.context.SecurityContextHolder;

@Component
public class RemoteCallBuilder {

    /**
     * Names of message queues which when processed by a MessageListener and cause remote call commands to be issued,
     * those commands should not be tagged.
     */
    private final Set<String> nonTaggedMessageQueueNames = new HashSet<>();

    @Autowired
    public RemoteCallBuilder(final MessageListener eventMessageListener,
            final MessageListener highPriorityEventMessageListener, final MessageListener remoteCallMessageListener,
            final MessageListener remoteResponseMessageListener, final MessageListener systemEventMessageListener) {
        for (final MessageListener messageListener : new MessageListener[] { eventMessageListener,
                highPriorityEventMessageListener, remoteCallMessageListener, remoteResponseMessageListener,
                systemEventMessageListener }) {
            nonTaggedMessageQueueNames.add(messageListener.queueName());
        }
    }

    public RemoteCall build(final String interfaceName, final Method method, final Object[] args) {
        final List<String> methodParameterTypesList = new ArrayList<>();
        for (final Class<?> parameterClass : method.getParameterTypes()) {
            methodParameterTypesList.add(parameterClass.getName());
        }
        final String[] methodParameterTypesArray = methodParameterTypesList.toArray(new String[0]);
        final String principal = SecurityContextHolder.getPrincipal();
        return new RemoteCall(interfaceName, method.getName(), methodParameterTypesArray, args, principal,
                shouldTagRemoteCall());
    }

    /**
     * Determines if a remote call should be tagged as a command for executing a low-priority domain event handler or
     * other application work queue processing e.g. indexing. Tagged commands are halted early in a blue-green
     * deployment (at LifecycleStatus.HALTING_LOW_PRIORITY_EVENTS status)
     * @return {@code true} if remote call should be tagged
     */
    public boolean shouldTagRemoteCall() {
        final String threadName = Thread.currentThread().getName();
        if (!threadName.startsWith("MessageProcessor:")) {
            return false;
        }
        final String sqsMessageProcessorQueueName = threadName.split(":")[1];
        return !nonTaggedMessageQueueNames.contains(sqsMessageProcessorQueueName);
    }
}