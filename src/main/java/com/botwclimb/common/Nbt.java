package com.botwclimb.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reading and writing saved data; the NBT calls changed shape in 1.21.11 (reads that return an Optional, no putUUID; since 1.21.5), so they live here. */
public final class Nbt {
    private Nbt() {}

    public static int getInt(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getIntOr(key, 0);
        *///?} else {
        return t.getInt(key);
        //?}
    }

    public static long getLong(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getLongOr(key, 0L);
        *///?} else {
        return t.getLong(key);
        //?}
    }

    public static float getFloat(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getFloatOr(key, 0f);
        *///?} else {
        return t.getFloat(key);
        //?}
    }

    public static double getDouble(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getDoubleOr(key, 0.0);
        *///?} else {
        return t.getDouble(key);
        //?}
    }

    public static String getString(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getStringOr(key, "");
        *///?} else {
        return t.getString(key);
        //?}
    }

    public static int[] getIntArray(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.getIntArray(key).orElse(new int[0]);
        *///?} else {
        return t.getIntArray(key);
        //?}
    }

    /** The compounds of a list. */
    public static List<CompoundTag> list(CompoundTag t, String key) {
        List<CompoundTag> out = new ArrayList<>();
        //? if >=1.21.5 {
        /*ListTag l = t.getListOrEmpty(key);
        for (Tag raw : l) if (raw instanceof CompoundTag c) out.add(c);
        *///?} else {
        for (Tag raw : t.getList(key, Tag.TAG_COMPOUND)) out.add((CompoundTag) raw);
        //?}
        return out;
    }

    public static void putUuid(CompoundTag t, String key, UUID id) {
        //? if >=1.21.5 {
        /*t.store(key, net.minecraft.core.UUIDUtil.CODEC, id);
        *///?} else {
        t.putUUID(key, id);
        //?}
    }

    public static UUID getUuid(CompoundTag t, String key) {
        //? if >=1.21.5 {
        /*return t.read(key, net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
        *///?} else {
        return t.getUUID(key);
        //?}
    }
}
