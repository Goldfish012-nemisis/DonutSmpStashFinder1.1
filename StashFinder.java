package com.stashhunter.modules;

import com.stashhunter.StashHunterAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity; // TrappedChestBlockEntity extends this
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StashFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ---------- Settings ----------
    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Alleen containers op of onder dit Y-level.")
        .defaultValue(-1)
        .range(-64, 320)
        .sliderRange(-64, 64)
        .build());

    private final Setting<Integer> minContainers = sgGeneral.add(new IntSetting.Builder()
        .name("min-containers")
        .description("Minimaal aantal aaneengesloten chests/barrels voor een stash.")
        .defaultValue(3)
        .min(2)
        .sliderMax(20)
        .build());

    private final Setting<Boolean> includeBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Tel barrels mee.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> diagonals = sgGeneral.add(new BoolSetting.Builder()
        .name("diagonal-adjacent")
        .description("Tel diagonaal naast elkaar ook als 'tegen elkaar aan'.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> dangerRadius = sgGeneral.add(new IntSetting.Builder()
        .name("danger-radius")
        .description("Straal rond de containers waarin obsidian/anchors NIET mogen staan.")
        .defaultValue(2)
        .range(1, 6)
        .sliderRange(1, 6)
        .build());

    private final Setting<Boolean> crying = sgGeneral.add(new BoolSetting.Builder()
        .name("crying-obsidian")
        .description("Tel crying obsidian ook als obsidian.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks tussen scans (20 = 1 seconde).")
        .defaultValue(20)
        .range(5, 200)
        .sliderRange(5, 100)
        .build());

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Stuur een chatmelding bij een nieuwe stash.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 170, 0, 40))
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 170, 0, 255))
        .build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Teken een lijn van je crosshair naar de stash.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(tracers::get)
        .build());

    // ---------- State ----------
    private static class Stash {
        final List<BlockPos> blocks;
        final Vec3 center;

        Stash(List<BlockPos> blocks) {
            this.blocks = blocks;
            double x = 0, y = 0, z = 0;
            for (BlockPos p : blocks) { x += p.getX() + 0.5; y += p.getY() + 0.5; z += p.getZ() + 0.5; }
            this.center = new Vec3(x / blocks.size(), y / blocks.size(), z / blocks.size());
        }

        long key() {
            long min = Long.MAX_VALUE;
            for (BlockPos p : blocks) min = Math.min(min, p.asLong());
            return min;
        }
    }

    private volatile List<Stash> stashes = new ArrayList<>();
    private final Set<Long> notified = new HashSet<>();
    private int timer;

    public StashFinder() {
        super(StashHunterAddon.CATEGORY, "stash-finder",
            "Zoekt stashes (chests/barrels) onder een bepaald Y-level zonder obsidian/anchors ernaast.");
    }

    @Override
    public void onActivate() {
        stashes = new ArrayList<>();
        notified.clear();
        timer = 0;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stashes = new ArrayList<>();
        notified.clear();
    }

    // ---------- Scanning ----------
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (++timer < interval.get()) return;
        timer = 0;
        scan();
    }

    private void scan() {
        // 1. Verzamel alle containers onder max-y (alleen geladen chunks)
        Set<BlockPos> containers = new HashSet<>();
        for (BlockEntity be : Utils.blockEntities()) {
            if (be.getBlockPos().getY() > maxY.get()) continue;
            if (be instanceof ChestBlockEntity || (includeBarrels.get() && be instanceof BarrelBlockEntity)) {
                containers.add(be.getBlockPos().immutable());
            }
        }

        // 2. Groepeer containers die tegen elkaar aan staan (BFS)
        List<Stash> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos start : containers) {
            if (visited.contains(start)) continue;

            List<BlockPos> group = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                group.add(cur);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (!diagonals.get() && dist != 1) continue;

                            BlockPos n = cur.offset(dx, dy, dz);
                            if (containers.contains(n) && visited.add(n)) queue.add(n);
                        }
                    }
                }
            }

            if (group.size() < minContainers.get()) continue;
            if (hasDanger(group)) continue;

            result.add(new Stash(group));
        }

        stashes = result;

        // 3. Meld nieuwe stashes
        if (notify.get()) {
            for (Stash s : result) {
                if (notified.add(s.key())) {
                    info("Stash gevonden bij (%d, %d, %d) - %d containers.",
                        (int) s.center.x, (int) s.center.y, (int) s.center.z, s.blocks.size());
                }
            }
        }
    }

    /** True als er obsidian / respawn anchor binnen de straal van de groep staat. */
    private boolean hasDanger(List<BlockPos> group) {
        int r = dangerRadius.get();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (BlockPos p : group) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        mp.set(p.getX() + x, p.getY() + y, p.getZ() + z);
                        Block b = mc.level.getBlockState(mp).getBlock();
                        if (b == Blocks.OBSIDIAN || b == Blocks.RESPAWN_ANCHOR) return true;
                        if (crying.get() && b == Blocks.CRYING_OBSIDIAN) return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------- Rendering ----------
    @EventHandler
    private void onRender(Render3DEvent event) {
        List<Stash> current = stashes;
        if (current.isEmpty()) return;

        Color side = sideColor.get();
        Color line = lineColor.get();
        Color tracer = tracerColor.get();

        for (Stash s : current) {
            // ESP op elke chest/barrel
            for (BlockPos p : s.blocks) {
                event.renderer.box(p, side, line, shapeMode.get(), 0);
            }

            // Tracer vanaf crosshair naar het midden van de stash
            if (tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    s.center.x, s.center.y, s.center.z,
                    tracer
                );
            }
        }
    }

    @Override
    public String getInfoString() {
        int n = stashes.size();
        return n == 0 ? null : String.valueOf(n);
    }
}
