/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Random;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class Nuker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("The shape of nuking algorithm.")
        .defaultValue(Shape.Sphere)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The way the blocks are broken.")
        .defaultValue(Mode.Flatten)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The break range.")
        .defaultValue(4)
        .min(0)
        .visible(() -> shape.get() != Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_up = sgGeneral.add(new IntSetting.Builder()
        .name("up")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_down = sgGeneral.add(new IntSetting.Builder()
        .name("down")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_left = sgGeneral.add(new IntSetting.Builder()
        .name("left")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_right = sgGeneral.add(new IntSetting.Builder()
        .name("right")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_forward = sgGeneral.add(new IntSetting.Builder()
        .name("forward")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_back = sgGeneral.add(new IntSetting.Builder()
        .name("back")
        .description("The break range.")
        .defaultValue(1)
        .min(0)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to break when behind blocks.")
        .defaultValue(4.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between breaking blocks.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks-per-tick")
        .description("Maximum blocks to try to break per tick. Useful when insta mining.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("The blocks you want to mine first.")
        .defaultValue(SortMode.Closest)
        .build()
    );

    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mine")
        .description("Attempt to instamine everything at once.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> suitableTools = sgGeneral.add(new BoolSetting.Builder()
        .name("only-suitable-tools")
        .description("Only mines when using an appropriate for the block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> interact = sgGeneral.add(new BoolSetting.Builder()
        .name("interact")
        .description("Interacts with the block instead of mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates server-side to the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smoothRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-rotate")
        .description("Smoothes rotation according to delay.")
        .defaultValue(true)
        .visible(() -> rotate.get() && maxBlocksPerTick.get() <= 1)
        .build()
    );

    // Whitelist and blacklist

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("Selection mode.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("The blocks you don't want to mine.")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("The blocks you want to mine.")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    private final Setting<Keybind> selectBlockBind = sgWhitelist.add(new KeybindSetting.Builder()
        .name("select-block-bind")
        .description("Adds targeted block to list when this button is pressed.")
        .defaultValue(Keybind.none())
        .build()
    );

    // Rendering

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("Whether to swing hand client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableRenderBounding = sgRender.add(new BoolSetting.Builder()
        .name("bounding-box")
        .description("Enable rendering bounding box for Cube and Uniform Cube.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBox = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-box-mode")
        .description("How the shape for the bounding box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColorBox = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the bounding box.")
        .defaultValue(new SettingColor(16,106,144, 100))
        .build()
    );

    private final Setting<SettingColor> lineColorBox = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the bounding box.")
        .defaultValue(new SettingColor(16,106,144, 255))
        .build()
    );

    private final Setting<Boolean> enableRenderBreaking = sgRender.add(new BoolSetting.Builder()
        .name("broken-blocks")
        .description("Enable rendering bounding box for Cube and Uniform Cube.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBreak = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-block-mode")
        .description("How the shapes for broken blocks are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the target block rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target block rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<Boolean> enableRenderDebug = sgRender.add(new BoolSetting.Builder()
        .name("debug-visuals")
        .description("Enable debug rendering.")
        .defaultValue(false)
        .build()
    );

    private final List<BlockPos> blocks = new ArrayList<>();
    private BlockPos currentBlock = null;
    private final Set<BlockPos> interacted = new ObjectOpenHashSet<>();

    private boolean firstBlock;
    private final BlockPos.Mutable lastBlockPos = new BlockPos.Mutable();
    private Block lastBlockType = null;

    private int timer;
    private int timerMax;
    private double startYaw = 0;
    private double startPitch = 0;
    private Vec3d randomOffsetOnFace;
    private int noBlockTimer;

    // Checks if the player is within a square "radius" centered on the block
    private boolean isVertical(BlockPos blockPos) {
        return isVertical(blockPos, 1);
    }
    private boolean isVertical(BlockPos blockPos, double range) {
        range = range / 2;

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d centerPos = blockPos.toCenterPos();

        return playerPos.x >= centerPos.x - range && playerPos.x <= centerPos.x + range
            && playerPos.z >= centerPos.z - range && playerPos.z <= centerPos.z + range;
    }

    private void resetOffsetOnFace(BlockPos blockPos) {
        // Check if the offset has already been reset for this block
        Block type = mc.world.getBlockState(blockPos).getBlock();
        if (lastBlockPos.equals(blockPos) && lastBlockType == type) return;

        lastBlockPos.set(blockPos);
        lastBlockType = type;

        Vec3d playerPos = mc.player.getEyePos();
        // If the block is directly above/below the player, minimize changing yaw
        if (isVertical(blockPos, 0.7)) {
            // Set the target to the player's position
            double x = playerPos.x - (double) blockPos.getX();
            double z = playerPos.z - (double) blockPos.getZ();
            double yaw = Math.toRadians(mc.player.getYaw() + 40 * (Math.random() - 0.5) + 90);

            // Adjust the target slightly to avoid yaw resetting to 0
            randomOffsetOnFace = new Vec3d(x + Math.cos(yaw) / 64, 0.5, z + Math.sin(yaw) / 64);

            return;
        }

        // Select a random visible side of the block
        ArrayList<Direction> sideList = new ArrayList<>(visibleFaceMap.get(blockPos));
        Direction side = sideList.get(new Random().nextInt(sideList.size()));
    
        // <narakomii> this seems to break when i remove this scope, i probably did something dumb and didn't notice - i'm too lazy to fix it rn
        {
            // Select a random offset
            double x = Math.random();
            double y = Math.random();
            double z = Math.random();

            // Clamp the offset to the selected side
            if (side.getOffsetX() == 1) x = 1;
            else if (side.getOffsetX() == -1) x = 0;
            else if (side.getOffsetY() == 1) y = 1;
            else if (side.getOffsetY() == -1) y = 0;
            else if (side.getOffsetZ() == 1) z = 1;
            else if (side.getOffsetZ() == -1) z = 0;
            
            randomOffsetOnFace = new Vec3d(x, y, z);
        }
    }

    private final BlockPos.Mutable pos1 = new BlockPos.Mutable(); // Rendering for cubes
    private final BlockPos.Mutable pos2 = new BlockPos.Mutable();
    int maxh = 0;
    int maxv = 0;

    public Nuker() {
        super(Categories.World, "nuker", "Breaks blocks around you.");
    }

    @Override
    public void onActivate() {
        resetValues();
    }

    private void resetValues() {
        currentBlock = null;
        firstBlock = true;
        timer = timerMax = smoothRotate.get() ? delay.get() : 0;
        startYaw = mc.player.getYaw();
        startPitch = mc.player.getPitch();
        noBlockTimer = 0;
        lastBlockPos.set((int) Math.floor(mc.player.getEyePos().x), (int) Math.floor(mc.player.getEyePos().y), (int) Math.floor(mc.player.getEyePos().z));
        lastBlockType = null;
        interacted.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (enableRenderBounding.get()) {
            // Render bounding box if cube and should break stuff
            if (shape.get() != Shape.Sphere && mode.get() != Mode.Smash) {
                int minX = Math.min(pos1.getX(), pos2.getX());
                int minY = Math.min(pos1.getY(), pos2.getY());
                int minZ = Math.min(pos1.getZ(), pos2.getZ());
                int maxX = Math.max(pos1.getX(), pos2.getX());
                int maxY = Math.max(pos1.getY(), pos2.getY());
                int maxZ = Math.max(pos1.getZ(), pos2.getZ());
                event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, sideColorBox.get(), lineColorBox.get(), shapeModeBox.get(), 0);
            }
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action == KeyAction.Press) addTargetedBlockToList();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Press) addTargetedBlockToList();
    }

    // Sorts nearest to player first, divided to lower importance below the other sort value results
    private double secondarySort(BlockPos blockPos) {
        Vec3d eyePos = mc.player.getEyePos();
        return Utils.squaredDistance(eyePos.x, eyePos.y, eyePos.z, ((double) blockPos.getX()) + 0.5, ((double) blockPos.getY()) + 0.5, ((double) blockPos.getZ()) + 0.5) / 262144;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Update timer
        if (timer > 0) {
            timer--;
        }

        visibleFaceMap.clear();

        // Calculate some stuff
        boolean smoothRotateEnabled = rotate.get() && maxBlocksPerTick.get() <= 1 && smoothRotate.get();

        Vec3d eyePos = mc.player.getEyePos();
        BlockPos playerBlockPos = mc.player.getBlockPos();

        double pX = eyePos.x, pY = eyePos.y, pZ = eyePos.z;
        double rangeD = range.get() + 2;
        double rangeSq = Math.pow(rangeD, 2);

        if (shape.get() == Shape.UniformCube) range.set((double) Math.round(rangeD));

        double pX_ = pX;
        double pZ_ = pZ;
        int r = (int) Math.round(rangeD);

        if (shape.get() == Shape.UniformCube) {
            pX_ += 1; // weird position stuff
            pos1.set(pX_ - r, pY - r + 1, pZ - r + 1); // down
            pos2.set(pX_ + r - 1, pY + r, pZ + r); // up
            maxh = 0;
            maxv = 0;
        } else {
            // Only change me if you want to mess with 3D rotations:
            // I messed with it
            Direction direction = mc.player.getHorizontalFacing();
            switch (direction) {
                case Direction.SOUTH -> {
                    pZ_ += 1;
                    pX_ += 1;
                    pos1.set(pX_ - (range_right.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_back.get() + 1)); // down
                    pos2.set(pX_ + range_left.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_forward.get()); // up
                }
                case Direction.WEST -> {
                    pos1.set(pX_ - range_forward.get(), Math.ceil(pY) - range_down.get(), pZ_ - range_right.get()); // down
                    pos2.set(pX_ + range_back.get() + 1, Math.ceil(pY + range_up.get() + 1), pZ_ + range_left.get() + 1); // up
                }
                case Direction.NORTH -> {
                    pX_ += 1;
                    pZ_ += 1;
                    pos1.set(pX_ - (range_left.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_forward.get() + 1)); // down
                    pos2.set(pX_ + range_right.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_back.get()); // up
                }
                case Direction.EAST -> {
                    pX_ += 1;
                    pos1.set(pX_ - (range_back.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - range_left.get()); // down
                    pos2.set(pX_ + range_forward.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_right.get() + 1); // up
                }
            }

            // get largest horizontal
            maxh = 1 + Math.max(Math.max(Math.max(range_back.get(), range_right.get()), range_forward.get()), range_left.get());
            maxv = 1 + Math.max(range_up.get(), range_down.get());
        }

        // Flatten
        if (mode.get() == Mode.Flatten) pos1.setY((int) Math.floor(pY + 0.5));

        Box box = new Box(pos1.toCenterPos(), pos2.toCenterPos());

        // Find blocks to break
        BlockIterator.register(Math.max((int) Math.ceil(rangeD + 1), maxh), Math.max((int) Math.ceil(rangeD), maxv), (blockPos, blockState) -> {
            Vec3d center = blockPos.toCenterPos();
            switch (shape.get()) {
                case Sphere -> {
                    if (Utils.squaredDistance(pX, pY, pZ, center.getX(), center.getY(), center.getZ()) > rangeSq) return;
                }
                case UniformCube -> {
                    if (chebyshevDist(playerBlockPos.getX(), playerBlockPos.getY(), playerBlockPos.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ()) >= rangeD) return;
                }
                case Cube -> {
                    if (!box.contains(center)) return;
                }
            }

            // Flatten
            if (mode.get() == Mode.Flatten && blockPos.getY() + 0.5 < pY) return;

            // Smash
            if (mode.get() == Mode.Smash && blockState.getHardness(mc.world, blockPos) != 0) return;

            // Use only optimal tools
            if (suitableTools.get() && !interact.get() && !mc.player.getMainHandStack().isSuitableFor(blockState)) return;

            // Block must be breakable
            if (!BlockUtils.canBreak(blockPos, blockState) && !interact.get()) return;

            // Check whitelist or blacklist
            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(blockState.getBlock())) return;
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(blockState.getBlock())) return;

            if (interact.get() && interacted.contains(blockPos)) return;

            // Raycast to block
            if (isOutOfRange(blockPos)) return;

            // Add block
            blocks.add(blockPos.toImmutable());
        });

        // Break block if found
        BlockIterator.after(() -> {
            // Sort blocks
            if (sortMode.get() == SortMode.TopDown)
                blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
            else if (sortMode.get() == SortMode.ClosestToLast && noBlockTimer <= 0) {
                // Sort by closest to last mined block, then closest to player
                blocks.sort(Comparator.comparingDouble(value ->
                    Utils.squaredDistance((double) lastBlockPos.getX(), (double) lastBlockPos.getY(), (double) lastBlockPos.getZ(), (double) value.getX(), (double) value.getY(), (double) value.getZ())
                    + secondarySort(value)
                ));
            } else if (sortMode.get() == SortMode.ClosestAngleToLast && noBlockTimer <= 0) {
                RenderUtils.renderTickingPoint(mc.player.getEyePos().add(Vec3d.fromPolar((float) startPitch, (float) startYaw)), Color.WHITE, 1, false);
                // Sort by closest (by angle) to last mined block, then closest to player
                blocks.sort(Comparator.comparingDouble(value -> {
                    // Ignore calculated yaw difference if the block is directly above/below the player
                    if (isVertical(value))
                        return Math.abs(MathHelper.wrapDegrees(90 - startPitch))
                        + secondarySort(value);
                    else
                        return Math.abs(MathHelper.wrapDegrees(Rotations.getYaw(value.toCenterPos()) - startYaw)) * 1.3 /* <- Increase importance of yaw compared to pitch */
                        + Math.abs(MathHelper.wrapDegrees(Rotations.getPitch(value.toCenterPos()) - startPitch))
                        + secondarySort(value);
                }));
            } else if (sortMode.get() != SortMode.None)
                blocks.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Furthest ? -1 : 1)));

            // Check if no block was found
            if (blocks.isEmpty()) {
                // If no block was found for long enough then set firstBlock flag to true to not wait before breaking another again
                if (!smoothRotateEnabled && noBlockTimer++ >= delay.get()) firstBlock = true;

                resetValues();
                return;
            }
            else {
                noBlockTimer = 0;
            }

            // Check if a block is already being mined
            if (currentBlock != null) {
                // Check if it's still valid
                if (!BlockUtils.canInstaBreak(currentBlock) && !packetMine.get() && blocks.contains(currentBlock)) {
                    // Move it to the start of the list (will be iterated first)
                    blocks.remove(currentBlock);
                    blocks.addFirst(currentBlock);
                } else {
                    currentBlock = null;
                }
            }

            // Update timer
            if (!firstBlock && (!lastBlockPos.equals(blocks.getFirst()) || lastBlockType != mc.world.getBlockState(blocks.getFirst()).getBlock())) {
                timer = timerMax = delay.get();

                firstBlock = false;

                // Try to reset random face offset
                //resetOffsetOnFace(blocks.getFirst());
            }

            // Break
            int count = 0;

            for (BlockPos block : blocks) {
                if (count >= maxBlocksPerTick.get()) break;

                boolean canInstaMine = BlockUtils.canInstaBreak(block);

                // Try to reset random face offset
                resetOffsetOnFace(block);

                // Add the offset to the block's position
                Vec3d lookPos = new Vec3d(block).add(randomOffsetOnFace);

                if (enableRenderBreaking.get()) RenderUtils.renderTickingBlock(block, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
                if (enableRenderDebug.get()) RenderUtils.renderTickingPoint(lookPos, Color.GREEN, 1, false);

                currentBlock = block;
                count++;

                if (timer <= 0) {
                    // If delay is over, mine the block
                    if (rotate.get())
                        Rotations.rotate(startYaw = Rotations.getYaw(lookPos), startPitch = Rotations.getPitch(lookPos), () -> breakBlock(block));
                    else
                        breakBlock(block);
                } else if (smoothRotateEnabled) {
                    // If not and smooth rotate is enabled, lerp to the target
                    double endYaw = Rotations.getYaw(lookPos);
                    double endPitch = Rotations.getPitch(lookPos);
                    double delta = 1 - ((double) timer) / timerMax;

                    // Weird easing hybrid of EaseOutQuart and modified EaseOutBack
                    final double c = 0.37;
                    delta = (1 + (c + 1) * Math.pow(delta - 1, 3) + c * Math.pow(delta - 1, 2)) * (1 - Math.pow(1 - delta, 4));

                    double yaw = MathHelper.lerpAngleDegrees(delta, startYaw, endYaw);
                    double pitch = MathHelper.lerpAngleDegrees(delta, startPitch, endPitch);

                    Rotations.rotate(yaw, pitch);
                    break;
                }

                if (!canInstaMine && !packetMine.get() /* With packet mine attempt to break everything possible at once */) break;
            }

            firstBlock = false;

            // Clear current block positions
            blocks.clear();
        });
    }

    private void breakBlock(BlockPos blockPos) {
        if (interact.get()) {
            // Interact mode
            BlockUtils.interact(new BlockHitResult(blockPos.toCenterPos(), BlockUtils.getDirection(blockPos), blockPos, true), Hand.MAIN_HAND, swing.get());
            interacted.add(blockPos);
        } else if (packetMine.get()) {
            // Packet mine mode
            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos), sequence));

            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos), sequence));
        } else {
            // Legit mine mode
            BlockUtils.breakBlock(blockPos, swing.get());
        }
    }

    private Multimap<BlockPos, Direction> visibleFaceMap = HashMultimap.create();

    private static final double boxShrink = Math.pow(2, -16);

    private boolean isOutOfRange(BlockPos blockPos) {
        boolean allOutOfRange = true;

        Vec3d eyePos = mc.player.getEyePos();
        double rangeSq = Math.pow(range.get(), 2);
        double wallsRangeSq = Math.pow(wallsRange.get(), 2);

        VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.player.getEntityWorld(), blockPos);

        // Iterate over each cuboid of the block's interaction box
        for (Box box : shape.getBoundingBoxes()) {
            // Shrink cuboid by a tiny amount to avoid false misses, but preserve accuracy
            // Then offset by block position
            box = box.contract(boxShrink).offset(blockPos);

            // Iterate over each corner of the adjusted cuboid
            for (Vec3d corner : Utils.getBoxCorners(box)) {
                // Raycast to the corner
                RaycastContext raycastContext = new RaycastContext(eyePos, corner, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
                BlockHitResult result = mc.world.raycast(raycastContext);

                boolean outOfRange = eyePos.squaredDistanceTo(corner) > wallsRangeSq /* <- Check if the block is within through-wall range */
                    && (result == null || !result.getBlockPos().equals(blockPos) || eyePos.squaredDistanceTo(result.getPos()) > rangeSq);

                if (!outOfRange) {
                    // If in range, add the block face to the map
                    visibleFaceMap.put(blockPos.toImmutable(), result.getSide());

                    // Then, if debug rendering is disabled: break and return false (block is not out of range) immediately
                    // If enabled: set return value to false, draw the corner, and continue iterating
                    if (!enableRenderDebug.get()) {
                       return false;
                    } else {
                        allOutOfRange = false;
                        RenderUtils.renderTickingPoint(corner, Color.BLUE, 1, false);
                    }
                }
            }
        }

        return allOutOfRange;
    }

    private void addTargetedBlockToList() {
        if (!selectBlockBind.get().isPressed() || mc.currentScreen != null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        Block targetBlock = mc.world.getBlockState(pos).getBlock();

        List<Block> list = listMode.get() == ListMode.Whitelist ? whitelist.get() : blacklist.get();
        String modeName = listMode.get().name();

        if (list.contains(targetBlock)) {
            list.remove(targetBlock);
            info("Removed " + Names.get(targetBlock) + " from " + modeName);
        } else {
            list.add(targetBlock);
            info("Added " + Names.get(targetBlock) + " to " + modeName);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = 0;
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public enum Mode {
        All,
        Flatten,
        Smash
    }

    public enum SortMode {
        None,
        Closest,
        Furthest,
        TopDown,
        ClosestToLast,
        ClosestAngleToLast
    }

    public enum Shape {
        Cube,
        UniformCube,
        Sphere
    }

    public static int chebyshevDist(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Gets the largest X, Y or Z difference, chebyshev distance
        int dX = Math.abs(x2 - x1);
        int dY = Math.abs(y2 - y1);
        int dZ = Math.abs(z2 - z1);
        return Math.max(Math.max(dX, dY), dZ);
    }
}