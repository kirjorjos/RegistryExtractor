local MOD = "registry_extractor"
local WORLD = "minecheck_world"
local OUT_COMMAND = "extractor-test.json" -- via minecheck.runCommand
local OUT_TYPED = "extractor-typed.json" -- via typeText + press("enter")

if minecheck.clickGui("Proceed to main menu", 0, 1) then
  minecheck.expect(minecheck.waitFor(minecheck.isOnTitleScreen, 30), "dismissed the mod-loading screen")
end

if not minecheck.expect(minecheck.isModLoaded(MOD), MOD .. " is loaded") then
  minecheck.fail(MOD .. " is not loaded — is the extractor jar in the run's mods/ folder?")
end

local version = minecheck.minecraftVersion()
minecheck.log("minecheck reports Minecraft " .. tostring(version))
-- `__EXPECTED_VERSION__` is substituted from gradle.properties by run.sh when it stages this file,
-- so the same script serves every version branch.
minecheck.expect(version == "__EXPECTED_VERSION__",
                  "running on Minecraft __EXPECTED_VERSION__, got " .. tostring(version))

local created = minecheck.createWorld({ name = WORLD })
if not minecheck.expect(created, "created and joined the world") then
  minecheck.fail("createWorld failed")
end
minecheck.expect(minecheck.isInWorld(), "the client is in the world")

minecheck.runCommand("extract " .. OUT_COMMAND)
minecheck.log("issued: /extract " .. OUT_COMMAND)

if not minecheck.expect(minecheck.typeText("extract " .. OUT_TYPED, "command"),
                        "typed the command into the chat screen") then
  minecheck.fail("typeText had nothing to type into — is the chat screen opening?")
end
minecheck.press("enter")
minecheck.sleep(2)
minecheck.log("typed: /extract " .. OUT_TYPED .. " (via the chat input path)")

minecheck.runCommand("extract .")
minecheck.log("issued: /extract . (expects the run dir to gain registry.json)")

-- The argument is a greedy string, so a path with spaces needs no quoting.
if not minecheck.expect(minecheck.typeText("extract extractor-spaced file.json", "command"),
                        "typed an unquoted path containing a space") then
  minecheck.fail("typeText had nothing to type into — is the chat screen opening?")
end
minecheck.press("enter")
minecheck.sleep(2)
minecheck.log("issued: /extract extractor-spaced file.json (spaces, unquoted)")

-- `~` expands to the executing JVM's user.home; the validator looks for the file there and removes it.
minecheck.runCommand("extract ~/.minecheck-extractor-home.json")
minecheck.sleep(2)
minecheck.log("issued: /extract ~/.minecheck-extractor-home.json")

minecheck.sleep(2)
minecheck.leaveWorld(30)
minecheck.quit()
