package org.camelia.studio.gachamelia.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.RoleColors;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateRolesEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiElement;
import org.camelia.studio.gachamelia.api.dto.ApiEmoji;
import org.camelia.studio.gachamelia.api.dto.ApiMessage;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.ApiRankStat;
import org.camelia.studio.gachamelia.api.dto.ApiRole;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.ApiStat;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.ApiUserStat;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.commands.personnage.FichePersoCommand;
import org.camelia.studio.gachamelia.listeners.GuildEmojiListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberJoinListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberLeaveListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberRoleChangeListener;
import org.camelia.studio.gachamelia.managers.CommandManager;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RuntimeListenersAndCommandsTest {
    @Test
    void commandManagerRegistersGlobalCommands() {
        List<Collection<? extends CommandData>> registeredCommands = new ArrayList<>();
        JDA jda = jdaForCommandRegistration(registeredCommands);

        CommandManager manager = new CommandManager(new RecordingBotApiService(sampleUserEnvelope()), catalogueCacheWith(sampleCatalogue("10", "11", "12")));

        manager.registerCommands(jda);

        assertThat(registeredCommands).hasSize(1);
        assertThat(registeredCommands.getFirst())
                .extracting(CommandData::getName)
                .containsExactly("ficheperso", "ping");
    }

    @Test
    void fichePersoUsesApiPayloadScopedByGuild() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        FichePersoCommand command = new FichePersoCommand(botApiService, cache);

        CapturedMessages capturedMessages = new CapturedMessages();
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", role("99", "Novice", Color.ORANGE)), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        SlashCommandInteractionEvent event = slashCommandEvent("ficheperso", guild, member, null, capturedMessages);

        command.execute(event);

        assertThat(botApiService.ensureUserCalls).isEqualTo(1);
        assertThat(botApiService.lastEnsureGuildId).isEqualTo("guild-1");
        assertThat(botApiService.lastEnsureUserId).isEqualTo("user-1");
        assertThat(capturedMessages.sentEmbeds).hasSize(1);
        MessageEmbed embed = capturedMessages.sentEmbeds.getFirst();
        assertThat(embed.getDescription()).contains("- Force : **7** (7 + 0)");
        assertThat(embed.getDescription()).contains("- Emblème : **<:comete:20>**");
        assertThat(capturedMessages.editedOriginalMessages).containsExactly("Fiche de personnage de Melaine");
    }

    @Test
    void fichePersoEditsOriginalWhenApiFailsAfterDeferReply() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        botApiService.ensureUserFailure = new ApiException(502, "api_user_missing", "boom");
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        FichePersoCommand command = new FichePersoCommand(botApiService, cache);

        CapturedMessages capturedMessages = new CapturedMessages();
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", role("99", "Novice", Color.ORANGE)), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        SlashCommandInteractionEvent event = slashCommandEvent("ficheperso", guild, member, null, capturedMessages);

        command.execute(event);

        assertThat(capturedMessages.sentEmbeds).isEmpty();
        assertThat(capturedMessages.editedOriginalMessages).containsExactly("La fiche de personnage n'a pas pu être chargée");
    }

    @Test
    void fichePersoEditsOriginalWhenCatalogueCacheIsMissingAfterDeferReply() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        FichePersoCommand command = new FichePersoCommand(botApiService, new GuildCatalogueCache());

        CapturedMessages capturedMessages = new CapturedMessages();
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", role("99", "Novice", Color.ORANGE)), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        SlashCommandInteractionEvent event = slashCommandEvent("ficheperso", guild, member, null, capturedMessages);

        command.execute(event);

        assertThat(capturedMessages.sentEmbeds).isEmpty();
        assertThat(capturedMessages.editedOriginalMessages).containsExactly("La fiche de personnage n'a pas pu être chargée");
    }

    @Test
    void joinListenerUsesGuildCatalogueSettingsAndApiRank() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        CatalogueMessageService messageService = new FixedCatalogueMessageService(Optional.of("Bienvenue %username%."));
        GuildMemberJoinListener listener = new GuildMemberJoinListener(botApiService, cache, messageService);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.PINK);
        TextChannel welcomeChannel = textChannel("10", capturedMessages);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole), Map.of("10", welcomeChannel), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        GuildMemberJoinEvent event = new GuildMemberJoinEvent(jdaSelf(), 1L, member);

        listener.onGuildMemberJoin(event);

        assertThat(botApiService.ensureUserCalls).isEqualTo(1);
        assertThat(botApiService.lastEnsureGuildId).isEqualTo("guild-1");
        assertThat(capturedMessages.roleAssignments).containsExactly("user-1->99");
        assertThat(capturedMessages.sentEmbeds).hasSize(1);
        assertThat(capturedMessages.sentEmbeds.getFirst().getDescription()).contains("Bienvenue %username%.");
        assertThat(capturedMessages.sentEmbeds.getFirst().getDescription()).contains("Rôle « Comète »");
    }

    @Test
    void joinListenerAssignsRankRoleWhenWelcomeChannelIsMissing() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue(null, "11", "12"));
        CatalogueMessageService messageService = new FixedCatalogueMessageService(Optional.of("Bienvenue %username%."));
        GuildMemberJoinListener listener = new GuildMemberJoinListener(botApiService, cache, messageService);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.PINK);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        GuildMemberJoinEvent event = new GuildMemberJoinEvent(jdaSelf(), 1L, member);

        listener.onGuildMemberJoin(event);

        assertThat(capturedMessages.roleAssignments).containsExactly("user-1->99");
        assertThat(capturedMessages.sentEmbeds).isEmpty();
    }

    @Test
    void joinListenerReturnsCleanlyWhenEnsureUserFails() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        botApiService.ensureUserFailure = new ApiException(502, "api_user_missing", "boom");
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        CatalogueMessageService messageService = new FixedCatalogueMessageService(Optional.of("Bienvenue %username%."));
        GuildMemberJoinListener listener = new GuildMemberJoinListener(botApiService, cache, messageService);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.PINK);
        TextChannel welcomeChannel = textChannel("10", capturedMessages);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole), Map.of("10", welcomeChannel), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        GuildMemberJoinEvent event = new GuildMemberJoinEvent(jdaSelf(), 1L, member);

        assertThatCode(() -> listener.onGuildMemberJoin(event)).doesNotThrowAnyException();
        assertThat(capturedMessages.roleAssignments).isEmpty();
        assertThat(capturedMessages.sentEmbeds).isEmpty();
    }

    @Test
    void leaveListenerUsesByeChannelAndFormatsUsername() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        CatalogueMessageService messageService = new FixedCatalogueMessageService(Optional.of("A bientôt %username%."));
        GuildMemberLeaveListener listener = new GuildMemberLeaveListener(botApiService, cache, messageService);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.CYAN);
        TextChannel byeChannel = textChannel("11", capturedMessages);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole), Map.of("11", byeChannel), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        User user = member.getUser();
        GuildMemberRemoveEvent event = new GuildMemberRemoveEvent(jdaSelf(), 1L, guild, user, member);

        listener.onGuildMemberRemove(event);

        assertThat(botApiService.ensureUserCalls).isEqualTo(1);
        assertThat(botApiService.lastEnsureGuildId).isEqualTo("guild-1");
        assertThat(capturedMessages.sentEmbeds).hasSize(1);
        assertThat(capturedMessages.sentEmbeds.getFirst().getDescription()).contains("A bientôt **Melaine**.");
        assertThat(capturedMessages.sentEmbeds.getFirst().getTitle()).isEqualTo("Au revoir, Melaine !");
    }

    @Test
    void leaveListenerReturnsCleanlyWhenCatalogueCacheIsMissing() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        CatalogueMessageService messageService = new FixedCatalogueMessageService(Optional.of("A bientôt %username%."));
        GuildMemberLeaveListener listener = new GuildMemberLeaveListener(botApiService, new GuildCatalogueCache(), messageService);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.CYAN);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of());
        User user = member.getUser();
        GuildMemberRemoveEvent event = new GuildMemberRemoveEvent(jdaSelf(), 1L, guild, user, member);

        assertThatCode(() -> listener.onGuildMemberRemove(event)).doesNotThrowAnyException();
        assertThat(capturedMessages.sentEmbeds).isEmpty();
        assertThat(capturedMessages.roleAssignments).isEmpty();
    }

    @Test
    void roleChangeListenerEnsuresStaffUserFromGuildSettings() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        GuildMemberRoleChangeListener listener = new GuildMemberRoleChangeListener(botApiService, cache);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.GREEN);
        Role staffRole = role("12", "Staff", Color.BLUE);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole, "12", staffRole), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of(staffRole));
        GuildMemberRoleAddEvent event = new GuildMemberRoleAddEvent(jdaSelf(), 1L, member, List.of(staffRole));

        listener.onGuildMemberRoleAdd(event);

        assertThat(botApiService.ensureStaffUserCalls).isEqualTo(1);
        assertThat(botApiService.lastEnsureGuildId).isEqualTo("guild-1");
        assertThat(botApiService.lastEnsureUserId).isEqualTo("user-1");
        assertThat(capturedMessages.roleAssignments).containsExactly("user-1->99");
    }

    @Test
    void roleChangeListenerReturnsCleanlyWhenEnsureStaffUserFails() {
        RecordingBotApiService botApiService = new RecordingBotApiService(sampleUserEnvelope());
        botApiService.ensureStaffUserFailure = new ApiException(502, "api_user_missing", "boom");
        GuildCatalogueCache cache = catalogueCacheWith(sampleCatalogue("10", "11", "12"));
        GuildMemberRoleChangeListener listener = new GuildMemberRoleChangeListener(botApiService, cache);

        CapturedMessages capturedMessages = new CapturedMessages();
        Role rankRole = role("99", "Novice", Color.GREEN);
        Role staffRole = role("12", "Staff", Color.BLUE);
        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of("99", rankRole, "12", staffRole), Map.of(), capturedMessages, null);
        Member member = member("user-1", "Melaine", guild, List.of(staffRole));
        GuildMemberRoleAddEvent event = new GuildMemberRoleAddEvent(jdaSelf(), 1L, member, List.of(staffRole));

        assertThatCode(() -> listener.onGuildMemberRoleAdd(event)).doesNotThrowAnyException();
        assertThat(capturedMessages.roleAssignments).isEmpty();
    }

    @Test
    void emojiListenerRefreshesOnAllSupportedEmojiEvents() {
        RecordingGuildEmojiRefreshDebouncer debouncer = new RecordingGuildEmojiRefreshDebouncer();
        GuildEmojiListener listener = new GuildEmojiListener(debouncer);

        Guild guild = guild("guild-1", "Gachamélia", "icon", Map.of(), Map.of(), new CapturedMessages(), null);
        RichCustomEmoji emoji = emoji("20", "comete", guild);

        listener.onEmojiAdded(new EmojiAddedEvent(jdaSelf(), 1L, emoji));
        listener.onEmojiRemoved(new EmojiRemovedEvent(jdaSelf(), 2L, emoji));
        listener.onEmojiUpdateName(new EmojiUpdateNameEvent(jdaSelf(), 3L, emoji, "ancienne"));
        listener.onEmojiUpdateRoles(new EmojiUpdateRolesEvent(jdaSelf(), 4L, emoji, List.of()));

        assertThat(debouncer.guildIds).containsExactly("guild-1", "guild-1", "guild-1", "guild-1");
    }

    private static CatalogueEnvelope sampleCatalogue(String welcomeChannelId, String byeChannelId, String staffRoleId) {
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(welcomeChannelId, byeChannelId, staffRoleId)),
                new ApiCatalogue(
                        List.of(new ApiRank(
                                1L,
                                "99",
                                "Novice",
                                100,
                                null,
                                false,
                                List.of(new ApiRankStat(5L, "Force", 100)),
                                List.of(new ApiMessage(1L, "Bienvenue %username%.")),
                                List.of(new ApiMessage(2L, "A bientôt %username%."))
                        )),
                        List.of(new ApiRole(2L, "Comète", 100, new ApiEmoji("server", null, "20", "comete", false, true, "<:comete:20>", null))),
                        List.of(new ApiStat(5L, "Force")),
                        List.of(new ApiElement(3L, "Ambre", new ApiEmoji("unicode", "🌘", null, null, false, true, "🌘", null)))
                )
        );
    }

    private static UserEnvelope sampleUserEnvelope() {
        return new UserEnvelope(new ApiUser(
                1L,
                "user-1",
                new ApiUser.ApiRankSummary(1L, "99", "Novice", false),
                new ApiUser.ApiRoleSummary(2L, "Comète"),
                List.of(new ApiUser.ApiElementSummary(3L, "Ambre")),
                List.of(new ApiUserStat(5L, "Force", 7))
        ));
    }

    private static GuildCatalogueCache catalogueCacheWith(CatalogueEnvelope envelope) {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", envelope);
        return cache;
    }

    private static JDA jdaForCommandRegistration(List<Collection<? extends CommandData>> registeredCommands) {
        CommandListUpdateAction updateAction = proxy(
                CommandListUpdateAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "addCommands" -> {
                        registeredCommands.add((Collection<? extends CommandData>) args[0]);
                        yield proxy;
                    }
                    case "queue" -> null;
                    case "getJDA", "setCheck", "deadline", "timeout", "addCheck" -> proxy;
                    case "submit" -> CompletableFuture.completedFuture(List.of());
                    case "complete" -> List.of();
                    case "toString" -> "CommandListUpdateAction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        return proxy(
                JDA.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "updateCommands" -> updateAction;
                    case "toString" -> "JDA[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static JDA jdaSelf() {
        SelfUser selfUser = proxy(
                SelfUser.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAvatarUrl", "getEffectiveAvatarUrl" -> "https://cdn.test/self.png";
                    case "getId" -> "bot-1";
                    case "toString" -> "User[self]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );

        return proxy(
                JDA.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSelfUser" -> selfUser;
                    case "toString" -> "JDA[self]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Guild guild(
            String id,
            String name,
            String iconId,
            Map<String, Role> roles,
            Map<String, TextChannel> channels,
            CapturedMessages capturedMessages,
            CommandListUpdateAction updateAction
    ) {
        return proxy(
                Guild.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "getIconId" -> iconId;
                    case "getRoleById" -> roles.get(String.valueOf(args[0]));
                    case "getTextChannelById" -> channels.get(String.valueOf(args[0]));
                    case "addRoleToMember" -> auditableRestAction(() -> capturedMessages.roleAssignments.add(((Member) args[0]).getId() + "->" + ((Role) args[1]).getId()));
                    case "updateCommands" -> updateAction;
                    case "toString" -> "Guild[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static Member member(String id, String effectiveName, Guild guild, List<Role> roles) {
        User user = proxy(
                User.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getEffectiveName", "getName" -> effectiveName;
                    case "getAsTag" -> effectiveName + "#0001";
                    case "getEffectiveAvatarUrl", "getAvatarUrl" -> "https://cdn.test/" + id + ".png";
                    case "toString" -> "User[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );

        return proxy(
                Member.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getGuild" -> guild;
                    case "getUser" -> user;
                    case "getEffectiveName" -> effectiveName;
                    case "getRoles" -> roles;
                    case "getAsMention" -> "<@" + id + ">";
                    case "getTimeJoined" -> OffsetDateTime.parse("2026-07-07T10:15:30+00:00");
                    case "getJDA" -> jdaSelf();
                    case "toString" -> "Member[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static Role role(String id, String name, Color color) {
        RoleColors colors = new RoleColors(color.getRGB(), 0, 0);
        return proxy(
                Role.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "getColors" -> colors;
                    case "toString" -> "Role[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static TextChannel textChannel(String id, CapturedMessages capturedMessages) {
        return proxy(
                TextChannel.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "sendMessageEmbeds" -> {
                        if (args[0] instanceof Collection<?> embeds) {
                            for (Object embed : embeds) {
                                capturedMessages.sentEmbeds.add((MessageEmbed) embed);
                            }
                        } else if (args[0] instanceof MessageEmbed embed) {
                            capturedMessages.sentEmbeds.add(embed);
                        }
                        yield messageCreateAction();
                    }
                    case "toString" -> "TextChannel[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static SlashCommandInteractionEvent slashCommandEvent(
            String name,
            Guild guild,
            Member member,
            Member optionMember,
            CapturedMessages capturedMessages
    ) {
        InteractionHook hook = proxy(
                InteractionHook.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "editOriginal" -> {
                        capturedMessages.editedOriginalMessages.add((String) args[0]);
                        yield webhookMessageEditAction();
                    }
                    case "setEphemeral" -> proxy;
                    case "getJDA" -> jdaSelf();
                    case "toString" -> "InteractionHook[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        MessageChannelUnion channel = proxy(
                MessageChannelUnion.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "sendMessageEmbeds" -> {
                        if (args[0] instanceof Collection<?> embeds) {
                            for (Object embed : embeds) {
                                capturedMessages.sentEmbeds.add((MessageEmbed) embed);
                            }
                        }
                        yield messageCreateAction();
                    }
                    case "asTextChannel" -> textChannel("10", capturedMessages);
                    case "toString" -> "MessageChannelUnion[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction interaction = proxy(
                net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getGuild" -> guild;
                    case "getMember" -> member;
                    case "getHook" -> hook;
                    case "getJDA" -> jdaSelf();
                    case "getChannel" -> channel;
                    case "getOptions" -> List.of();
                    case "getOption" -> optionMember == null ? null : optionMapping(optionMember);
                    case "toString" -> "SlashCommandInteraction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        return new SlashCommandInteractionEvent(jdaSelf(), 1L, interaction);
    }

    private static net.dv8tion.jda.api.interactions.commands.OptionMapping optionMapping(Member member) {
        return proxy(
                net.dv8tion.jda.api.interactions.commands.OptionMapping.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAsMember" -> member;
                    case "toString" -> "OptionMapping[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static RichCustomEmoji emoji(String id, String name, Guild guild) {
        return proxy(
                RichCustomEmoji.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "getGuild" -> guild;
                    case "getRoles" -> List.of();
                    case "toString" -> "Emoji[" + id + "]";
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
                    case "setCheck", "timeout", "deadline", "reason" -> proxy;
                    case "getJDA" -> jdaSelf();
                    case "submit" -> CompletableFuture.completedFuture(null);
                    case "complete" -> null;
                    case "toString" -> "AuditableRestAction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static <T> RestAction<T> restAction(T value) {
        return proxy(
                RestAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> null;
                    case "setCheck", "timeout", "deadline", "addCheck" -> proxy;
                    case "getJDA" -> jdaSelf();
                    case "submit" -> CompletableFuture.completedFuture(value);
                    case "complete" -> value;
                    case "toString" -> "RestAction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static MessageCreateAction messageCreateAction() {
        return proxy(
                MessageCreateAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> null;
                    case "setCheck", "timeout", "deadline", "addCheck" -> proxy;
                    case "getJDA" -> jdaSelf();
                    case "submit" -> CompletableFuture.completedFuture(null);
                    case "complete" -> null;
                    case "toString" -> "MessageCreateAction[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static WebhookMessageEditAction<?> webhookMessageEditAction() {
        return proxy(
                WebhookMessageEditAction.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> null;
                    case "setCheck", "timeout", "deadline", "addCheck" -> proxy;
                    case "getJDA" -> jdaSelf();
                    case "submit" -> CompletableFuture.completedFuture(null);
                    case "complete" -> null;
                    case "toString" -> "WebhookMessageEditAction[test]";
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

    private static final class CapturedMessages {
        private final List<MessageEmbed> sentEmbeds = new ArrayList<>();
        private final List<String> editedOriginalMessages = new ArrayList<>();
        private final List<String> roleAssignments = new ArrayList<>();
    }

    private static final class RecordingBotApiService extends BotApiService {
        private final UserEnvelope envelope;
        private int ensureUserCalls;
        private int ensureStaffUserCalls;
        private String lastEnsureGuildId;
        private String lastEnsureUserId;
        private RuntimeException ensureUserFailure;
        private RuntimeException ensureStaffUserFailure;

        private RecordingBotApiService(UserEnvelope envelope) {
            super(null, null, null);
            this.envelope = envelope;
        }

        @Override
        public UserEnvelope ensureUser(String guildId, String userDiscordId) {
            if (ensureUserFailure != null) {
                throw ensureUserFailure;
            }
            ensureUserCalls++;
            lastEnsureGuildId = guildId;
            lastEnsureUserId = userDiscordId;
            return envelope;
        }

        @Override
        public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
            if (ensureStaffUserFailure != null) {
                throw ensureStaffUserFailure;
            }
            ensureStaffUserCalls++;
            lastEnsureGuildId = guildId;
            lastEnsureUserId = userDiscordId;
            return envelope;
        }
    }

    private static final class FixedCatalogueMessageService extends CatalogueMessageService {
        private final Optional<String> message;

        private FixedCatalogueMessageService(Optional<String> message) {
            super(new java.util.Random(0));
            this.message = message;
        }

        @Override
        public Optional<String> randomWelcomeMessage(CatalogueEnvelope envelope, long rankId) {
            return message;
        }

        @Override
        public Optional<String> randomByeMessage(CatalogueEnvelope envelope, long rankId) {
            return message;
        }
    }

    private static final class RecordingGuildEmojiRefreshDebouncer extends GuildEmojiRefreshDebouncer {
        private final List<String> guildIds = new ArrayList<>();

        private RecordingGuildEmojiRefreshDebouncer() {
            super(null, null, Duration.ZERO);
        }

        @Override
        public void requestRefresh(Guild guild) {
            guildIds.add(guild.getId());
        }
    }
}
