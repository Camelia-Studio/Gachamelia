package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiElement;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.ApiRole;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.ApiStat;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadyListenerTest {
    @Test
    void initializeLoadsMembersSynchronouslyAndStartsEmojiRefresh() {
        RecordingBotApiService botApiService = new RecordingBotApiService();
        RecordingBotEmojiScheduler botEmojiScheduler = new RecordingBotEmojiScheduler();
        ReadyListener listener = new ReadyListener(botApiService, botEmojiScheduler);

        List<String> roleAssignments = new ArrayList<>();
        Role staffRole = role("staff-1", "Staff", Color.BLUE);
        Role rankRole = role("rank-1", "Novice", Color.ORANGE);
        Guild guild = guild(
                "guild-1",
                List.of(member("user-1", "Melaine", List.of(staffRole))),
                Map.of("staff-1", staffRole, "rank-1", rankRole),
                roleAssignments,
                false
        );
        JDA jda = jda(List.of(guild));

        listener.initialize(jda);

        assertThat(botEmojiScheduler.startedWith).isSameAs(jda);
        assertThat(botApiService.initializeGuildCalls).isEqualTo(1);
        assertThat(botApiService.ensureStaffUserCalls).isEqualTo(1);
        assertThat(roleAssignments).containsExactly("user-1->rank-1");
    }

    @Test
    void initializePropagatesMemberLoadFailure() {
        RecordingBotApiService botApiService = new RecordingBotApiService();
        ReadyListener listener = new ReadyListener(botApiService, new RecordingBotEmojiScheduler());
        Guild guild = guild("guild-1", List.of(), Map.of(), new ArrayList<>(), true);

        assertThatThrownBy(() -> listener.initialize(jda(List.of(guild))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("load failure");
    }

    @Test
    void initializeSkipsMemberLoadingWhenCatalogueIsEmpty() {
        RecordingBotApiService botApiService = new RecordingBotApiService(new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(null, null, null)),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        ));
        ReadyListener listener = new ReadyListener(botApiService, new RecordingBotEmojiScheduler());
        Guild guild = guild("guild-1", List.of(), Map.of(), new ArrayList<>(), true);

        listener.initialize(jda(List.of(guild)));

        assertThat(botApiService.initializeGuildCalls).isEqualTo(1);
        assertThat(botApiService.ensureStaffUserCalls).isZero();
    }


    private static JDA jda(List<Guild> guilds) {
        SelfUser selfUser = proxy(
                SelfUser.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAsTag" -> "Gachamelia#0001";
                    case "toString" -> "SelfUser[test]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );

        return proxy(
                JDA.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGuilds" -> guilds;
                    case "getSelfUser" -> selfUser;
                    case "toString" -> "JDA[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Guild guild(
            String guildId,
            List<Member> members,
            Map<String, Role> roles,
            List<String> roleAssignments,
            boolean failOnLoad
    ) {
        Task<List<Member>> task = proxy(
                Task.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> {
                        if (failOnLoad) {
                            throw new IllegalStateException("load failure");
                        }
                        yield members;
                    }
                    case "onSuccess" -> throw new AssertionError("initialize must not rely on Task.onSuccess");
                    case "onError", "setTimeout" -> proxy;
                    case "isStarted" -> true;
                    case "cancel" -> null;
                    case "toString" -> "Task[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        return proxy(
                Guild.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> guildId;
                    case "getName" -> "Gachamélia";
                    case "getIconId" -> "icon";
                    case "loadMembers" -> task;
                    case "getRoleById" -> roles.get(String.valueOf(args[0]));
                    case "addRoleToMember" -> auditableRestAction(() -> roleAssignments.add(((Member) args[0]).getId() + "->" + ((Role) args[1]).getId()));
                    case "toString" -> "Guild[" + guildId + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static Member member(String id, String effectiveName, List<Role> roles) {
        net.dv8tion.jda.api.entities.User user = proxy(
                net.dv8tion.jda.api.entities.User.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getAsTag" -> effectiveName + "#0001";
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

    private static Role role(String id, String name, Color color) {
        net.dv8tion.jda.api.entities.RoleColors colors = new net.dv8tion.jda.api.entities.RoleColors(color.getRGB(), 0, 0);
        return proxy(
                Role.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "getColors" -> colors;
                    case "toString" -> "Role[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static AuditableRestAction<Void> auditableRestAction(Runnable queueCallback) {
        return proxy(
                AuditableRestAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> {
                        queueCallback.run();
                        yield null;
                    }
                    case "submit" -> CompletableFuture.completedFuture(null);
                    case "complete" -> null;
                    case "getJDA", "setCheck", "timeout", "deadline", "reason" -> proxy;
                    case "toString" -> "AuditableRestAction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
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

    private static final class RecordingBotEmojiScheduler extends BotEmojiScheduler {
        private JDA startedWith;

        private RecordingBotEmojiScheduler() {
            super(null, new EmojiSnapshotService());
        }

        @Override
        public void start(JDA jda) {
            startedWith = jda;
        }
    }

    private static final class RecordingBotApiService extends BotApiService {
        private final CatalogueEnvelope catalogueEnvelope;
        private int initializeGuildCalls;
        private int ensureStaffUserCalls;

        private RecordingBotApiService() {
            this(new CatalogueEnvelope(
                    new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(null, null, "staff-1")),
                    new ApiCatalogue(
                            List.of(new ApiRank(1L, "rank-1", "Novice", 100, null, false, List.of(), List.of(), List.of())),
                            List.of(new ApiRole(2L, "Comète", 100, null)),
                            List.of(new ApiStat(3L, "Force")),
                            List.of(new ApiElement(4L, "Ambre", null))
                    )
            ));
        }

        private RecordingBotApiService(CatalogueEnvelope catalogueEnvelope) {
            super(null, null, null);
            this.catalogueEnvelope = catalogueEnvelope;
        }

        @Override
        public CatalogueEnvelope initializeGuild(Guild guild) {
            initializeGuildCalls++;
            return catalogueEnvelope;
        }

        @Override
        public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
            ensureStaffUserCalls++;
            return new UserEnvelope(new ApiUser(
                    1L,
                    userDiscordId,
                    new ApiUser.ApiRankSummary(1L, "rank-1", "Novice", true),
                    new ApiUser.ApiRoleSummary(2L, "Comète"),
                    List.of(),
                    List.of()
            ));
        }
    }
}
