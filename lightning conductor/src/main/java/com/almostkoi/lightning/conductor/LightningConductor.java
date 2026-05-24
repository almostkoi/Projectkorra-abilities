package com.almostkoi.lightning.conductor;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.LightningAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.event.PlayerSwingEvent;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class LightningConductor extends LightningAbility implements AddonAbility {
    private enum Phase {
        TARGETING,
        CHAIN_FORMING,
        CHAIN_ACTIVE,
        BOLT_FIRING,
        CLEANUP
    }
    @com.projectkorra.projectkorra.attribute.Attribute(com.projectkorra.projectkorra.attribute.Attribute.COOLDOWN)
    private long cooldown = 6000; 
    @com.projectkorra.projectkorra.attribute.Attribute(com.projectkorra.projectkorra.attribute.Attribute.RANGE)
    private double range = 15.0; 
    @com.projectkorra.projectkorra.attribute.Attribute(com.projectkorra.projectkorra.attribute.Attribute.DAMAGE)
    private double damage = 3.0; 
    private int chainFormTicks = 15; 
    private int chainActiveTicks = 10; 
    private int boltStaggerTicks = 2; 
    private double selectRange = 2.0; 
    private int constraintIterations = 5; 
    private double flickerChance = 0.3; 
    private int flickerInterval = 3; 
    private static final java.util.Map<Entity, Long> conductiveEntities = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CONDUCTIVE_DURATION = 10000; 
    private static final double CONDUCTIVE_DAMAGE = 3.0; 
    private Phase phase;
    private VerletChain chain;
    private ChainRenderer renderer;
    private Location targetLocation;
    private Entity targetEntity;
    private int ticksInPhase;
    private int boltIndex;
    private final java.util.Map<Entity, Long> damagedEntities = new java.util.HashMap<>();
    public LightningConductor(Player player) {
        super(player);
        if (hasAbility(player, LightningConductor.class)) {
            return;
        }
        if (!bPlayer.canBend(this)) {
            return;
        }
        this.phase = Phase.TARGETING;
        this.ticksInPhase = 0;
        this.boltIndex = 0;
        start();
    }
    @Override
    public void progress() {
        if (!player.isOnline() || player.isDead()) {
            remove();
            return;
        }
        ticksInPhase++;
        switch (phase) {
            case TARGETING -> handleTargeting();
            case CHAIN_FORMING -> handleChainForming();
            case CHAIN_ACTIVE -> handleChainActive();
            case BOLT_FIRING -> handleBoltFiring();
            case CLEANUP -> handleCleanup();
        }
    }
    private void handleTargeting() {
        Entity target = findTargetEntity();
        if (target != null) {
            targetEntity = target;
            targetLocation = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        } else {
            Location blockTarget = findTargetBlock();
            if (blockTarget != null) {
                targetLocation = blockTarget;
                targetEntity = null;
            } else {
                remove();
                return;
            }
        }
        if (player.getLocation().distance(targetLocation) > range) {
            remove();
            return;
        }
        initializeChain();
        phase = Phase.CHAIN_FORMING;
        ticksInPhase = 0;
    }
    private void handleChainForming() {
        updateChainEndpoints();
        chain.simulate(constraintIterations);
        Vector3d[] positions = chain.getPositions();
        renderer.draw(positions, false, 0);
        for (int i = 0; i < chain.getNodeCount(); i++) {
            damageNearbyEntities(i);
        }
        if (ticksInPhase >= chainFormTicks) {
            phase = Phase.CHAIN_ACTIVE;
            ticksInPhase = 0;
        }
    }
    private void handleChainActive() {
        updateChainEndpoints();
        chain.simulate(constraintIterations);
        renderer.draw(chain.getPositions(), false, 0);
        for (int i = 0; i < chain.getNodeCount(); i++) {
            damageNearbyEntities(i);
        }
        if (ticksInPhase >= chainActiveTicks) {
            phase = Phase.BOLT_FIRING;
            ticksInPhase = 0;
            boltIndex = 0;
        }
    }
    private void handleBoltFiring() {
        updateChainEndpoints();
        chain.simulate(constraintIterations);
        renderer.draw(chain.getPositions(), true, boltIndex);
        if (ticksInPhase % boltStaggerTicks == 0 && boltIndex < chain.getNodeCount()) {
            boltIndex++;
        }
        for (int i = 0; i < chain.getNodeCount(); i++) {
            damageNearbyEntities(i);
        }
        if (boltIndex >= chain.getNodeCount()) {
            phase = Phase.CLEANUP;
            ticksInPhase = 0;
        }
    }
    private void handleCleanup() {
        if (targetLocation != null) {
            player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, targetLocation, 3, 0.5, 0.5, 0.5, 0);
            player.getWorld().playSound(targetLocation, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
            for (Entity entity : player.getWorld().getNearbyEntities(targetLocation, 3.0, 3.0, 3.0)) {
                if (!(entity instanceof LivingEntity living))
                    continue;
                if (entity.equals(player))
                    continue;
                DamageHandler.damageEntity(living, 10.0, this);
            }
        }
        remove();
    }
    private void initializeChain() {
        Location start = getHandLocation();
        double distance = start.distance(targetLocation);
        int nodeCount = Math.max(5, (int) Math.floor(distance / 1.5));
        chain = new VerletChain(start, targetLocation, nodeCount, player.getWorld());
        renderer = new ChainRenderer(player.getWorld(), nodeCount, player.getEntityId());
    }
    private void updateChainEndpoints() {
        chain.pinStart(getHandLocation());
        if (targetEntity != null && targetEntity.isValid() && !targetEntity.isDead()) {
            targetLocation = targetEntity.getLocation().add(0, targetEntity.getHeight() / 2.0, 0);
        }
        chain.pinEnd(targetLocation);
    }
    private void damageNearbyEntities(int nodeIndex) {
        Vector3d nodePos = chain.getPositions()[nodeIndex];
        Location nodeLoc = new Location(player.getWorld(), nodePos.x, nodePos.y, nodePos.z);
        for (Entity entity : nodeLoc.getWorld().getNearbyEntities(nodeLoc, selectRange, selectRange, selectRange)) {
            if (!(entity instanceof LivingEntity living))
                continue;
            if (entity.equals(player))
                continue; 
            long now = System.currentTimeMillis();
            if (damagedEntities.containsKey(entity) && now - damagedEntities.get(entity) < 500) {
                continue; 
            }
            DamageHandler.damageEntity(living, damage, this);
            living.addPotionEffect(
                    new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 1));
            damagedEntities.put(entity, now);
            boolean wasConductive = conductiveEntities.containsKey(entity)
                    && conductiveEntities.get(entity) > System.currentTimeMillis();
            conductiveEntities.put(entity, System.currentTimeMillis() + CONDUCTIVE_DURATION);
            if (!wasConductive) {
                entity.getScheduler().runAtFixedRate(com.projectkorra.projectkorra.ProjectKorra.plugin, task -> {
                    if (!entity.isValid() || !conductiveEntities.containsKey(entity)
                            || System.currentTimeMillis() > conductiveEntities.get(entity)) {
                        conductiveEntities.remove(entity);
                        task.cancel();
                        return;
                    }
                    entity.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                            entity.getLocation().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0.02);
                }, null, 1L, 10L);
            }
        }
    }
    private Entity findTargetEntity() {
        List<Entity> nearby = new ArrayList<>(
                player.getNearbyEntities(range, range, range));
        nearby.removeIf(e -> !(e instanceof LivingEntity) || e.equals(player));
        if (nearby.isEmpty())
            return null;
        return GeneralMethods.getTargetedEntity(player, range, nearby);
    }
    private Location findTargetBlock() {
        org.bukkit.block.Block block = player.getTargetBlockExact((int) range);
        if (block == null)
            return null;
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
    private Location getHandLocation() {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector right = eye.getDirection().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
        org.bukkit.util.Vector forward = eye.getDirection().normalize();
        return eye.clone()
                .add(right.multiply(0.4))
                .subtract(new org.bukkit.util.Vector(0, 0.5, 0))
                .add(forward.multiply(0.3));
    }
    @Override
    public void remove() {
        if (renderer != null) {
            renderer.despawnAll();
        }
        chain = null;
        damagedEntities.clear();
        if (phase != Phase.TARGETING) {
            bPlayer.addCooldown(this);
        }
        super.remove();
    }
    @Override
    public boolean isSneakAbility() {
        return false;
    }
    @Override
    public boolean isHarmlessAbility() {
        return false;
    }
    @Override
    public boolean isIgniteAbility() {
        return false;
    }
    @Override
    public boolean isExplosiveAbility() {
        return false;
    }
    @Override
    public long getCooldown() {
        return cooldown;
    }
    @Override
    public String getName() {
        return "LightningConductor";
    }
    @Override
    public Location getLocation() {
        return player != null ? player.getLocation() : null;
    }
    @Override
    public void load() {
        String path = "ExtraAbilities.almostkoi.Lightning.LightningConductor.";
        org.bukkit.configuration.file.FileConfiguration config = com.projectkorra.projectkorra.ProjectKorra.plugin.getConfig();
        config.addDefault(path + "Cooldown", 6000);
        config.addDefault(path + "Range", 15.0);
        config.addDefault(path + "Damage", 3.0);
        config.options().copyDefaults(true);
        com.projectkorra.projectkorra.ProjectKorra.plugin.saveConfig();
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSwing(PlayerSwingEvent event) {
                Player player = event.getPlayer();
                BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
                if (bPlayer == null)
                    return;
                CoreAbility boundAbility = bPlayer.getBoundAbility();
                if (boundAbility == null)
                    return;
                if (!(boundAbility instanceof LightningConductor))
                    return;
                if (!bPlayer.canBend(boundAbility))
                    return;
                if (!bPlayer.canCurrentlyBendWithWeapons())
                    return;
                if (!bPlayer.isElementToggled(boundAbility.getElement()))
                    return;
                new LightningConductor(player);
            }
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                LightningConductor ability = CoreAbility.getAbility(
                        event.getPlayer(), LightningConductor.class);
                if (ability != null) {
                    ability.remove();
                }
            }
            @EventHandler(ignoreCancelled = true)
            public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
                Entity damager = event.getDamager();
                if (damager instanceof org.bukkit.entity.Projectile proj
                        && proj.getShooter() instanceof Entity shooter) {
                    damager = shooter;
                }
                if (damager == null)
                    return;
                final Entity finalDamager = damager;
                Long expireTime = conductiveEntities.get(finalDamager);
                if (expireTime != null) {
                    if (System.currentTimeMillis() < expireTime) {
                        conductiveEntities.remove(finalDamager);
                        if (event.getEntity() instanceof LivingEntity victim) {
                            Bukkit.getRegionScheduler().runDelayed(ProjectKorra.plugin, victim.getLocation(), task -> {
                                if (victim.isDead() || !victim.isValid())
                                    return;
                                if (finalDamager instanceof Player p) {
                                    DamageHandler.damageEntity(victim, p, CONDUCTIVE_DAMAGE,
                                            CoreAbility.getAbility(LightningConductor.class));
                                } else {
                                    victim.damage(CONDUCTIVE_DAMAGE, finalDamager);
                                }
                                victim.getWorld().spawnParticle(org.bukkit.Particle.DUST_COLOR_TRANSITION,
                                        victim.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1,
                                        new org.bukkit.Particle.DustTransition(
                                                org.bukkit.Color.fromRGB(0, 255, 255),
                                                org.bukkit.Color.fromRGB(255, 255, 255),
                                                1.5f));
                                victim.getWorld().playSound(victim.getLocation(),
                                        org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 2.0f);
                            }, 1L);
                        }
                    } else {
                        conductiveEntities.remove(finalDamager);
                    }
                }
            }
        }, ProjectKorra.plugin);
        ProjectKorra.log.info("LightningConductor addon loaded successfully.");
    }
    @Override
    public void stop() {
        getAbilities(LightningConductor.class).forEach(LightningConductor::remove);
        ProjectKorra.log.info("LightningConductor addon stopped — all active chains cleaned up.");
    }
    @Override
    public String getAuthor() {
        return "almostkoi";
    }
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    @Override
    public String getDescription() {
        return "Fire a chain of lightning toward a target. The chain sways realistically " +
                "under gravity, then a bolt fires along it dealing damage to anything in its path. " +
                "Range: " + range + " blocks. Cooldown: " + (cooldown / 1000.0) + "s.";
    }
    @Override
    public String getInstructions() {
        return "Left-click while looking at a target within " + (int) range + " blocks. " +
                "A lightning chain will form and a bolt will fire along it.";
    }
    @Override
    public boolean isEnabled() {
        return true;
    }
}