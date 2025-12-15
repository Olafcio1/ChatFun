package pl.olafcio.chatfun;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ChatFun extends JavaPlugin implements Listener {
    private ExecutorService executor;
    private Future<?> task;
    private Random random;

    private int onlinePlayers = 0;
    private final ArrayList<String> options = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    record Game(
            @NotNull String name,
            @NotNull Component message,
            @NotNull ConfigurationSection custom,
            int interval, int maxTime, int minPlayers,
            int responsePollingRate,
            @NotNull TextComponent correctMessage,
            @NotNull TextComponent missMessage
    ) {}

    @SuppressWarnings("unchecked")
    <I, O> O computeComplex(ConfigurationSection section, O defaultValue, String key, Function<I, O> computer) {
        if (section.contains(key))
            return computer.apply((I) section.get(key));
        else return defaultValue;
    }

    <T> T compute(ConfigurationSection section, T defaultValue, String key, Function<T, T> computer) {
        return computeComplex(section, defaultValue, key, computer);
    }

    @NotNull TextComponent str2component(String input) {
        return PlainTextComponentSerializer.plainText().deserialize(
                MessageUtil.parseFull(
                MessageUtil.replaceLegacyColors(
                MessageUtil.replaceLegacyHex(
                        input
                )))
        );
    }

    int second2ms(int x) {
        return x * 1000;
    }

    void pickOptions(FileConfiguration config) {
        Consumer<Keyed> appender = item ->
                options.add(item.key().value());

        if (config.getBoolean("types.items"))
            Registry.ITEM.stream().forEach(appender);

        if (config.getBoolean("types.blocks"))
            Registry.BLOCK.stream().forEach(appender);

        if (config.getBoolean("types.effects"))
            Registry.EFFECT.stream().forEach(appender);

        if (config.getBoolean("types.biomes"))
            Registry.BIOME.stream().forEach(appender);

        if (config.getBoolean("types.advancements"))
            Registry.ADVANCEMENT.stream().forEach(appender);
    }

    public void reload() {
        super.reloadConfig();
        var config = getConfig();

        var interval = config.getInt("interval") * 1000;
        var maxTime = config.getInt("max-time") * 1000;
        var minPlayers = config.getInt("min-players");

        pickOptions(config);

        var responsePollingRate = config.getInt("response-polling-rate") * 1000;

        var correctMessage = str2component(config.getString("correct-message"));
        var missMessage = str2component(config.getString("miss-message"));

        var games = Stream.of("unscramble")
                .map(name -> {
                    var section = config.getConfigurationSection("games." + name);
                    assert section != null;
                    section.set("name", name);
                    return section;
                })
                .filter(section -> section.getBoolean("enabled"))
                .map(section -> new Game(
                        section.getString("name"), // it is not null nigga
                        str2component(section.getString("message")), // ts is not either
                        section,
                        compute(section, interval, "overrides.interval", this::second2ms),
                        compute(section, maxTime, "overrides.max-time", this::second2ms),
                        section.getInt("overrides.min-players", minPlayers),
                        compute(section, responsePollingRate, "overrides.response-polling-rate", this::second2ms),
                        computeComplex(section, correctMessage, "overrides.correct-message", this::str2component),
                        computeComplex(section, missMessage, "overrides.miss-message", this::str2component)
                ))
        .toList();
        var gamesAmount = games.size();

        executor = Executors.newFixedThreadPool(2);
        random = new Random();
        task = executor.submit(() -> {
            while (true) {
                var index = random.nextInt(gamesAmount);
                var game = games.get(index);

                if (onlinePlayers < game.minPlayers) {
                    Thread.sleep(1000);
                    continue;
                }

                Thread.sleep(game.interval);
                startGame(game);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onlinePlayers++;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayers--;
    }

    Game activeGame;

    String key = null;
    boolean keyExpect = false;

    void startGame(Game game) throws InterruptedException {
        key = options.get(random.nextInt(options.size()));

        String hiddenKey = null;
        if (game.name.equals("unscramble"))
            hiddenKey = StringUtils.rotate(key, random.nextInt(key.length()));

        announce(game, hiddenKey);
        awaitFor(game);
    }

    void announce(Game game, String secret) {
        Bukkit.broadcast(game.message.replaceText(TextReplacementConfig.builder()
                        .matchLiteral("{{key}}")
                        .replacement(secret)
        .build()));
    }

    void awaitFor(Game game) throws InterruptedException {
        activeGame = game;
        keyExpect = true;

        var start = System.currentTimeMillis();
        while (keyExpect && (System.currentTimeMillis() - start) < game.maxTime)
            Thread.sleep(game.responsePollingRate);

        if (keyExpect) {
            keyExpect = false;
            Bukkit.broadcast(activeGame.missMessage.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("{{key}}")
                    .replacement(key)
            .build()));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (keyExpect) {
            var raw = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
            if (raw.equals(key)) {
                keyExpect = false;
                Bukkit.broadcast(activeGame.correctMessage
                        .replaceText(TextReplacementConfig.builder()
                                .matchLiteral("{{key}}")
                                .replacement(key)
                        .build())
                        .replaceText(TextReplacementConfig.builder()
                                .matchLiteral("{{player}}")
                                .replacement(event.getPlayer().getName())
                        .build())
                );
            }
        }
    }

    @Override
    public void onDisable() {
        task.cancel(true);
        executor.shutdownNow();
    }
}
