package example;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class NativeFixtureTest {
  @GameTest
  public void authoritativeBlockState(GameTestHelper helper) {
    helper.setBlock(0, 1, 0, Blocks.GOLD_BLOCK);
    helper.assertBlockPresent(Blocks.GOLD_BLOCK, 0, 1, 0);
    helper.succeed();
  }
}
