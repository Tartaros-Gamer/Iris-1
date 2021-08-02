/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.B;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public class SchemaBuilder {
    private static final String SYMBOL_LIMIT__N = "*";
    private static final String SYMBOL_TYPE__N = "";
    private static final JSONArray POTION_TYPES = getPotionTypes();
    private static final JSONArray ENCHANT_TYPES = getEnchantmentTypes();
    private static final JSONArray ITEM_TYPES = new JSONArray(B.getItemTypes());
    private static final JSONArray FONT_TYPES = new JSONArray(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    private final KMap<String, JSONObject> definitions;
    private final Class<?> root;
    private final KList<String> warnings;
    private final IrisDataManager data;

    public SchemaBuilder(Class<?> root, IrisDataManager data) {
        this.data = data;
        warnings = new KList<>();
        this.definitions = new KMap<>();
        this.root = root;
    }

    public JSONObject compute() {
        JSONObject schema = new JSONObject();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("$id", "http://volmit.com/iris-schema/" + root.getSimpleName().toLowerCase() + ".json");

        JSONObject props = buildProperties(root);

        for (String i : props.keySet()) {
            if (!schema.has(i)) {
                schema.put(i, props.get(i));
            }
        }

        JSONObject defs = new JSONObject();

        for (Map.Entry<String, JSONObject> entry : definitions.entrySet()) {
            defs.put(entry.getKey(), entry.getValue());
        }

        schema.put("definitions", defs);

        for (String i : warnings) {
            Iris.warn(root.getSimpleName() + ": " + i);
        }

        return schema;
    }

    private JSONObject buildProperties(Class<?> c) {
        JSONObject o = new JSONObject();
        JSONObject properties = new JSONObject();
        o.put("description", getDescription(c));
        o.put("type", getType(c));
        JSONArray required = new JSONArray();

        for (Field k : c.getDeclaredFields()) {
            k.setAccessible(true);

            if (Modifier.isStatic(k.getModifiers()) || Modifier.isFinal(k.getModifiers()) || Modifier.isTransient(k.getModifiers())) {
                continue;
            }

            JSONObject property = buildProperty(k, c);

            if (property.getBoolean("!required")) {
                required.put(k.getName());
            }

            property.remove("!required");
            properties.put(k.getName(), property);
        }

        if (required.length() > 0) {
            o.put("required", required);
        }

        o.put("properties", properties);

        return o;
    }

    private JSONObject buildProperty(Field k, Class<?> cl) {
        JSONObject prop = new JSONObject();
        String type = getType(k.getType());
        KList<String> description = new KList<>();
        prop.put("!required", k.isAnnotationPresent(Required.class));
        prop.put("type", type);
        String fancyType = "Unknown Type";

        switch (type) {
            case "boolean" -> fancyType = "Boolean";
            case "integer" -> {
                fancyType = "Integer";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minimum", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maximum", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum allowed is " + max);
                }
            }
            case "number" -> {
                fancyType = "Number";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    double min = k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minimum", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    double max = k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maximum", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum allowed is " + max);
                }
            }
            case "string" -> {
                fancyType = "Text";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minLength", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum Length allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maxLength", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum Length allowed is " + max);
                }
                // TODO Automate registry lists
                if (k.isAnnotationPresent(RegistryListBiome.class)) {
                    String key = "enum-reg-biome";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getBiomeLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Biome";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Biome (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListMythical.class)) {
                    String key = "enum-reg-mythical";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(Iris.linkMythicMobs.getMythicMobTypes()));
                        definitions.put(key, j);
                    }

                    fancyType = "Mythic Mob Type";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Mythic Mob Type (use ctrl+space for auto complete!) Define mythic mobs with the mythic mobs plugin configuration files.");
                }
                else if (k.isAnnotationPresent(RegistryListSpawner.class)) {
                    String key = "enum-reg-spawner";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getSpawnerLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Spawner";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Spawner (use ctrl+space for auto complete!)");
                }
                else if (k.isAnnotationPresent(RegistryListBlockType.class)) {
                    String key = "enum-block-type";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        JSONArray ja = new JSONArray();

                        for (String i : data.getBlockLoader().getPossibleKeys()) {
                            ja.put(i);
                        }

                        for (String i : B.getBlockTypes()) {
                            ja.put(i);
                        }

                        j.put("enum", ja);
                        definitions.put(key, j);
                    }

                    fancyType = "Block Type";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListItemType.class)) {
                    String key = "enum-item-type";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", ITEM_TYPES);
                        definitions.put(key, j);
                    }

                    fancyType = "Item Type";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListEntity.class)) {
                    String key = "enum-reg-entity";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getEntityLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Entity";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Iris Entity (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListFont.class)) {
                    String key = "enum-font";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", FONT_TYPES);
                        definitions.put(key, j);
                    }

                    fancyType = "Font Family";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListLoot.class)) {
                    String key = "enum-reg-loot-table";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getLootLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Loot Table";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Loot Table (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListDimension.class)) {
                    String key = "enum-reg-dimension";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getDimensionLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Dimension";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Dimension (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListGenerator.class)) {
                    String key = "enum-reg-generator";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getGeneratorLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Generator";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Generator (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListObject.class)) {
                    String key = "enum-reg-object";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getObjectLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Object";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Object (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListRegion.class)) {
                    String key = "enum-reg-region";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getRegionLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Region";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Region (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListJigsawPiece.class)) {
                    String key = "enum-reg-structure-piece";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getJigsawPieceLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Jigsaw Piece";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw Piece (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListJigsaw.class)) {
                    String key = "enum-reg-jigsaw";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getJigsawStructureLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Jigsaw";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListJigsawPool.class)) {
                    String key = "enum-reg-structure-pool";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(data.getJigsawPoolLoader().getPossibleKeys()));
                        definitions.put(key, j);
                    }

                    fancyType = "Iris Jigsaw Pool";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw Piece (use ctrl+space for auto complete!)");
                } else if (k.getType().equals(Enchantment.class)) {
                    String key = "enum-enchantment";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", ENCHANT_TYPES);
                        definitions.put(key, j);
                    }

                    fancyType = "Enchantment Type";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
                } else if (k.getType().equals(PotionEffectType.class)) {
                    String key = "enum-potion-effect-type";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", POTION_TYPES);
                        definitions.put(key, j);
                    }

                    fancyType = "Potion Effect Type";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");

                } else if (k.getType().isEnum()) {
                    fancyType = k.getType().getSimpleName().replaceAll("\\QIris\\E", "");
                    JSONArray a = new JSONArray();
                    boolean advanced = k.getType().isAnnotationPresent(Desc.class);
                    for (Object gg : k.getType().getEnumConstants()) {
                        if (advanced) {
                            try {
                                JSONObject j = new JSONObject();
                                String name = ((Enum<?>) gg).name();
                                j.put("const", name);
                                Desc dd = k.getType().getField(name).getAnnotation(Desc.class);
                                j.put("description", dd == null ? ("No Description for " + name) : dd.value());
                                a.put(j);
                            } catch (Throwable e) {
                                Iris.reportError(e);
                                e.printStackTrace();
                            }
                        } else {
                            a.put(((Enum<?>) gg).name());
                        }
                    }

                    String key = (advanced ? "oneof-" : "") + "enum-" + k.getType().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put(advanced ? "oneOf" : "enum", a);
                        definitions.put(key, j);
                    }

                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid " + k.getType().getSimpleName().replaceAll("\\QIris\\E", "") + " (use ctrl+space for auto complete!)");

                }
            }
            case "object" -> {
                fancyType = k.getType().getSimpleName().replaceAll("\\QIris\\E", "") + " (Object)";
                String key = "obj-" + k.getType().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
                if (!definitions.containsKey(key)) {
                    definitions.put(key, new JSONObject());
                    definitions.put(key, buildProperties(k.getType()));
                }
                prop.put("$ref", "#/definitions/" + key);
            }
            case "array" -> {
                fancyType = "List of Something...?";
                ArrayType t = k.getDeclaredAnnotation(ArrayType.class);
                if (t != null) {
                    if (t.min() > 0) {
                        prop.put("minItems", t.min());
                        if (t.min() == 1) {
                            description.add(SYMBOL_LIMIT__N + " At least one entry must be defined, or just remove this list.");
                        } else {
                            description.add(SYMBOL_LIMIT__N + " Requires at least " + t.min() + " entries.");
                        }
                    }

                    String arrayType = getType(t.type());

                    switch (arrayType) {
                        case "integer" -> fancyType = "List of Integers";
                        case "number" -> fancyType = "List of Numbers";
                        case "object" -> {
                            fancyType = "List of " + t.type().getSimpleName().replaceAll("\\QIris\\E", "") + "s (Objects)";
                            String key = "obj-" + t.type().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
                            if (!definitions.containsKey(key)) {
                                definitions.put(key, new JSONObject());
                                definitions.put(key, buildProperties(t.type()));
                            }
                            JSONObject items = new JSONObject();
                            items.put("$ref", "#/definitions/" + key);
                            prop.put("items", items);
                        }
                        case "string" -> {
                            fancyType = "List of Text";
                            if (k.isAnnotationPresent(RegistryListBiome.class)) {
                                fancyType = "List of Iris Biomes";
                                String key = "enum-reg-biome";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getBiomeLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Biome (use ctrl+space for auto complete!)");
                            }
                            else if (k.isAnnotationPresent(RegistryListSpawner.class)) {
                                fancyType = "List of Iris Spawners";
                                String key = "enum-reg-spawner";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getSpawnerLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Spawner (use ctrl+space for auto complete!)");
                            }
                            else if (k.isAnnotationPresent(RegistryListMythical.class)) {
                                fancyType = "List of Mythic Mob Types";
                                String key = "enum-reg-mythical";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    JSONArray ja = new JSONArray();

                                    for (String i : Iris.linkMythicMobs.getMythicMobTypes()) {
                                        ja.put(i);
                                    }

                                    j.put("enum", ja);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Mythic Mob Type (use ctrl+space for auto complete!) Configure mob types in the mythic mobs plugin configuration files.");
                            } else if (k.isAnnotationPresent(RegistryListBlockType.class)) {
                                fancyType = "List of Block Types";
                                String key = "enum-block-type";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    JSONArray ja = new JSONArray();

                                    for (String i : data.getBlockLoader().getPossibleKeys()) {
                                        ja.put(i);
                                    }

                                    for (String i : B.getBlockTypes()) {
                                        ja.put(i);
                                    }

                                    j.put("enum", ja);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListItemType.class)) {
                                fancyType = "List of Item Types";
                                String key = "enum-item-type";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", ITEM_TYPES);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListEntity.class)) {
                                fancyType = "List of Iris Entities";
                                String key = "enum-reg-entity";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getEntityLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Iris Entity (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListFont.class)) {
                                String key = "enum-font";
                                fancyType = "List of Font Families";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", FONT_TYPES);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListLoot.class)) {
                                fancyType = "List of Iris Loot Tables";
                                String key = "enum-reg-loot-table";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getLootLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Loot Table (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListDimension.class)) {
                                fancyType = "List of Iris Dimensions";
                                String key = "enum-reg-dimension";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getDimensionLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Dimension (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListGenerator.class)) {
                                fancyType = "List of Iris Generators";
                                String key = "enum-reg-generator";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getGeneratorLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Generator (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListObject.class)) {
                                fancyType = "List of Iris Objects";
                                String key = "enum-reg-object";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getObjectLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Object (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListRegion.class)) {
                                fancyType = "List of Iris Regions";
                                String key = "enum-reg-region";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getRegionLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Region (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListJigsawPiece.class)) {
                                fancyType = "List of Iris Jigsaw Pieces";
                                String key = "enum-reg-structure-piece";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getJigsawPieceLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw Piece (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListJigsawPool.class)) {
                                fancyType = "List of Iris Jigsaw Pools";
                                String key = "enum-reg-structure-pool";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getJigsawPoolLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw Pool (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListJigsaw.class)) {
                                fancyType = "List of Iris Jigsaw Structures";
                                String key = "enum-reg-jigsaw";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(data.getJigsawStructureLoader().getPossibleKeys()));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Jigsaw (use ctrl+space for auto complete!)");
                            } else if (t.type().equals(Enchantment.class)) {
                                fancyType = "List of Enchantment Types";
                                String key = "enum-enchantment";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", ENCHANT_TYPES);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
                            } else if (t.type().equals(PotionEffectType.class)) {
                                fancyType = "List of Potion Effect Types";
                                String key = "enum-potion-effect-type";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", POTION_TYPES);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");
                            } else if (t.type().isEnum()) {
                                fancyType = "List of " + t.type().getSimpleName().replaceAll("\\QIris\\E", "") + "s";
                                JSONArray a = new JSONArray();
                                boolean advanced = t.type().isAnnotationPresent(Desc.class);
                                for (Object gg : t.type().getEnumConstants()) {
                                    if (advanced) {
                                        try {
                                            JSONObject j = new JSONObject();
                                            String name = ((Enum<?>) gg).name();
                                            j.put("const", name);
                                            Desc dd = t.type().getField(name).getAnnotation(Desc.class);
                                            j.put("description", dd == null ? ("No Description for " + name) : dd.value());
                                            a.put(j);
                                        } catch (Throwable e) {
                                            Iris.reportError(e);
                                            e.printStackTrace();
                                        }
                                    } else {
                                        a.put(((Enum<?>) gg).name());
                                    }
                                }

                                String key = (advanced ? "oneof-" : "") + "enum-" + t.type().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put(advanced ? "oneOf" : "enum", a);
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid " + t.type().getSimpleName().replaceAll("\\QIris\\E", "") + " (use ctrl+space for auto complete!)");
                            }
                        }
                    }
                } else {
                    warnings.add("Undefined array type for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
                }
            }
            default -> warnings.add("Unexpected Schema Type: " + type + " for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
        }

        KList<String> d = new KList<>();
        d.add(k.getName());
        d.add(getFieldDescription(k));
        d.add("   ");
        d.add(fancyType);
        d.add(getDescription(k.getType()));

        try {
            k.setAccessible(true);
            Object value = k.get(cl.newInstance());

            if (value != null) {
                if (value instanceof List) {
                    d.add("* Default Value is an empty list");
                } else if (!cl.isPrimitive() && !(value instanceof Number) && !(value instanceof String) && !(cl.isEnum())) {
                    d.add("* Default Value is a default object (create this object to see default properties)");
                } else {
                    d.add("* Default Value is " + value);
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        description.forEach((g) -> d.add(g.trim()));
        prop.put("type", type);
        prop.put("description", d.toString("\n"));

        return prop;
    }

    private String getType(Class<?> c) {
        if (c.equals(int.class) || c.equals(Integer.class) || c.equals(long.class)) {
            return "integer";
        }

        if (c.equals(float.class) || c.equals(double.class)) {
            return "number";
        }

        if (c.equals(boolean.class)) {
            return "boolean";
        }

        if (c.equals(String.class) || c.isEnum() || c.equals(Enchantment.class) || c.equals(PotionEffectType.class)) {
            return "string";
        }

        if (c.equals(KList.class)) {
            return "array";
        }

        if (c.equals(KMap.class)) {
            return "object";
        }

        if (!c.isAnnotationPresent(Desc.class)) {
            warnings.addIfMissing("Unsupported Type: " + c.getCanonicalName() + " Did you forget @Desc?");
        }

        return "object";
    }

    private String getFieldDescription(Field r) {
        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        warnings.addIfMissing("Missing @Desc on field " + r.getName() + " (" + r.getType() + ")");
        return "No Field Description";
    }

    private String getDescription(Class<?> r) {
        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        if (!r.isPrimitive() && !r.equals(KList.class) && !r.equals(KMap.class) && r.getCanonicalName().startsWith("com.volmit.")) {
            warnings.addIfMissing("Missing @Desc on " + r.getSimpleName());
        }
        return "";
    }

    private static JSONArray getEnchantmentTypes() {
        JSONArray a = new JSONArray();

        for (Field gg : Enchantment.class.getDeclaredFields()) {
            a.put(gg.getName());
        }

        return a;
    }

    private static JSONArray getPotionTypes() {
        JSONArray a = new JSONArray();

        for (PotionEffectType gg : PotionEffectType.values()) {
            a.put(gg.getName().toUpperCase().replaceAll("\\Q \\E", "_"));
        }

        return a;
    }
}
