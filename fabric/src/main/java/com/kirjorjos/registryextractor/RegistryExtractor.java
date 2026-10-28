package com.kirjorjos.registryextractor;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistryExtractor implements ClientModInitializer
{
    public static final String MOD_ID = "registry_extractor";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient()
    {
        LOGGER.info("Registry Extractor Initializing...");
        ModCommands.register();
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
