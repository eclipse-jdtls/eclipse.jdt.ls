/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.debug.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.ls.debug.IBreakpoint;
import org.eclipse.jdt.ls.debug.IEventHub;

import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class Breakpoint implements IBreakpoint {
    private VirtualMachine vm = null;
    private IEventHub eventHub = null;
    private String className = null;
    private int lineNumber = 0;
    private int hitCount = 0;

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber) {
        this(vm, eventHub, className, lineNumber, 0);
    }

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount) {
        this.vm = vm;
        this.eventHub = eventHub;
        this.className = className;
        this.lineNumber = lineNumber;
        this.hitCount = hitCount;
    }

    // IDebugResource
    private List<EventRequest> requests = new ArrayList<EventRequest>();
    private List<Disposable> subscriptions = new ArrayList<Disposable>();

    @Override
    public List<EventRequest> requests() {
        return requests;
    }

    @Override
    public List<Disposable> subscriptions() {
        return subscriptions;
    }

    // AutoCloseable
    @Override
    public void close() throws Exception {
        vm.eventRequestManager().deleteEventRequests(requests());
        subscriptions().forEach(subscription -> {
            subscription.dispose();
        });
    }

    // IBreakpoint
    @Override
    public String className() {
        return className;
    }

    @Override
    public int lineNumber() {
        return lineNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IBreakpoint)) {
            return super.equals(obj);
        }

        IBreakpoint breakpoint = (IBreakpoint)obj;
        return this.className().equals(breakpoint.className()) && this.lineNumber() == breakpoint.lineNumber();
    }

    @Override
    public int hitCount() {
        return hitCount;
    }

    @Override
    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;

        Observable.fromIterable(this.requests())
        .filter(request -> request instanceof BreakpointRequest)
        .subscribe(request -> {
            request.addCountFilter(hitCount);
            request.enable();
        });
    }

    @Override
    public CompletableFuture<IBreakpoint> install() {
        // It's possible that different class loaders create new class with the same name.
        // Here to listen to future class prepare events to handle such case.
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(className);
        classPrepareRequest.enable();
        requests.add(classPrepareRequest);

        // Local types also needs to be handled
        ClassPrepareRequest localClassPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        localClassPrepareRequest.addClassFilter(className + "$*");
        localClassPrepareRequest.enable();
        requests.add(localClassPrepareRequest);

        CompletableFuture<IBreakpoint> future = new CompletableFuture<IBreakpoint>();

        Disposable subscription = eventHub.events()
                .filter(debugEvent -> debugEvent.event.request().equals(classPrepareRequest)
                        || debugEvent.event.request().equals(localClassPrepareRequest))
                .subscribe(debugEvent -> {
                    ClassPrepareEvent event = (ClassPrepareEvent) debugEvent.event;
                    List<BreakpointRequest> newRequests = createBreakpointRequests(event.referenceType(),
                            lineNumber, hitCount);
                    requests.addAll(newRequests);
                    if (!newRequests.isEmpty() && !future.isDone()) {
                        future.complete(this);
                    }
                });
        subscriptions.add(subscription);

        List<ReferenceType> refTypes = vm.classesByName(className);
        requests.addAll(createBreakpointRequests(refTypes, lineNumber, hitCount));
        if (!requests.isEmpty()) {
            future.complete(this);
        }

        return future;
    }

    private static List<Location> collectLocations(ReferenceType refType, int lineNumber) {
        List<Location> locations = new ArrayList<Location>();

        try {
            locations.addAll(refType.locationsOfLine(lineNumber));
        } catch (Exception e) {
            // could be AbsentInformationException or ClassNotPreparedException
            // but both are expected so no need to further handle
        }

        return locations;
    }

    private static List<Location> collectLocations(List<ReferenceType> refTypes, int lineNumber) {
        List<Location> locations = new ArrayList<Location>();
        refTypes.forEach(refType -> {
            locations.addAll(collectLocations(refType, lineNumber));
            locations.addAll(collectLocations(refType.nestedTypes(), lineNumber));
        });

        return locations;
    }

    private List<BreakpointRequest> createBreakpointRequests(ReferenceType refType,
            int lineNumber, int hitCount) {
        List<ReferenceType> refTypes = new ArrayList<ReferenceType>();
        refTypes.add(refType);
        return createBreakpointRequests(refTypes, lineNumber, hitCount);
    }

    private List<BreakpointRequest> createBreakpointRequests(List<ReferenceType> refTypes,
            int lineNumber, int hitCount) {
        List<Location> locations = collectLocations(refTypes, lineNumber);

        // find out the existing breakpoint locations
        List<Location> existingLocations = new ArrayList<Location>(requests.size());
        Observable.fromIterable(requests).filter(request -> request instanceof BreakpointRequest)
                .map(request -> ((BreakpointRequest) request).location()).toList().subscribe(list -> {
                    existingLocations.addAll(list);
                });

        // remove duplicated locations
        List<Location> newLocations = new ArrayList<Location>(locations.size());
        Observable.fromIterable(locations).filter(location -> !existingLocations.contains(location)).toList()
                .subscribe(list -> {
                    newLocations.addAll(list);
                });

        List<BreakpointRequest> newRequests = new ArrayList<BreakpointRequest>(newLocations.size());

        newLocations.forEach(location -> {
            BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
            request.setSuspendPolicy(BreakpointRequest.SUSPEND_EVENT_THREAD);
            if (hitCount > 0) {
                request.addCountFilter(hitCount);
            }
            request.enable();
            newRequests.add(request);
        });

        return newRequests;
    }
}
