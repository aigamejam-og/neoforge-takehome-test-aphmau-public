package dev.camkey;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = CamKeyMod.MOD_ID, dist = Dist.CLIENT)
public final class CamKeyMod {
    public static final String MOD_ID = "camkey";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CamKeyMod(IEventBus modBus) {
        SequenceStore.load();
    }
}
