package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.PortableBeacons;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class RecipeManager {


    public static final Map<String, BeaconRecipe> RECIPES = new LinkedHashMap<>();
    private static final Supplier<? extends Yaml> YAML_SUPPLIER;
    static {
        Supplier<? extends Yaml> yamlSupplier;
        try {
            // Bukkit YAML
            Field yamlField = YamlConfiguration.class.getDeclaredField("yaml");
            yamlField.setAccessible(true);
            yamlSupplier = () -> {
                try {
                    return (Yaml) yamlField.get(new YamlConfiguration());
                } catch (ReflectiveOperationException ex) {
                    throw new Error(ex);
                }
            };
        } catch (ReflectiveOperationException ex) {
            yamlSupplier = Yaml::new;
        }
        YAML_SUPPLIER = yamlSupplier;
    }

    public static void loadRecipes() {
        RECIPES.clear();
        PortableBeacons plugin = PortableBeacons.INSTANCE;
        var recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            return;
        }
        Yaml yaml = YAML_SUPPLIER.get(); // BukkitYaml
        try (var inputStream = new FileInputStream(recipesFile)) {
            Map<String, Object> map = yaml.load(inputStream);
            for (var entry : ((Map<String, Object>) map.get("recipes")).entrySet()) {
                String recipeName = entry.getKey();
                try {
                    Map<String, Object> recipeMap = (Map<String, Object>) entry.getValue();
                    RECIPES.put(recipeName, BeaconRecipe.load(recipeMap));
                } catch (IllegalArgumentException | ClassCastException ex) {
                    plugin.logger.warning("Failed to load recipe " + recipeName + ": " + ex);
                }
            }
            plugin.logger.info("Loaded " + RECIPES.size() + " recipes");
        } catch (IOException ex) {
            plugin.logger.severe("Failed to open recipes.yml: " + ex);
        }
    }

}
