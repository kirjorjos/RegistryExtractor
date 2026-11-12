package com.kirjorjos.registryextractor;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
	modid = RegistryExtractor.MOD_ID,
	bus = Mod.EventBusSubscriber.Bus.FORGE,
	value = Dist.CLIENT
)
public class ModCommands {
	
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
			ExtractCommand.register(event.getDispatcher());
	}

}
