// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.BountySettings;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Bounty;
import io.github.lifesmp.core.domain.model.KillStreak;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.BountyRepository;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.ports.RewardGateway;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bounties placed by players and the server-side wanted status.
 * Every method runs on the server thread; the methods are synchronized only to keep the maps consistent.
 */
public final class BountyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BountyService.class);
    /** Survival timers are written to the database once a minute and on quit, not every second. */
    private static final long PERSIST_EVERY_SECONDS = 60L;

    /** Result of a player action; {@link #key()} is the message to show the player. */
    public enum Status {
        OK(""),
        DISABLED("bounty.disabled"),
        SELF("bounty.self"),
        TARGET_HAS_BOUNTY("bounty.target-has-bounty"),
        ISSUER_LIMIT("bounty.issuer-limit"),
        XP_BELOW_MIN("bounty.xp-below-min"),
        BUYOUT_BELOW_MIN("bounty.buyout-below-min"),
        NOT_ENOUGH_LIVES("bounty.not-enough-lives"),
        NOT_ENOUGH_XP("bounty.not-enough-xp"),
        NO_BOUNTY("bounty.no-bounty");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        @NotNull
        public String key() {
            return key;
        }
    }

    private final BountyRepository repository;
    private final LifeProfileCache profileCache;
    private final NotificationService notifications;
    private final RewardGateway rewards;
    private volatile Supplier<BountySettings> settings;

    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private final Map<UUID, Long> wantedSeconds = new HashMap<>();
    private final Map<String, Instant> cooldowns = new HashMap<>();
    private final Map<UUID, KillStreak> killStreaks = new HashMap<>();

    public BountyService(
            @NotNull BountyRepository repository,
            @NotNull LifeProfileCache profileCache,
            @NotNull NotificationService notifications,
            @NotNull RewardGateway rewards,
            @NotNull Supplier<BountySettings> settings
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
        this.notifications = Objects.requireNonNull(notifications, "notifications cannot be null");
        this.rewards = Objects.requireNonNull(rewards, "rewards cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    public void setSettings(@NotNull Supplier<BountySettings> settings) {
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    @NotNull
    public BountySettings getSettings() {
        return settings.get();
    }

    /** Loads stored bounties; call once at startup before players join. */
    public synchronized void load() {
        BountyRepository.Snapshot snapshot = repository.loadAll().join();
        snapshot.bounties().forEach(b -> bounties.put(b.target(), b));
        wantedSeconds.putAll(snapshot.wantedSeconds());
        cooldowns.putAll(snapshot.cooldowns());
        killStreaks.putAll(snapshot.streaks());
        LOGGER.info("Loaded {} bounties and {} wanted players", bounties.size(), wantedSeconds.size());
    }

    /** The message for a non-OK status, with its placeholders filled in. */
    @NotNull
    public String statusMessage(@NotNull Status status, @NotNull LifeConfig config) {
        BountySettings s = settings.get();
        return switch (status) {
            case XP_BELOW_MIN -> config.formatMessage(status.key(), "min", String.valueOf(s.minXp()));
            case BUYOUT_BELOW_MIN -> config.formatMessage(status.key(), "min", String.valueOf(s.minBuyout()));
            default -> config.formatMessage(status.key());
        };
    }

    // ---- queries ----

    @NotNull
    public synchronized Optional<Bounty> getBounty(@NotNull UUID target) {
        return Optional.ofNullable(bounties.get(target));
    }

    public synchronized boolean isWanted(@NotNull UUID player) {
        return wantedSeconds.containsKey(player);
    }

    /** Whether the player gets the bounty icon next to the name. */
    public synchronized boolean isHunted(@NotNull UUID player) {
        return settings.get().enabled() && (bounties.containsKey(player) || wantedSeconds.containsKey(player));
    }

    public synchronized long getWantedSurvivedSeconds(@NotNull UUID player) {
        return wantedSeconds.getOrDefault(player, 0L);
    }

    // ---- player actions ----

    /**
     * Checks a bounty before the item menu opens, so the issuer does not fill a chest for nothing.
     */
    @NotNull
    public synchronized Status validatePlace(
            @NotNull UUID issuer, @NotNull UUID target, int xp, int lives, int buyout, int issuerXp
    ) {
        BountySettings s = settings.get();
        if (!s.enabled()) {
            return Status.DISABLED;
        }
        if (issuer.equals(target)) {
            return Status.SELF;
        }
        if (bounties.containsKey(target)) {
            return Status.TARGET_HAS_BOUNTY;
        }
        long active = bounties.values().stream().filter(b -> b.issuer().equals(issuer)).count();
        if (active >= s.maxActivePerIssuer()) {
            return Status.ISSUER_LIMIT;
        }
        if (xp < s.minXp()) {
            return Status.XP_BELOW_MIN;
        }
        if (buyout < s.minBuyout()) {
            return Status.BUYOUT_BELOW_MIN;
        }
        if (xp > issuerXp) {
            return Status.NOT_ENOUGH_XP;
        }
        int issuerLives = profileCache.getCachedProfile(issuer).map(LifeProfile::getLives).orElse(0);
        if (lives < 0 || issuerLives - lives < 1) {
            return Status.NOT_ENOUGH_LIVES;
        }
        return Status.OK;
    }

    /**
     * Places the bounty. On {@link Status#OK} the staked lives are already taken; the platform must then
     * take {@code xp} levels from the issuer. On any other status the platform returns the items.
     */
    @NotNull
    public synchronized Status place(
            @NotNull UUID issuer, @NotNull String issuerName, @NotNull String targetName, @NotNull UUID target,
            byte @Nullable [] items, int xp, int lives, int buyout, int issuerXp,
            @NotNull Instant now, @NotNull LifeConfig config
    ) {
        Status status = validatePlace(issuer, target, xp, lives, buyout, issuerXp);
        if (status != Status.OK) {
            return status;
        }
        if (lives > 0) {
            addLives(issuer, -lives, config);
        }
        int storedXp = xp - xp * settings.get().taxPercent() / 100;
        Bounty bounty = new Bounty(target, issuer, issuerName, items, storedXp, lives, buyout, 0L, now);
        bounties.put(target, bounty);
        repository.saveBounty(bounty);

        refreshName(target);
        notifications.sendMessage(issuer, config.formatMessage("bounty.placed", "player", targetName));
        notifications.sendMessage(target, config.formatMessage("bounty.target-notice", "player", issuerName));
        return Status.OK;
    }

    /** Cancels the issuer's bounty and refunds part of the stake. */
    @NotNull
    public synchronized Status cancel(@NotNull UUID issuer, @NotNull LifeConfig config) {
        Bounty bounty = bounties.values().stream().filter(b -> b.issuer().equals(issuer)).findFirst().orElse(null);
        if (bounty == null) {
            return Status.NO_BOUNTY;
        }
        int pct = settings.get().cancelRefundPercent();
        removeBounty(bounty.target());
        rewards.give(issuer, bounty.items(), pct, bounty.xpLevels() * pct / 100);
        addLives(issuer, bounty.lives() * pct / 100, config);
        notifications.sendMessage(issuer, config.formatMessage("bounty.cancelled", "percent", String.valueOf(pct)));
        notifications.sendMessage(bounty.target(), config.formatMessage("bounty.removed-target"));
        return Status.OK;
    }

    /**
     * The target buys the bounty out: the stake goes to the target. On {@link Status#OK} the platform must
     * take {@link Bounty#buyoutLevels()} XP levels from the target; they are burned.
     */
    @NotNull
    public synchronized Status buyout(@NotNull UUID target, int targetXp, @NotNull LifeConfig config) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return Status.NO_BOUNTY;
        }
        if (targetXp < bounty.buyoutLevels()) {
            return Status.NOT_ENOUGH_XP;
        }
        removeBounty(target);
        payStake(target, bounty, config);
        notifications.sendMessage(target, config.formatMessage("bounty.bought-out"));
        notifications.sendMessage(bounty.issuer(), config.formatMessage("bounty.bought-out-issuer"));
        return Status.OK;
    }

    // ---- life cycle hooks, called by LifeEngine ----

    /**
     * Handles a death that cost a life.
     *
     * @param killer        the killing player, or null for a non-PvP death
     * @param livesBefore   victim lives before this death
     * @param victimBanned  whether this death banned the victim
     * @param overrideActive whether a global PvP event is running; such kills pay nothing
     * @param retaliation   whether the killer was retaliating; such kills do not make the killer wanted
     */
    public synchronized void onDeath(
            @NotNull UUID victim, @Nullable UUID killer, @Nullable String victimName, int livesBefore,
            boolean victimBanned, boolean overrideActive, boolean retaliation, @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        BountySettings s = settings.get();
        if (killStreaks.remove(victim) != null) {
            repository.deleteStreak(victim);
        }
        boolean victimWasWanted = wantedSeconds.remove(victim) != null;
        if (victimWasWanted) {
            repository.deleteWanted(victim);
            refreshName(victim);
        }
        if (!s.enabled() || killer == null || killer.equals(victim) || overrideActive) {
            return;
        }
        String name = victimName != null ? victimName : victim.toString();

        Bounty bounty = bounties.get(victim);
        // A bounty on a red target is frozen; the issuer can never claim their own bounty
        if (bounty != null && livesBefore > 1 && !bounty.issuer().equals(killer) && !onCooldown(killer, victim, now)) {
            removeBounty(victim);
            payStake(killer, bounty, config);
            Instant until = now.plusSeconds(s.pairCooldownSeconds());
            cooldowns.put(killer + ":" + victim, until);
            repository.saveCooldown(killer, victim, until);
            notifications.sendMessage(killer, config.formatMessage("bounty.claimed", "player", name));
        }

        if (victimWasWanted) {
            addLives(killer, 1, config);
            notifications.sendMessage(killer, config.formatMessage("bounty.wanted-claimed", "player", name));
        }

        if (retaliation) {
            return;
        }
        KillStreak previous = killStreaks.get(killer);
        KillStreak current = previous == null || previous.isExpired(now, s.streakResetSeconds())
                ? new KillStreak(1, now)
                : new KillStreak(previous.kills() + 1, previous.startedAt());
        killStreaks.put(killer, current);
        repository.saveStreak(killer, current);
        int streak = current.kills();
        int killerLives = profileCache.getCachedProfile(killer).map(LifeProfile::getLives).orElse(0);
        boolean triggered = streak >= s.wantedStreak() || victimBanned || livesBefore < killerLives;
        if (triggered && !wantedSeconds.containsKey(killer)) {
            wantedSeconds.put(killer, 0L);
            repository.saveWanted(killer, 0L);
            refreshName(killer);
            notifications.sendMessage(killer, config.formatMessage("bounty.wanted-now"));
        }
    }

    /** Advances survival timers of an online, non-AFK player by one second. */
    public synchronized void tickOnline(@NotNull UUID player, @NotNull LifeConfig config) {
        BountySettings s = settings.get();
        if (!s.enabled()) {
            return;
        }
        long duration = s.durationSeconds();

        Bounty bounty = bounties.get(player);
        int lives = profileCache.getCachedProfile(player).map(LifeProfile::getLives).orElse(0);
        // The timer of a red target stands still, like the bounty itself
        if (bounty != null && lives > 1) {
            long survived = bounty.survivedSeconds() + 1;
            if (survived >= duration) {
                removeBounty(player);
                payStake(player, bounty, config);
                notifications.sendMessage(player, config.formatMessage("bounty.survived"));
                notifications.sendMessage(bounty.issuer(), config.formatMessage("bounty.survived-issuer"));
            } else {
                Bounty updated = bounty.withSurvivedSeconds(survived);
                bounties.put(player, updated);
                if (survived % PERSIST_EVERY_SECONDS == 0) {
                    repository.saveBounty(updated);
                }
            }
        }

        Long wanted = wantedSeconds.get(player);
        if (wanted != null) {
            long survived = wanted + 1;
            if (survived >= duration) {
                wantedSeconds.remove(player);
                repository.deleteWanted(player);
                refreshName(player);
                notifications.sendMessage(player, config.formatMessage("bounty.wanted-cleared"));
            } else {
                wantedSeconds.put(player, survived);
                if (survived % PERSIST_EVERY_SECONDS == 0) {
                    repository.saveWanted(player, survived);
                }
            }
        }
    }

    /** Writes the survival timers of a leaving player. */
    public synchronized void onQuit(@NotNull UUID player) {
        Bounty bounty = bounties.get(player);
        if (bounty != null) {
            repository.saveBounty(bounty);
        }
        Long wanted = wantedSeconds.get(player);
        if (wanted != null) {
            repository.saveWanted(player, wanted);
        }
    }

    /** Writes all survival timers and waits for pending writes; call before the database closes. */
    public synchronized void shutdown() {
        bounties.values().forEach(repository::saveBounty);
        wantedSeconds.forEach(repository::saveWanted);
        repository.flush().join();
    }

    /** Prunes expired hunter cooldowns. */
    public synchronized void pruneExpired(@NotNull Instant now) {
        cooldowns.values().removeIf(until -> !now.isBefore(until));
        long resetSeconds = settings.get().streakResetSeconds();
        killStreaks.entrySet().removeIf(e -> {
            boolean expired = e.getValue().isExpired(now, resetSeconds);
            if (expired) {
                repository.deleteStreak(e.getKey());
            }
            return expired;
        });
    }

    // ---- internals ----

    private boolean onCooldown(UUID hunter, UUID target, Instant now) {
        Instant until = cooldowns.get(hunter + ":" + target);
        return until != null && now.isBefore(until);
    }

    private void removeBounty(UUID target) {
        bounties.remove(target);
        repository.deleteBounty(target);
        refreshName(target);
    }

    private void payStake(UUID recipient, Bounty bounty, LifeConfig config) {
        if (!rewards.give(recipient, bounty.items(), 100, bounty.xpLevels())) {
            LOGGER.warn("Bounty stake on {} could not be given to offline player {}", bounty.target(), recipient);
        }
        addLives(recipient, bounty.lives(), config);
    }

    /** Adds (or with a negative amount takes) lives; lives above the maximum are burned. */
    private void addLives(UUID player, int amount, LifeConfig config) {
        if (amount == 0) {
            return;
        }
        profileCache.getCachedProfile(player).ifPresent(profile -> {
            int newLives;
            synchronized (profile) {
                newLives = Math.clamp(profile.getLives() + (long) amount, 1, config.getMaxLives());
                profile.setLives(newLives);
            }
            profileCache.saveProfileImmediately(profile);
            notifications.updateDisplayNameAndTab(player, newLives);
        });
    }

    private void refreshName(UUID player) {
        profileCache.getCachedProfile(player)
                .ifPresent(p -> notifications.updateDisplayNameAndTab(player, p.getLives()));
    }
}
