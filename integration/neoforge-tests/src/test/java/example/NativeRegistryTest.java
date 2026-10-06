package example;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Uses ModDevGradle's native FML JUnit launch and real bootstrapped game registries. */
class NativeRegistryTest {
  @BeforeAll
  static void bootstrap() {
    net.minecraft.SharedConstants.tryDetectVersion();
    net.minecraft.server.Bootstrap.bootStrap();
  }

  @Test
  void blocksAndItemsUseNativeRegistries() {
    assertEquals(
        "minecraft:gold_block", BuiltInRegistries.BLOCK.getKey(Blocks.GOLD_BLOCK).toString());
    assertSame(Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK.defaultBlockState().getBlock());
    assertEquals("minecraft:stone", BuiltInRegistries.ITEM.getKey(Items.STONE).toString());
  }
}
