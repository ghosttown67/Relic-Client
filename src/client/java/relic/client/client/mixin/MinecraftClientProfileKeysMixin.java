package relic.client.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.RelicClient;

import java.net.Proxy;
import java.util.UUID;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientProfileKeysMixin {

    @Shadow @Final private Proxy networkProxy;

    @Shadow public abstract Session getSession();

    @Unique private UUID relic$lastKeysUuid = null;
    @Unique private String relic$lastKeysToken = null;
    @Unique private ProfileKeys relic$cachedKeys = null;

    @Inject(method = "getProfileKeys", at = @At("HEAD"), cancellable = true)
    private void relic$rebuildProfileKeysOnSessionSwap(CallbackInfoReturnable<ProfileKeys> cir) {
        Session session = getSession();
        UUID uuid = session.getUuidOrNull();
        String token = session.getAccessToken();

        boolean changed = relic$lastKeysUuid == null
                || !relic$lastKeysUuid.equals(uuid)
                || relic$lastKeysToken == null
                || !relic$lastKeysToken.equals(token);

        if (changed) {
            relic$lastKeysUuid = uuid;
            relic$lastKeysToken = token;
            try {

                YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(networkProxy);
                UserApiService userApiService = auth.createUserApiService(token);
                MinecraftClient mc = (MinecraftClient) (Object) this;
                relic$cachedKeys = ProfileKeys.create(userApiService, session, mc.runDirectory.toPath());
                RelicClient.LOGGER.info("Rebuilt profile keys for account {}", session.getUsername());
            } catch (Exception e) {
                RelicClient.LOGGER.error("Failed to rebuild profile keys for {}: {}",
                        session.getUsername(), e.toString());
                relic$cachedKeys = null;
            }
        }

        if (relic$cachedKeys != null) {
            cir.setReturnValue(relic$cachedKeys);
        }
    }
}
