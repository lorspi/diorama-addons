package io.github.dioramaaddons.webmap.compat.mochos.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dioramaaddons.webmap.WebMapService;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mochos Esenciales replaces the vanilla chat: it cancels {@code ServerChatEvent} and delivers messages with its
 * own payloads, firing no event the web map could listen to. Its global channel logs every accepted message
 * with {@code LOGGER.info("<{}> {}", name, text)} (the only two-argument {@code info} call in
 * {@code Chat.send}, after mute and spam checks), so that call is wrapped to copy the message to the web chat.
 * Group and direct messages are private and never published. If the method changes shape the injector simply
 * does not apply (require = 0) and the web chat misses those messages; nothing else breaks.
 */
@Pseudo
@Mixin(targets = "io.github.mochos.esenciales.server.Chat", remap = false)
public abstract class MochosChatMixin {
    @WrapOperation(method = "send", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private void dioaddonsWebmap$globalChat(Logger logger, String format, Object name, Object text,
            Operation<Void> original) {
        original.call(logger, format, name, text);
        WebMapService s = WebMapService.get();
        if (s != null && "<{}> {}".equals(format)) {
            s.onChat(String.valueOf(name), String.valueOf(text));
        }
    }
}
