package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuildRuntimeCoordinatorTest {
    @Test
    void startGuildSynchronizesInOrderAndSignalsReady() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        ManualExecutor executor = new ManualExecutor();
        TestScheduler scheduler = new TestScheduler();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, scheduler);
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));

        coordinator.startGuild(guild("guild-1"));
        assertThat(api.calls).isEmpty();
        executor.runAll();

        assertThat(api.calls).containsExactly("upsert:guild-1", "emojis:guild-1", "catalogue:guild-1");
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
        assertThat(readyGuilds).containsExactly("guild-1");
    }

    @Test
    void refreshSignalsOnlyFalseToTrueAndReplacesReadyWithUnready() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        api.catalogues.addAll(List.of(unreadyEnvelope(), readyEnvelope(), readyEnvelope(), unreadyEnvelope()));
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        coordinator.refreshGuild(guild);
        coordinator.refreshGuild(guild);
        coordinator.refreshGuild(guild);

        assertThat(readyGuilds).containsExactly("guild-1");
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
        assertThat(cache.findReady("guild-1")).isEmpty();
    }

    @Test
    void recoverySignalsReadyEvenWhenPreviousCatalogueWasReady() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        readyGuilds.clear();

        coordinator.recoverGuild(guild);

        assertThat(readyGuilds).containsExactly("guild-1");
    }

    @Test
    void failedRefreshPreservesLastValidCatalogue() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        CatalogueEnvelope previous = cache.require("guild-1");
        api.loadFailures.add(new ApiException(502, "request_failed", "offline"));
        coordinator.refreshGuild(guild);

        assertThat(cache.find("guild-1")).containsSame(previous);
    }

    @Test
    void malformedCatalogueNeverReplacesLastValidCatalogue() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        CatalogueEnvelope previous = cache.require("guild-1");

        api.catalogues.add(new CatalogueEnvelope(previous.server(), null, previous.catalogue()));
        coordinator.refreshGuild(guild);
        assertThat(cache.find("guild-1")).containsSame(previous);

        api.catalogues.add(new CatalogueEnvelope(
                previous.server(),
                new ApiCatalogueValidation(true, null, List.of()),
                new ApiCatalogue(null, List.of(), List.of(), List.of())
        ));
        coordinator.refreshGuild(guild);

        assertThat(cache.find("guild-1")).containsSame(previous);
    }

    @Test
    void executeRuntimeRecoversInactiveServerAndRetriesExactlyOnce() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        api.calls.clear();
        AtomicInteger attempts = new AtomicInteger();

        String result = coordinator.executeRuntime(guild("guild-1"), () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ApiException(409, "server_inactive", "inactive");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
        assertThat(api.calls).containsExactly("upsert:guild-1", "catalogue:guild-1");
    }

    @Test
    void executeRuntimeRecoversMissingServerButPropagatesSecondFailure() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.executeRuntime(guild("guild-1"), () -> {
            attempts.incrementAndGet();
            throw new ApiException(404, "server_not_found", "missing");
        })).isInstanceOf(ApiException.class).hasMessageContaining("missing");

        assertThat(attempts).hasValue(2);
    }

    @Test
    void executeRuntimeDoesNotRetryWhenRecoveredCatalogueIsUnready() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        cache.put("guild-1", readyEnvelope());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.executeRuntime(guild("guild-1"), () -> {
            attempts.incrementAndGet();
            throw new ApiException(409, "server_inactive", "inactive");
        })).isInstanceOf(GuildNotReadyException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void emojiRefreshRecoversInactiveServerWithoutRequiringReadyCatalogue() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        api.emojiFailures.add(new ApiException(409, "server_inactive", "inactive"));
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        api.calls.clear();
        api.emojiFailures.add(new ApiException(409, "server_inactive", "inactive"));

        coordinator.refreshGuildEmojis(guild("guild-1"));

        assertThat(api.calls).containsExactly(
                "emojis:guild-1", "upsert:guild-1", "catalogue:guild-1", "emojis:guild-1"
        );
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
    }

    @Test
    void leaveRemovesLocalStateCancelsReconciliationAndDeactivates() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        List<String> cancelled = new ArrayList<>();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler(), cancelled);
        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();

        coordinator.leaveGuild("guild-1");

        assertThat(cache.find("guild-1")).isEmpty();
        assertThat(cancelled).containsExactly("guild-1");
        executor.runAll();
        assertThat(api.calls).containsExactly("delete:guild-1");
    }

    @Test
    void transportFailuresRetryAtTenAndSixtySecondsFromDeparture() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.addAll(List.of(
                new ApiException(0, "request_failed", "offline"),
                new ApiException(0, "request_failed", "offline"),
                new ApiException(0, "request_failed", "offline")
        ));
        ManualExecutor executor = new ManualExecutor();
        MutableNanoClock clock = new MutableNanoClock();
        TestScheduler scheduler = new TestScheduler(clock);
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler, new ArrayList<>(), clock);

        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();
        coordinator.leaveGuild("guild-1");
        executor.runAll();
        assertThat(api.calls).containsExactly("delete:guild-1");

        scheduler.advanceToSeconds(9);
        executor.runAll();
        assertThat(api.calls).hasSize(1);
        scheduler.advanceToSeconds(10);
        executor.runAll();
        assertThat(api.calls).hasSize(2);
        scheduler.advanceToSeconds(59);
        executor.runAll();
        assertThat(api.calls).hasSize(2);
        scheduler.advanceToSeconds(60);
        executor.runAll();
        assertThat(api.calls).hasSize(3);
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void missingServerStopsDeactivationRetries() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.add(new ApiException(404, "server_not_found", "missing"));
        ManualExecutor executor = new ManualExecutor();
        TestScheduler scheduler = new TestScheduler();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler);

        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();
        coordinator.leaveGuild("guild-1");
        executor.runAll();

        assertThat(api.calls).containsExactly("delete:guild-1");
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void rejoinCancelsPendingDeleteRetry() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.add(new ApiException(0, "request_failed", "offline"));
        ManualExecutor executor = new ManualExecutor();
        MutableNanoClock clock = new MutableNanoClock();
        TestScheduler scheduler = new TestScheduler(clock);
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler, new ArrayList<>(), clock);
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        executor.runAll();
        coordinator.leaveGuild("guild-1");
        executor.runAll();
        assertThat(api.deleteCalls).hasValue(1);

        coordinator.startGuild(guild);
        executor.runAll();
        scheduler.advanceToSeconds(10);
        executor.runAll();

        assertThat(api.deleteCalls).hasValue(1);
        assertThat(api.calls.getLast()).isEqualTo("catalogue:guild-1");
    }

    @Test
    void refreshQueuedBeforeLeaveCannotWriteAfterDelete() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, new TestScheduler());
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        executor.runAll();
        api.calls.clear();
        coordinator.refreshGuild(guild);
        coordinator.leaveGuild("guild-1");
        executor.runAllReversed();

        assertThat(api.calls).containsExactly("delete:guild-1");
    }

    @Test
    void successfulDeactivationIsTerminalWithoutRetry() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        TestScheduler scheduler = new TestScheduler();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler);

        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();
        coordinator.leaveGuild("guild-1");
        executor.runAll();

        assertThat(api.calls).containsExactly("delete:guild-1");
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void unavailableExpectationWaitsForGuildAvailableBeforeInitialization() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, new TestScheduler());
        Guild guild = guild("guild-1");

        coordinator.expectUnavailableGuild(guild.getId());
        executor.runAll();
        assertThat(api.calls).isEmpty();

        coordinator.guildAvailable(guild);
        executor.runAll();

        assertThat(api.calls).containsExactly("upsert:guild-1", "emojis:guild-1", "catalogue:guild-1");
        assertThat(api.deleteCalls).hasValue(0);
    }

    @Test
    void callsAfterCloseCannotCreateWorkOrExecuteRuntimeSupplier() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        List<String> cancelled = new ArrayList<>();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler(), cancelled);
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        api.calls.clear();
        AtomicInteger runtimeCalls = new AtomicInteger();

        coordinator.close();
        coordinator.startGuild(guild);
        coordinator.refreshGuild(guild);
        coordinator.recoverGuild(guild);
        coordinator.expectUnavailableGuild(guild.getId());
        coordinator.guildAvailable(guild);
        coordinator.leaveGuild(guild.getId());
        coordinator.refreshGuildEmojis(guild);

        assertThatThrownBy(() -> coordinator.executeRuntime(guild, () -> {
            runtimeCalls.incrementAndGet();
            return "unexpected";
        })).isInstanceOf(GuildNotReadyException.class);
        assertThat(runtimeCalls).hasValue(0);
        assertThat(api.calls).isEmpty();
        assertThat(cancelled).isEmpty();
    }

    @Test
    void closeWaitsForAdmittedRefreshBeforeReturning() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        assertThat(waitUntil(() -> cache.find(guild.getId()).isPresent())).isTrue();
        CatalogueEnvelope previous = cache.require(guild.getId());
        api.catalogues.add(unreadyEnvelope());
        api.blockNextCatalogueLoad();

        coordinator.refreshGuild(guild);
        assertThat(api.awaitBlockedCatalogueLoad()).isTrue();
        Thread closeThread = new Thread(coordinator::close, "coordinator-close");
        closeThread.start();
        assertThat(waitUntil(() -> isWaiting(closeThread) || !closeThread.isAlive())).isTrue();
        boolean closeWaited = isWaiting(closeThread);
        api.releaseCatalogueLoad();
        assertThat(api.awaitCompletedCatalogueLoad()).isTrue();
        closeThread.join(2_000);

        assertThat(closeWaited).isTrue();
        assertThat(closeThread.isAlive()).isFalse();
    }

    @Test
    void leaveWaitsForAdmittedRefreshAndKeepsDeleteAsLastWrite() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<String> cancelled = Collections.synchronizedList(new ArrayList<>());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler(), cancelled);
        Guild guild = guild("guild-1");
        List<String> readyGuilds = Collections.synchronizedList(new ArrayList<>());
        coordinator.setReadyHandler(ready -> readyGuilds.add(ready.getId()));
        coordinator.startGuild(guild);
        assertThat(waitUntil(() -> cache.find(guild.getId()).isPresent())).isTrue();
        api.calls.clear();
        api.catalogues.add(readyEnvelope());
        api.blockNextCatalogueLoad();

        coordinator.refreshGuild(guild);
        assertThat(api.awaitBlockedCatalogueLoad()).isTrue();
        Thread leaveThread = new Thread(() -> coordinator.leaveGuild(guild.getId()), "guild-leave");
        leaveThread.start();
        assertThat(waitUntil(() -> isWaiting(leaveThread) || !leaveThread.isAlive())).isTrue();
        boolean leaveWaited = isWaiting(leaveThread);
        assertThat(cache.find(guild.getId())).isEmpty();
        assertThat(cancelled).containsExactly(guild.getId());
        api.releaseCatalogueLoad();
        assertThat(api.awaitCompletedCatalogueLoad()).isTrue();
        leaveThread.join(2_000);
        assertThat(waitUntil(() -> api.deleteCalls.get() == 1)).isTrue();

        assertThat(leaveWaited).isTrue();
        assertThat(leaveThread.isAlive()).isFalse();
        assertThat(cache.find(guild.getId())).isEmpty();
        assertThat(readyGuilds).isEmpty();
        assertThat(api.calls.getLast()).isEqualTo("delete:guild-1");
        coordinator.close();
    }

    @Test
    void closeWinningBeforeRetryAdmissionPreventsSecondRuntimeSupplier() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        api.blockNextCatalogueLoad();
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicReference<Throwable> runtimeFailure = new AtomicReference<>();

        Thread runtimeThread = new Thread(() -> {
            try {
                coordinator.executeRuntime(guild, () -> {
                    if (supplierCalls.incrementAndGet() == 1) {
                        throw new ApiException(409, "server_inactive", "inactive");
                    }
                    return "unexpected";
                });
            } catch (Throwable throwable) {
                runtimeFailure.set(throwable);
            }
        }, "runtime-retry");
        runtimeThread.start();
        assertThat(api.awaitBlockedCatalogueLoad()).isTrue();

        Thread closeThread = new Thread(coordinator::close, "coordinator-close");
        closeThread.start();
        assertThat(waitUntil(() -> isWaiting(closeThread) || !closeThread.isAlive())).isTrue();
        boolean closeWaited = isWaiting(closeThread);
        api.releaseCatalogueLoad();
        runtimeThread.join(2_000);
        closeThread.join(2_000);

        assertThat(closeWaited).isTrue();
        assertThat(supplierCalls).hasValue(1);
        assertThat(runtimeFailure.get()).isInstanceOf(GuildNotReadyException.class);
        assertThat(runtimeThread.isAlive()).isFalse();
        assertThat(closeThread.isAlive()).isFalse();
    }

    @Test
    void leaveWaitsForAdmittedRecoveryPostBeforeReturning() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor lifecycleExecutor = new ManualExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(
                api, new GuildCatalogueCache(), lifecycleExecutor, new TestScheduler()
        );
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        lifecycleExecutor.runAll();
        api.calls.clear();
        api.blockNextUpsert();
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger postOrder = new AtomicInteger();
        AtomicInteger leaveOrder = new AtomicInteger();
        api.beforeUpsertRecord = () -> postOrder.set(sequence.incrementAndGet());
        AtomicReference<Throwable> runtimeFailure = new AtomicReference<>();

        Thread runtimeThread = new Thread(() -> {
            try {
                coordinator.executeRuntime(guild, () -> {
                    throw new ApiException(409, "server_inactive", "inactive");
                });
            } catch (Throwable throwable) {
                runtimeFailure.set(throwable);
            }
        }, "runtime-recovery");
        runtimeThread.start();
        assertThat(api.awaitBlockedUpsert()).isTrue();

        Thread leaveThread = new Thread(() -> {
            coordinator.leaveGuild(guild.getId());
            leaveOrder.set(sequence.incrementAndGet());
        }, "guild-leave");
        leaveThread.start();
        assertThat(waitUntil(() -> isWaiting(leaveThread) || !leaveThread.isAlive())).isTrue();
        boolean leaveWaited = isWaiting(leaveThread);
        api.releaseUpsert();
        runtimeThread.join(2_000);
        leaveThread.join(2_000);

        assertThat(leaveWaited).isTrue();
        assertThat(postOrder).hasValue(1);
        assertThat(leaveOrder).hasValue(2);
        assertThat(runtimeFailure.get()).isInstanceOf(GuildNotReadyException.class);
        assertThat(runtimeThread.isAlive()).isFalse();
        assertThat(leaveThread.isAlive()).isFalse();
        coordinator.close();
    }

    @Test
    void leaveWinningBeforeRecoveryAdmissionPreventsLatePost() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor lifecycleExecutor = new ManualExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(
                api, new GuildCatalogueCache(), lifecycleExecutor, new TestScheduler()
        );
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        lifecycleExecutor.runAll();
        api.calls.clear();
        CountDownLatch holderEntered = new CountDownLatch(1);
        Semaphore holderRelease = new Semaphore(0);
        Thread holderThread = new Thread(() -> coordinator.executeRuntime(guild, () -> {
            holderEntered.countDown();
            holderRelease.acquireUninterruptibly();
            return "held";
        }), "runtime-holder");
        holderThread.start();
        assertThat(holderEntered.await(2, TimeUnit.SECONDS)).isTrue();

        CountDownLatch firstSupplierEntered = new CountDownLatch(1);
        Semaphore firstSupplierRelease = new Semaphore(0);
        AtomicReference<Throwable> recoveryFailure = new AtomicReference<>();
        Thread recoveryThread = new Thread(() -> {
            try {
                coordinator.executeRuntime(guild, () -> {
                    firstSupplierEntered.countDown();
                    firstSupplierRelease.acquireUninterruptibly();
                    throw new ApiException(409, "server_inactive", "inactive");
                });
            } catch (Throwable throwable) {
                recoveryFailure.set(throwable);
            }
        }, "runtime-recovery");
        recoveryThread.start();
        assertThat(firstSupplierEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread leaveThread = new Thread(() -> coordinator.leaveGuild(guild.getId()), "guild-leave");
        leaveThread.start();
        assertThat(waitUntil(() -> isWaiting(leaveThread))).isTrue();
        firstSupplierRelease.release();
        assertThat(waitUntil(() -> isWaiting(recoveryThread) || !recoveryThread.isAlive())).isTrue();
        boolean recoveryWaitedForAdmission = isWaiting(recoveryThread);

        assertThat(recoveryWaitedForAdmission).isTrue();
        assertThat(api.calls).doesNotContain("upsert:guild-1");
        holderRelease.release();
        holderThread.join(2_000);
        recoveryThread.join(2_000);
        leaveThread.join(2_000);
        assertThat(recoveryFailure.get()).isInstanceOf(GuildNotReadyException.class);
        assertThat(api.calls).doesNotContain("upsert:guild-1");
        coordinator.close();
    }

    @Test
    void leaveWaitsForAdmittedReadyHandlerBeforeReturning() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, new TestScheduler());
        CountDownLatch handlerEntered = new CountDownLatch(1);
        Semaphore handlerRelease = new Semaphore(0);
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger handlerOrder = new AtomicInteger();
        AtomicInteger leaveOrder = new AtomicInteger();
        coordinator.setReadyHandler(ignored -> {
            handlerEntered.countDown();
            handlerRelease.acquireUninterruptibly();
            handlerOrder.set(sequence.incrementAndGet());
        });

        coordinator.startGuild(guild("guild-1"));
        assertThat(handlerEntered.await(2, TimeUnit.SECONDS)).isTrue();
        Thread leaveThread = new Thread(() -> {
            coordinator.leaveGuild("guild-1");
            leaveOrder.set(sequence.incrementAndGet());
        }, "guild-leave");
        leaveThread.start();
        assertThat(waitUntil(() -> isWaiting(leaveThread) || !leaveThread.isAlive())).isTrue();
        boolean leaveWaited = isWaiting(leaveThread);
        handlerRelease.release();
        leaveThread.join(2_000);

        assertThat(leaveWaited).isTrue();
        assertThat(handlerOrder).hasValue(1);
        assertThat(leaveOrder).hasValue(2);
        assertThat(leaveThread.isAlive()).isFalse();
        coordinator.close();
    }

    @Test
    void readyHandlerCanReadCurrentCatalogueWithoutDeadlock() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(
                api, new GuildCatalogueCache(), new DirectExecutor(), new TestScheduler()
        );
        AtomicReference<CatalogueEnvelope> observedCatalogue = new AtomicReference<>();
        coordinator.setReadyHandler(guild -> coordinator.findReadyCatalogue(guild.getId())
                .ifPresent(observedCatalogue::set));

        coordinator.startGuild(guild("guild-1"));

        assertThat(observedCatalogue.get()).isSameAs(api.currentCatalogue);
        coordinator.close();
    }

    @Test
    void reentrantLeaveFromReadyHandlerFailsFastInsteadOfDeadlocking() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(
                api, new GuildCatalogueCache(), new DirectExecutor(), new TestScheduler()
        );
        AtomicReference<Throwable> reentrantFailure = new AtomicReference<>();
        coordinator.setReadyHandler(guild -> {
            try {
                coordinator.leaveGuild(guild.getId());
            } catch (Throwable throwable) {
                reentrantFailure.set(throwable);
            }
        });

        coordinator.startGuild(guild("guild-1"));

        assertThat(reentrantFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("leaveGuild");
        coordinator.close();
    }

    @Test
    void closeAlreadyInProgressFailsFastFromAnAdmittedOperation() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(
                api, new GuildCatalogueCache(), new DirectExecutor(), new TestScheduler()
        );
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        CountDownLatch supplierEntered = new CountDownLatch(1);
        Semaphore callNestedClose = new Semaphore(0);
        AtomicReference<Throwable> nestedCloseFailure = new AtomicReference<>();
        AtomicReference<Throwable> runtimeFailure = new AtomicReference<>();

        Thread runtimeThread = Thread.ofPlatform().daemon(true).name("runtime-close").start(() -> {
            try {
                coordinator.executeRuntime(guild, () -> {
                    supplierEntered.countDown();
                    callNestedClose.acquireUninterruptibly();
                    try {
                        coordinator.close();
                    } catch (Throwable throwable) {
                        nestedCloseFailure.set(throwable);
                    }
                    return "ok";
                });
            } catch (Throwable throwable) {
                runtimeFailure.set(throwable);
            }
        });
        assertThat(supplierEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread closeThread = Thread.ofPlatform().daemon(true).name("coordinator-close").start(coordinator::close);
        assertThat(waitUntil(() -> isWaiting(closeThread))).isTrue();
        callNestedClose.release();
        runtimeThread.join(2_000);
        closeThread.join(2_000);

        assertThat(nestedCloseFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("close");
        assertThat(runtimeFailure.get()).isNull();
        assertThat(runtimeThread.isAlive()).isFalse();
        assertThat(closeThread.isAlive()).isFalse();
    }

    @Test
    void leaveWinningBeforeReadyDispatchCancelsSignal() throws Exception {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GatedSecondTaskExecutor executor = new GatedSecondTaskExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        AtomicInteger readyCalls = new AtomicInteger();
        coordinator.setReadyHandler(ignored -> readyCalls.incrementAndGet());
        CountDownLatch holderEntered = new CountDownLatch(1);
        Semaphore holderRelease = new Semaphore(0);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holderThread = null;
        Thread leaveThread = null;

        try {
            coordinator.startGuild(guild("guild-1"));
            assertThat(executor.awaitFirstTaskCompleted()).isTrue();
            assertThat(executor.awaitSecondTaskBlocked()).isTrue();

            holderThread = new Thread(() -> {
                try {
                    coordinator.executeRuntime(guild("guild-1"), () -> {
                        holderEntered.countDown();
                        holderRelease.acquireUninterruptibly();
                        return "held";
                    });
                } catch (Throwable throwable) {
                    holderFailure.set(throwable);
                }
            }, "runtime-holder");
            holderThread.start();
            assertThat(holderEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Thread activeLeaveThread = new Thread(() -> coordinator.leaveGuild("guild-1"), "guild-leave");
            leaveThread = activeLeaveThread;
            activeLeaveThread.start();
            assertThat(waitUntil(() -> isWaiting(activeLeaveThread))).isTrue();
            executor.releaseSecondTask();
            assertThat(executor.awaitWorkerWaiting()).isTrue();
            holderRelease.release();
            holderThread.join(2_000);
            activeLeaveThread.join(2_000);
            assertThat(executor.awaitIdle()).isTrue();

            assertThat(readyCalls).hasValue(0);
            assertThat(holderFailure.get()).isNull();
        } finally {
            holderRelease.release();
            executor.releaseSecondTask();
            if (holderThread != null) {
                holderThread.join(2_000);
            }
            if (leaveThread != null) {
                leaveThread.join(2_000);
            }
            coordinator.close();
        }
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler
    ) {
        return coordinator(api, cache, executor, scheduler, new ArrayList<>());
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler,
            List<String> cancelled
    ) {
        return new GuildRuntimeCoordinator(api, cache, cancelled::add, executor, scheduler);
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler,
            List<String> cancelled,
            LongSupplier nanoTime
    ) {
        return new GuildRuntimeCoordinator(api, cache, cancelled::add, executor, scheduler, nanoTime);
    }

    private static CatalogueEnvelope readyEnvelope() {
        return envelope(true);
    }

    private static CatalogueEnvelope unreadyEnvelope() {
        return envelope(false);
    }

    private static CatalogueEnvelope envelope(boolean ready) {
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Guild", null, new ApiServerSettings(null, null, null)),
                new ApiCatalogueValidation(ready, ready ? List.of() : List.of("role_catalogue_empty"), List.of()),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }

    private static Guild guild(String id) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> "Guild";
                    case "getIconId" -> null;
                    case "getEmojis" -> List.of();
                    case "toString" -> "Guild[" + id + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static boolean isWaiting(Thread thread) {
        return thread.isAlive() && switch (thread.getState()) {
            case BLOCKED, WAITING, TIMED_WAITING -> true;
            default -> false;
        };
    }

    private static final class RecordingBotApiService extends BotApiService {
        private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        private final Queue<CatalogueEnvelope> catalogues = new ConcurrentLinkedQueue<>();
        private final Queue<RuntimeException> loadFailures = new ConcurrentLinkedQueue<>();
        private final Queue<RuntimeException> emojiFailures = new ConcurrentLinkedQueue<>();
        private final Queue<RuntimeException> deactivateFailures = new ConcurrentLinkedQueue<>();
        private final Semaphore catalogueLoadEntered = new Semaphore(0);
        private final Semaphore catalogueLoadRelease = new Semaphore(0);
        private final Semaphore catalogueLoadCompleted = new Semaphore(0);
        private final Semaphore upsertEntered = new Semaphore(0);
        private final Semaphore upsertRelease = new Semaphore(0);
        private volatile CatalogueEnvelope currentCatalogue;
        private volatile boolean blockNextCatalogueLoad;
        private volatile boolean blockNextUpsert;
        private volatile Runnable beforeUpsertRecord = () -> { };
        private final AtomicInteger deleteCalls = new AtomicInteger();

        private RecordingBotApiService(CatalogueEnvelope catalogue) {
            super(null, null);
            currentCatalogue = catalogue;
        }

        @Override
        public DiscordServerEnvelope upsertGuild(Guild guild) {
            if (blockNextUpsert) {
                blockNextUpsert = false;
                upsertEntered.release();
                upsertRelease.acquireUninterruptibly();
            }
            beforeUpsertRecord.run();
            calls.add("upsert:" + guild.getId());
            return new DiscordServerEnvelope(currentCatalogue.server());
        }

        @Override
        public CatalogueEnvelope loadCatalogue(String guildId) {
            calls.add("catalogue:" + guildId);
            if (blockNextCatalogueLoad) {
                blockNextCatalogueLoad = false;
                catalogueLoadEntered.release();
                catalogueLoadRelease.acquireUninterruptibly();
            }
            if (!loadFailures.isEmpty()) {
                throw loadFailures.remove();
            }
            if (!catalogues.isEmpty()) {
                currentCatalogue = catalogues.remove();
            }
            catalogueLoadCompleted.release();
            return currentCatalogue;
        }

        @Override
        public EmojiSnapshotResponse refreshGuildEmojis(Guild guild) {
            calls.add("emojis:" + guild.getId());
            if (!emojiFailures.isEmpty()) {
                throw emojiFailures.remove();
            }
            return new EmojiSnapshotResponse(new EmojiSnapshotResponse.EmojiCache("server", guild.getId(), 0, 0));
        }

        @Override
        public DiscordServerEnvelope deactivateGuild(String guildId) {
            calls.add("delete:" + guildId);
            deleteCalls.incrementAndGet();
            if (!deactivateFailures.isEmpty()) {
                throw deactivateFailures.remove();
            }
            return new DiscordServerEnvelope(currentCatalogue.server());
        }

        private void blockNextCatalogueLoad() {
            blockNextCatalogueLoad = true;
            catalogueLoadCompleted.drainPermits();
        }

        private boolean awaitBlockedCatalogueLoad() throws InterruptedException {
            return catalogueLoadEntered.tryAcquire(2, TimeUnit.SECONDS);
        }

        private void releaseCatalogueLoad() {
            catalogueLoadRelease.release();
        }

        private boolean awaitCompletedCatalogueLoad() throws InterruptedException {
            return catalogueLoadCompleted.tryAcquire(2, TimeUnit.SECONDS);
        }

        private void blockNextUpsert() {
            blockNextUpsert = true;
        }

        private boolean awaitBlockedUpsert() throws InterruptedException {
            return upsertEntered.tryAcquire(2, TimeUnit.SECONDS);
        }

        private void releaseUpsert() {
            upsertRelease.release();
        }
    }

    private static final class GatedSecondTaskExecutor extends ThreadPoolExecutor {
        private final AtomicInteger startedTasks = new AtomicInteger();
        private final CountDownLatch firstTaskCompleted = new CountDownLatch(1);
        private final CountDownLatch secondTaskBlocked = new CountDownLatch(1);
        private final Semaphore secondTaskRelease = new Semaphore(0);
        private volatile Thread workerThread;

        private GatedSecondTaskExecutor() {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable runnable) {
            workerThread = thread;
            if (startedTasks.incrementAndGet() == 2) {
                secondTaskBlocked.countDown();
                secondTaskRelease.acquireUninterruptibly();
            }
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            if (startedTasks.get() == 1) {
                firstTaskCompleted.countDown();
            }
        }

        private boolean awaitFirstTaskCompleted() throws InterruptedException {
            return firstTaskCompleted.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitSecondTaskBlocked() throws InterruptedException {
            return secondTaskBlocked.await(500, TimeUnit.MILLISECONDS);
        }

        private void releaseSecondTask() {
            secondTaskRelease.release();
        }

        private boolean awaitIdle() throws InterruptedException {
            return waitUntil(() -> getActiveCount() == 0 && getQueue().isEmpty());
        }

        private boolean awaitWorkerWaiting() throws InterruptedException {
            return waitUntil(() -> workerThread != null && isWaiting(workerThread));
        }
    }

    private static class ManualExecutor extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor closed");
            }
            tasks.addLast(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }

        void runAllReversed() {
            while (!tasks.isEmpty()) {
                tasks.removeLast().run();
            }
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
    }

    private static final class DirectExecutor extends ManualExecutor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class MutableNanoClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }

        void setSeconds(long seconds) {
            now = TimeUnit.SECONDS.toNanos(seconds);
        }
    }

    private static final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final MutableNanoClock clock;
        private final Queue<ScheduledEntry> tasks = new java.util.PriorityQueue<>(Comparator.comparingLong(ScheduledEntry::targetNanos));
        private boolean shutdown;

        private TestScheduler() {
            this(new MutableNanoClock());
        }

        private TestScheduler(MutableNanoClock clock) {
            this.clock = clock;
        }

        void advanceToSeconds(long seconds) {
            clock.setSeconds(seconds);
            while (!tasks.isEmpty() && tasks.peek().targetNanos() <= clock.getAsLong()) {
                tasks.remove().command().run();
            }
        }

        int pendingTaskCount() {
            return tasks.size();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            TestScheduledFuture<Void> future = new TestScheduledFuture<>();
            tasks.add(new ScheduledEntry(clock.getAsLong() + unit.toNanos(delay), () -> {
                if (!future.isCancelled()) {
                    command.run();
                    future.complete(null);
                }
            }));
            return future;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            TestScheduledFuture<V> future = new TestScheduledFuture<>();
            tasks.add(new ScheduledEntry(clock.getAsLong() + unit.toNanos(delay), () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }));
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            schedule(command, 0, TimeUnit.NANOSECONDS);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.clear();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        private record ScheduledEntry(long targetNanos, Runnable command) {
        }
    }

    private static final class TestScheduledFuture<V> extends CompletableFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }
    }
}
