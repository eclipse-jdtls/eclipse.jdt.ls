package org.eclipse.jdt.ls.debug.internal;

import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class EventHub implements AutoCloseable {
    private PublishSubject<DebugEvent> subject = PublishSubject.<DebugEvent>create();

    public Observable<DebugEvent> observable() {
        return subject;
    }

    private Thread workingThread = null;
    private boolean isClosed = false;

    /**
     * @param vm
     *            the target virtual machine.
     */
    public void start(VirtualMachine vm) {
        if (isClosed) {
            throw new IllegalStateException("This event hub is already closed.");
        }

        workingThread = new Thread(() -> {
            EventQueue queue = vm.eventQueue();
            while (true) {
                try {
                    if (Thread.interrupted()) {
                        subject.onComplete();
                        return;
                    }

                    EventSet set = queue.remove();
                    boolean shouldResume = true;
                    for (Event event : set) {
                        DebugEvent dbgEvent = new DebugEvent();
                        dbgEvent.event = event;
                        subject.onNext(dbgEvent);
                        shouldResume &= dbgEvent.shouldResume;
                    }

                    if (shouldResume) {
                        set.resume();
                    }
                } catch (InterruptedException e) {
                    subject.onComplete();
                    return;
                } catch (VMDisconnectedException e) {
                    subject.onError(e);
                    return;
                }
            }
        });

        workingThread.start();
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }

        workingThread.interrupt();
        workingThread = null;
    }
}
