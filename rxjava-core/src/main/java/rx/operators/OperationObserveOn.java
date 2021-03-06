/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.operators;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.concurrency.ImmediateScheduler;
import rx.concurrency.Schedulers;
import rx.util.functions.Func1;

public class OperationObserveOn {

    public static <T> Func1<Observer<T>, Subscription> observeOn(Observable<T> source, Scheduler scheduler) {
        return new ObserveOn<T>(source, scheduler);
    }

    private static class ObserveOn<T> implements Func1<Observer<T>, Subscription> {
        private final Observable<T> source;
        private final Scheduler scheduler;

        public ObserveOn(Observable<T> source, Scheduler scheduler) {
            this.source = source;
            this.scheduler = scheduler;
        }

        @Override
        public Subscription call(final Observer<T> observer) {
            if (scheduler instanceof ImmediateScheduler) {
                // do nothing if we request ImmediateScheduler so we don't invoke overhead
                return source.subscribe(observer);
            } else {
                return source.subscribe(new ScheduledObserver<T>(observer, scheduler));
            }
        }
    }

    public static class UnitTest {

        @Test
        @SuppressWarnings("unchecked")
        public void testObserveOn() {

            Scheduler scheduler = spy(OperatorTester.UnitTest.forwardingScheduler(Schedulers.immediate()));

            Observer<Integer> observer = mock(Observer.class);
            Observable.create(observeOn(Observable.from(1, 2, 3), scheduler)).subscribe(observer);

            verify(observer, times(1)).onNext(1);
            verify(observer, times(1)).onNext(2);
            verify(observer, times(1)).onNext(3);
            verify(observer, times(1)).onCompleted();
        }

        @Test
        @SuppressWarnings("unchecked")
        public void testOrdering() throws InterruptedException {
            Observable<String> obs = Observable.from("one", null, "two", "three", "four");

            Observer<String> observer = mock(Observer.class);

            InOrder inOrder = inOrder(observer);

            final CountDownLatch completedLatch = new CountDownLatch(1);
            doAnswer(new Answer<Void>() {

                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    completedLatch.countDown();
                    return null;
                }
            }).when(observer).onCompleted();

            obs.observeOn(Schedulers.threadPoolForComputation()).subscribe(observer);

            if (!completedLatch.await(1000, TimeUnit.MILLISECONDS)) {
                fail("timed out waiting");
            }

            inOrder.verify(observer, times(1)).onNext("one");
            inOrder.verify(observer, times(1)).onNext(null);
            inOrder.verify(observer, times(1)).onNext("two");
            inOrder.verify(observer, times(1)).onNext("three");
            inOrder.verify(observer, times(1)).onNext("four");
            inOrder.verify(observer, times(1)).onCompleted();
            inOrder.verifyNoMoreInteractions();
        }

    }

}
