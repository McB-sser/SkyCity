import org.bukkit.block.Biome;
public class ListBiomes {
  public static void main(String[] args) {
    for (Biome biome : Biome.values()) {
      System.out.println(biome.name());
    }
  }
}
