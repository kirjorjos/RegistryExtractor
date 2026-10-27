local window_name = get_window_name()
local window_class = get_window_class()

-- Log everything for debugging
local file = io.open("devilspie2.log", "a")
if file then
    file:write(string.format("Name: %s | Class: %s\n", window_name or "nil", window_class or "nil"))
    file:close()
end

-- Catch Prism Launcher and Minecraft
if (window_name and (string.find(window_name, "Minecraft") or string.find(window_name, "Prism Launcher"))) 
   or (window_class and (string.find(window_class, "PrismLauncher") or string.find(window_class, "minecraft"))) then
    debug_print("Minimizing: " .. (window_name or window_class))
    minimize();
end
