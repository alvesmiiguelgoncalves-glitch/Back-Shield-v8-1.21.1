package net.backshield.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracao SOMENTE visual (cliente). Arquivo: config/backshield-client.json
 *
 * O arquivo e um objeto cujas chaves sao os PERFIS; mexer em um perfil nunca altera outro:
 *
 *   normal            - sem nada de especial
 *   item1             - 1 item nas costas (slot de costas do BackSlot)
 *   item2             - 2 itens nas costas (addon do BackSlot: espada na cintura + item nas costas)
 *   backpack          - mochila do Backpacked equipada
 *   chest             - peitoral (armadura) equipado
 *   chest_item1       - peitoral + 1 item nas costas
 *   chest_item2       - peitoral + 2 itens nas costas
 *   chest_backpack    - peitoral + mochila
 *   elytra            - elytra equipada, em pe
 *   elytra_backpack   - elytra + mochila
 *   flying            - voando com elytra (asas abertas)
 *   flying_backpack   - voando com elytra + mochila
 *   gliding           - elytra equipada, no ar com as asas fechadas
 *   gliding_backpack  - elytra, no ar com as asas fechadas + mochila
 *
 * Cada perfil tem x, y, z, rotX, rotY, rotZ e scale:
 *   x = lateral; y = para BAIXO a partir dos ombros; z = para TRAS a partir da superficie das costas;
 *   rotX/rotY/rotZ = graus (em torno do centro do escudo, ordem Y, X, Z);
 *   scale = DIFERENCA de tamanho (0 = normal, 0.5 = 50% maior, -0.25 = 25% menor).
 *
 * Perfis que ainda nao existem no arquivo sao criados como COPIA do perfil "pai" (veja PARENTS), para o escudo
 * nao ficar fora do lugar; depois e so ajustar. "/backshield reset <perfil>" zera um perfil (tudo 0).
 */
public final class BackShieldConfig {

    /** Ordem importa: o pai de um perfil sempre aparece antes dele. */
    public static final List<String> PROFILES = List.of(
            "normal", "item1", "item2", "backpack",
            "chest", "chest_item1", "chest_item2", "chest_backpack",
            "elytra", "elytra_backpack",
            "flying", "flying_backpack",
            "gliding", "gliding_backpack");

    /** De qual perfil um perfil NOVO copia seus valores iniciais. */
    private static final Map<String, String> PARENTS = Map.ofEntries(
            Map.entry("item1", "normal"),
            Map.entry("item2", "normal"),
            Map.entry("chest", "normal"),
            Map.entry("chest_item1", "chest"),
            Map.entry("chest_item2", "chest"),
            Map.entry("chest_backpack", "backpack"),
            Map.entry("elytra_backpack", "backpack"),
            Map.entry("flying_backpack", "flying"),
            Map.entry("gliding", "elytra"),
            Map.entry("gliding_backpack", "elytra_backpack"));

    public static final List<String> FIELDS = List.of("x", "y", "z", "rotX", "rotY", "rotZ", "scale");

    private static final Logger LOGGER = LoggerFactory.getLogger("backshield");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Profile>>() {
    }.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("backshield-client.json");
    private static final Path FILE_TMP = FabricLoader.getInstance().getConfigDir().resolve("backshield-client.json.tmp");

    private static volatile Map<String, Profile> profiles = new LinkedHashMap<>();

    private BackShieldConfig() {
    }

    /** Um conjunto de ajustes. Tudo 0 = pose base. */
    public static final class Profile {
        public float x = 0.0F;
        public float y = 0.0F;
        public float z = 0.0F;
        public float rotX = 0.0F;
        public float rotY = 0.0F;
        public float rotZ = 0.0F;
        public float scale = 0.0F;

        Profile copy() {
            Profile p = new Profile();
            p.x = x;
            p.y = y;
            p.z = z;
            p.rotX = rotX;
            p.rotY = rotY;
            p.rotZ = rotZ;
            p.scale = scale;
            return p;
        }
    }

    /** Nunca retorna null para um nome de PROFILES. */
    public static Profile profile(String name) {
        Profile p = profiles.get(name);
        return p != null ? p : new Profile();
    }

    public static synchronized void load() {
        Map<String, Profile> loaded = null;
        if (Files.exists(FILE)) {
            try {
                String text = Files.readString(FILE, StandardCharsets.UTF_8);
                loaded = GSON.fromJson(text, MAP_TYPE);
                // Formato antigo (campos soltos como "height"): guarda uma copia para nao perder os valores.
                if (loaded != null && !text.contains("\"normal\"")) {
                    backup("old");
                    loaded = null;
                }
            } catch (Exception e) {
                // Arquivo ilegivel: NAO sobrescreve sem antes guardar uma copia.
                LOGGER.warn("Could not read {} - keeping a copy as backshield-client.broken.json", FILE, e);
                backup("broken");
                loaded = null;
            }
        }
        Map<String, Profile> result = new LinkedHashMap<>();
        if (loaded != null) {
            loaded.forEach((k, v) -> {
                if (v != null) {
                    result.put(k, v);
                }
            });
        }
        // Perfis ausentes: copia do pai (ou zero se nao tiver pai) sem mexer nos que o usuario ja ajustou.
        for (String name : PROFILES) {
            if (!result.containsKey(name)) {
                Profile parent = result.get(PARENTS.get(name));
                result.put(name, parent != null ? parent.copy() : new Profile());
            }
        }
        profiles = result;
        save();
    }

    private static void backup(String suffix) {
        try {
            Files.copy(FILE, FILE.resolveSibling("backshield-client." + suffix + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up {}", FILE, e);
        }
    }

    /** Grava em arquivo temporario e troca de uma vez, para um crash nunca deixar o arquivo pela metade. */
    public static synchronized void save() {
        try {
            Files.writeString(FILE_TMP, GSON.toJson(profiles, MAP_TYPE), StandardCharsets.UTF_8);
            try {
                Files.move(FILE_TMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(FILE_TMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    /** Zera UM perfil (tudo 0); se name for null, zera todos. */
    public static synchronized void reset(String name) {
        for (String profileName : PROFILES) {
            if (name == null || name.equals(profileName)) {
                profiles.put(profileName, new Profile());
            }
        }
        save();
    }

    /** @return true se perfil e campo existem e o valor foi alterado (somente aquele campo daquele perfil). */
    public static synchronized boolean set(String profileName, String fieldName, float value) {
        if (!PROFILES.contains(profileName) || !FIELDS.contains(fieldName)) {
            return false;
        }
        Profile profile = profiles.get(profileName);
        if (profile == null) {
            profile = new Profile();
            profiles.put(profileName, profile);
        }
        try {
            Field field = Profile.class.getField(fieldName);
            field.setFloat(profile, value);
            save();
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static List<String> describe(String profileName) {
        List<String> lines = new ArrayList<>();
        for (String name : PROFILES) {
            if (profileName != null && !profileName.equals(name)) {
                continue;
            }
            Profile p = profile(name);
            lines.add("[" + name + "] x=" + p.x + " y=" + p.y + " z=" + p.z
                    + " rotX=" + p.rotX + " rotY=" + p.rotY + " rotZ=" + p.rotZ + " scale=" + p.scale);
        }
        return lines;
    }
}
