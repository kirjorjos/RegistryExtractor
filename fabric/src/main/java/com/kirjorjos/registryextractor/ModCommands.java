package com.kirjorjos.registryextractor;

import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public class ModCommands {

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
				ExtractCommand.register(dispatcher));
	}

}
