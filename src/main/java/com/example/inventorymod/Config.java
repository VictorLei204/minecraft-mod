package com.example.inventorymod;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    // Define the Spec and the Config instance holder
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    // Static initializer to build the spec and create the config instance
    static {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    // Static fields directly accessed from the COMMON instance
    public static ForgeConfigSpec.BooleanValue enableItemTransfer;
    public static ForgeConfigSpec.ConfigValue<String> transferKey;

    // Class containing all our common configuration options
    public static class CommonConfig {
        public final ForgeConfigSpec.BooleanValue enableItemTransfer;
        public final ForgeConfigSpec.ConfigValue<String> transferKey;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Item Transfer Settings").push("item_transfer");

            enableItemTransfer = builder
                    .comment("Enable or disable automatic item transfer when the key is pressed.")
                    .define("enableItemTransfer", true);

            transferKey = builder
                    .comment("Key binding for item transfer (Informational, actual key is set in controls).")
                    .define("transferKey", "R");

            builder.pop();
            
            // Store references to the config values for easy access
            Config.enableItemTransfer = this.enableItemTransfer;
            Config.transferKey = this.transferKey;
        }
    }
}
