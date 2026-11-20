-- The extraction a manual dispatch runs: join a world, write the registry, quit.
--
-- `__OUTPUT_FILE__` and `__EXPECTED_VERSION__` are substituted by run.sh when it stages this file,
-- so the same script serves every version branch and every output name. The file it writes is
-- checked by run.sh after the client exits, because a script that reached this point is not the
-- same thing as a registry on disk.
local MOD = "registry_extractor"
local WORLD = "extractor_registry" -- fixed, so a re-run reuses the save instead of adding another
local OUTPUT = "__OUTPUT_FILE__"

if minecheck.clickGui("Proceed to main menu", 0, 1) then
  minecheck.expect(minecheck.waitFor(minecheck.isOnTitleScreen, 30), "dismissed the mod-loading screen")
end

if not minecheck.expect(minecheck.isModLoaded(MOD), MOD .. " is loaded") then
  minecheck.fail(MOD .. " is not loaded — is the extractor jar in the run's mods/ folder?")
end

local version = minecheck.minecraftVersion()
minecheck.log("minecheck reports Minecraft " .. tostring(version))
minecheck.expect(version == "__EXPECTED_VERSION__",
                  "running on Minecraft __EXPECTED_VERSION__, got " .. tostring(version))

-- The extraction walks the registries of the loaded modpack, so a world has to be open first.
if not minecheck.expect(minecheck.createWorld({ name = WORLD }), "created and joined the world") then
  minecheck.fail("createWorld failed")
end
minecheck.expect(minecheck.isInWorld(), "the client is in the world")

minecheck.runCommand("extract " .. OUTPUT)
minecheck.log("issued: /extract " .. OUTPUT)
minecheck.sleep(5)

minecheck.leaveWorld(30)
minecheck.quit()
