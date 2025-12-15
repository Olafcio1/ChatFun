package pl.olafcio.chatfun;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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
            @NotNull String message,
            @NotNull ConfigurationSection custom,
            int interval, int maxTime, int minPlayers,
            int responsePollingRate,
            @NotNull String correctMessage,
            @NotNull String missMessage
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

    @NotNull Component str2component(String input) {
        return MiniMessage.miniMessage().deserialize(
                MessageUtil.parseCenters(
                MessageUtil.parseMultiplies(
                MessageUtil.replaceLegacyColors(
                MessageUtil.replaceLegacyFormatting(
                MessageUtil.replaceLegacyHex(
                        input
                )))))
        );
    }

    int second2ms(int x) {
        return x * 1000;
    }

    void pickOptions(FileConfiguration config) {
        Consumer<Keyed> appender = item ->
                options.add(item.key().value());

        if (config.getBoolean("categories.items"))
            Registry.ITEM.stream().forEach(appender);

        if (config.getBoolean("categories.blocks"))
            Registry.BLOCK.stream().forEach(appender);

        if (config.getBoolean("categories.effects"))
            Registry.EFFECT.stream().forEach(appender);

        if (config.getBoolean("categories.biomes"))
            Registry.BIOME.stream().forEach(appender);

        if (config.getBoolean("categories.advancements"))
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

        var correctMessage = config.getString("correct-message");
        var missMessage = config.getString("miss-message");

        var games = Stream.of("unscramble")
                .map(name -> {
                    var section = config.getConfigurationSection("games." + name);
                    assert section != null;
                    section.set("name", name);
                    return section;
                })
                .filter(section -> section.getBoolean("enabled"))
                .map(section -> new Game(
                        section.getString("name"),
                        section.getString("message"),
                        section,
                        compute(section, interval, "overrides.interval", this::second2ms),
                        compute(section, maxTime, "overrides.max-time", this::second2ms),
                        section.getInt("overrides.min-players", minPlayers),
                        compute(section, responsePollingRate, "overrides.response-polling-rate", this::second2ms),
                        section.getString("overrides.correct-message", correctMessage),
                        section.getString("overrides.miss-message", missMessage)
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
        key = key.replace("_", " ");
        key = key.substring(key.lastIndexOf("/") + 1);

        String hiddenKey = null;
        if (game.name.equals("unscramble")) {
            var words = Arrays.stream(key.split(" ")).map(word -> {
                var chars = word.chars()
                                .mapToObj(c -> (char) c)
                                .collect(Collectors.toCollection(ArrayList::new));

                Collections.shuffle(chars, random);
                return StringUtils.join(chars.toArray(Character[]::new));
            });

            hiddenKey = words.collect(Collectors.joining(" "));
        }

        announce(game, hiddenKey);
        awaitFor(game);
    }

    void announce(Game game, String secret) {
        Bukkit.broadcast(str2component(
            game.message
                .replace("{{key}}", secret)
        ));
    }

    void awaitFor(Game game) throws InterruptedException {
        activeGame = game;
        keyExpect = true;

        var start = System.currentTimeMillis();
        while (keyExpect && (System.currentTimeMillis() - start) < game.maxTime)
            Thread.sleep(game.responsePollingRate);

        if (keyExpect) {
            keyExpect = false;
            Bukkit.broadcast(str2component(
                activeGame.missMessage
                    .replace("{{key}}", key)
            ));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (keyExpect) {
            var raw = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
            if (raw.equalsIgnoreCase(key)) {
                keyExpect = false;
                Bukkit.broadcast(str2component(
                    activeGame.correctMessage
                        .replace("{{key}}", key)
                        .replace("{{player}}", event.getPlayer().getName())
                ));
            }
        }
    }

    @Override
    public void onDisable() {
        task.cancel(true);
        executor.shutdownNow();
    }
}
