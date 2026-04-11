import java.lang.reflect.*;
public class ListBiomeFields {
  public static void main(String[] args) throws Exception {
    Class<?> cls = Class.forName("org.bukkit.block.Biome", false, ListBiomeFields.class.getClassLoader());
    for (Field f : cls.getDeclaredFields()) {
      int m = f.getModifiers();
      if (Modifier.isStatic(m) && cls.isAssignableFrom(f.getType())) {
        System.out.println(f.getName());
      }
    }
  }
}
