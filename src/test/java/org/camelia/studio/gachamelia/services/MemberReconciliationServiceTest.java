package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class MemberReconciliationServiceTest {
    @Test
    void requestUsesAsyncMemberCallbackAndSkipsBotsByDefault() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);

            assertThat(task.loadCount()).isEqualTo(1);
            assertThat(api.userIds()).isEmpty();

            task.succeed(List.of(member("human", false, List.of()), member("bot", true, List.of())));
            assertThat(api.userIds()).isEmpty();

            executor.runAll();

            assertThat(api.userIds()).containsExactly("human");
        } finally {
            service.close();
        }
    }

    @Test
    void requestIncludesBotsWhenConfigured() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, true, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            task.succeed(List.of(member("human", false, List.of()), member("bot", true, List.of())));
            executor.runAll();

            assertThat(api.userIds()).containsExactly("human", "bot");
        } finally {
            service.close();
        }
    }

    @Test
    void requestsAcrossGuildsNeverExceedGlobalConcurrency() throws InterruptedException {
        RecordingBotApiService api = new RecordingBotApiService();
        api.blockCalls(2);
        GuildCatalogueCache cache = readyCache("guild-1", null);
        cache.put("guild-2", readyEnvelope(null));
        ControlledMemberTask firstTask = new ControlledMemberTask();
        ControlledMemberTask secondTask = new ControlledMemberTask();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });

        try {
            service.request(guild("guild-1", firstTask, Map.of(), new ArrayList<>()));
            service.request(guild("guild-2", secondTask, Map.of(), new ArrayList<>()));
            firstTask.succeed(List.of(member("one", false, List.of()), member("two", false, List.of())));
            secondTask.succeed(List.of(member("three", false, List.of()), member("four", false, List.of())));

            assertThat(api.awaitBlockedCalls()).isTrue();
            assertThat(api.maximumConcurrency()).isLessThanOrEqualTo(2);

            api.releaseCalls();
            assertThat(api.awaitCompletedCalls(4)).isTrue();
            assertThat(api.maximumConcurrency()).isLessThanOrEqualTo(2);
        } finally {
            api.releaseCalls();
            service.close();
        }
    }

    @Test
    void requestWhileRunIsActiveStartsExactlyOneTrailingRun() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            service.request(guild);
            service.request(guild);

            task.succeed(List.of(member("human", false, List.of())));
            executor.runAll();

            assertThat(task.loadCount()).isEqualTo(2);
        } finally {
            service.close();
        }
    }

    @Test
    void failedMemberLoadCompletesRunAndStartsTrailingRequest() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            service.request(guild);

            task.fail(new IllegalStateException("load failed"));

            assertThat(task.loadCount()).isEqualTo(2);
        } finally {
            service.close();
        }
    }

    @Test
    void readyCheckPreventsTasksFromCallingApiAfterCatalogueTurnsUnready() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            cache.put("guild-1", unreadyEnvelope(null));
            task.succeed(List.of(member("human", false, List.of())));
            executor.runAll();

            assertThat(api.userIds()).isEmpty();
        } finally {
            service.close();
        }
    }

    @Test
    void cancelInvalidatesCallbacksFromAnOlderRun() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            service.cancel("guild-1");
            cache.put("guild-1", readyEnvelope(null));

            task.succeed(List.of(member("human", false, List.of())));
            executor.runAll();

            assertThat(api.userIds()).isEmpty();
        } finally {
            service.close();
        }
    }

    @Test
    void recoverableMemberFailureRequestsRecoveryOnceAndAbortsUnstartedTasks() {
        RecordingBotApiService api = new RecordingBotApiService();
        api.failFirstCallWith(new ApiException(409, "server_inactive", "inactive"));
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        List<String> recoveryGuilds = new ArrayList<>();
        MemberReconciliationService service = service(api, cache, false, executor, guild -> recoveryGuilds.add(guild.getId()));
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            task.succeed(List.of(
                    member("first", false, List.of()),
                    member("second", false, List.of()),
                    member("third", false, List.of())
            ));
            executor.runAll();

            assertThat(api.userIds()).containsExactly("first");
            assertThat(recoveryGuilds).containsExactly("guild-1");
        } finally {
            service.close();
        }
    }

    @Test
    void ordinaryMemberFailureDoesNotAbortOtherMembers() {
        RecordingBotApiService api = new RecordingBotApiService();
        api.failFirstCallWith(new ApiException(502, "api_user_missing", "missing user"));
        GuildCatalogueCache cache = readyCache("guild-1", null);
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        List<String> recoveryGuilds = new ArrayList<>();
        MemberReconciliationService service = service(api, cache, false, executor, guild -> recoveryGuilds.add(guild.getId()));
        Guild guild = guild("guild-1", task, Map.of(), new ArrayList<>());

        try {
            service.request(guild);
            task.succeed(List.of(member("first", false, List.of()), member("second", false, List.of())));
            executor.runAll();

            assertThat(api.userIds()).containsExactly("first", "second");
            assertThat(recoveryGuilds).isEmpty();
        } finally {
            service.close();
        }
    }

    @Test
    void staffMembersUseStaffEnsureAndReceiveTheirRankRole() {
        RecordingBotApiService api = new RecordingBotApiService();
        GuildCatalogueCache cache = readyCache("guild-1", "staff-role");
        ControlledMemberTask task = new ControlledMemberTask();
        ManualExecutorService executor = new ManualExecutorService();
        List<String> assignments = new ArrayList<>();
        Role staffRole = role("staff-role");
        Role rankRole = role("rank-role");
        Guild guild = guild(
                "guild-1",
                task,
                Map.of(staffRole.getId(), staffRole, rankRole.getId(), rankRole),
                assignments
        );
        MemberReconciliationService service = service(api, cache, false, executor, ignored -> { });

        try {
            service.request(guild);
            task.succeed(List.of(member("staff", false, List.of(staffRole))));
            executor.runAll();

            assertThat(api.staffUserIds()).containsExactly("staff");
            assertThat(assignments).containsExactly("staff->rank-role");
        } finally {
            service.close();
        }
    }

    private static MemberReconciliationService service(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            boolean syncBotMembers,
            ExecutorService executor,
            Consumer<Guild> recoveryRequest
    ) {
        return new MemberReconciliationService(api, cache, syncBotMembers, recoveryRequest, executor);
    }

    private static GuildCatalogueCache readyCache(String guildId, String staffRoleId) {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put(guildId, readyEnvelope(staffRoleId));
        return cache;
    }

    private static CatalogueEnvelope readyEnvelope(String staffRoleId) {
        return envelope(true, staffRoleId);
    }

    private static CatalogueEnvelope unreadyEnvelope(String staffRoleId) {
        return envelope(false, staffRoleId);
    }

    private static CatalogueEnvelope envelope(boolean ready, String staffRoleId) {
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Guild", null, new ApiServerSettings(null, null, staffRoleId)),
                new ApiCatalogueValidation(ready, List.of(), List.of()),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }

    private static Guild guild(
            String guildId,
            ControlledMemberTask members,
            Map<String, Role> roles,
            List<String> assignments
    ) {
        return proxy(
                Guild.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> guildId;
                    case "loadMembers" -> members.load();
                    case "getRoleById" -> roles.get(String.valueOf(args[0]));
                    case "addRoleToMember" -> auditableRestAction(() -> assignments.add(
                            ((Member) args[0]).getId() + "->" + ((Role) args[1]).getId()
                    ));
                    case "toString" -> "Guild[" + guildId + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static Member member(String id, boolean bot, List<Role> roles) {
        User user = proxy(
                User.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getAsTag" -> id + "#0001";
                    case "isBot" -> bot;
                    case "toString" -> "User[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
        return proxy(
                Member.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getUser" -> user;
                    case "getRoles" -> roles;
                    case "toString" -> "Member[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static Role role(String id) {
        return proxy(
                Role.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "toString" -> "Role[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static AuditableRestAction<Void> auditableRestAction(Runnable callback) {
        return proxy(
                AuditableRestAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> {
                        callback.run();
                        yield null;
                    }
                    case "toString" -> "AuditableRestAction[test]";
                    default -> defaultValue(method.getReturnType(), proxy);
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultSnowflakeOrUnsupported(String methodName) {
        return switch (methodName) {
            case "getIdLong" -> 1L;
            case "hashCode" -> 1;
            case "equals" -> false;
            case "compareTo" -> 0;
            default -> throw new UnsupportedOperationException(methodName);
        };
    }

    private static Object defaultValue(Class<?> type, Object proxy) {
        if (!type.isPrimitive()) {
            return type.isAssignableFrom(proxy.getClass()) ? proxy : null;
        }
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

    @SuppressWarnings("unchecked")
    private static final class ControlledMemberTask {
        private final Task<List<Member>> task;
        private Consumer<List<Member>> success;
        private Consumer<Throwable> error;
        private int loadCount;

        private ControlledMemberTask() {
            task = proxy(
                    Task.class,
                    (proxy, method, args) -> switch (method.getName()) {
                        case "onSuccess" -> {
                            success = castConsumer(args[0]);
                            yield proxy;
                        }
                        case "onError" -> {
                            error = castConsumer(args[0]);
                            yield proxy;
                        }
                        case "setTimeout" -> proxy;
                        case "isStarted" -> false;
                        case "cancel" -> null;
                        case "toString" -> "ControlledMemberTask";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private Task<List<Member>> load() {
            loadCount++;
            return task;
        }

        private int loadCount() {
            return loadCount;
        }

        private void succeed(List<Member> members) {
            assertThat(success).as("member load callback").isNotNull();
            success.accept(members);
        }

        @SuppressWarnings("unused")
        private void fail(Throwable throwable) {
            assertThat(error).as("member load error callback").isNotNull();
            error.accept(throwable);
        }

        @SuppressWarnings("unchecked")
        private static <T> Consumer<T> castConsumer(Object value) {
            return (Consumer<T>) value;
        }
    }

    private static final class RecordingBotApiService extends BotApiService {
        private final List<String> userIds = Collections.synchronizedList(new ArrayList<>());
        private final List<String> staffUserIds = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger currentConcurrency = new AtomicInteger();
        private final AtomicInteger maximumConcurrency = new AtomicInteger();
        private volatile CountDownLatch blockedCalls;
        private volatile CountDownLatch releaseCalls;
        private volatile CountDownLatch completedCalls;
        private volatile RuntimeException firstFailure;

        private RecordingBotApiService() {
            super(null, null, null);
        }

        private void blockCalls(int expectedCalls) {
            blockedCalls = new CountDownLatch(expectedCalls);
            releaseCalls = new CountDownLatch(1);
            completedCalls = new CountDownLatch(4);
        }

        private boolean awaitBlockedCalls() throws InterruptedException {
            return blockedCalls.await(2, TimeUnit.SECONDS);
        }

        private void releaseCalls() {
            CountDownLatch latch = releaseCalls;
            if (latch != null) {
                latch.countDown();
            }
        }

        private boolean awaitCompletedCalls(int expectedCalls) throws InterruptedException {
            CountDownLatch latch = completedCalls;
            return latch != null && latch.await(2, TimeUnit.SECONDS);
        }

        private void failFirstCallWith(RuntimeException failure) {
            firstFailure = failure;
        }

        private List<String> userIds() {
            return userIds;
        }

        private List<String> staffUserIds() {
            return staffUserIds;
        }

        private int maximumConcurrency() {
            return maximumConcurrency.get();
        }

        @Override
        public UserEnvelope ensureUser(String guildId, String userDiscordId) {
            userIds.add(userDiscordId);
            return ensure(userDiscordId);
        }

        @Override
        public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
            staffUserIds.add(userDiscordId);
            return ensure(userDiscordId);
        }

        private UserEnvelope ensure(String userDiscordId) {
            RuntimeException failure = firstFailure;
            if (failure != null) {
                firstFailure = null;
                throw failure;
            }

            int current = currentConcurrency.incrementAndGet();
            maximumConcurrency.accumulateAndGet(current, Math::max);
            try {
                CountDownLatch started = blockedCalls;
                if (started != null) {
                    started.countDown();
                    assertThat(releaseCalls.await(2, TimeUnit.SECONDS)).isTrue();
                }
                return userEnvelope(userDiscordId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                currentConcurrency.decrementAndGet();
                CountDownLatch completed = completedCalls;
                if (completed != null) {
                    completed.countDown();
                }
            }
        }
    }

    private static UserEnvelope userEnvelope(String userDiscordId) {
        return new UserEnvelope(new ApiUser(
                1L,
                userDiscordId,
                new ApiUser.ApiRankSummary(1L, "rank-role", "Rank", false),
                null,
                List.of(),
                List.of()
        ));
    }

    private static final class ManualExecutorService extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(tasks);
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

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor closed");
            }
            tasks.add(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }
}
