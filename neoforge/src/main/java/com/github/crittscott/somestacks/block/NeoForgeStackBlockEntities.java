package com.github.crittscott.somestacks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/** Native update entry points validate the complete stack snapshot before inherited loading. */
public final class NeoForgeStackBlockEntities {
    private NeoForgeStackBlockEntities() {}

    private static void update(StackBlockEntity blockEntity, ValueInput input) {
        if (blockEntity.isValidClientUpdate(input)) {
            blockEntity.loadWithComponents(input);
        }
    }

    public static final class Storage extends StorageStackBE {
        public Storage(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void handleUpdateTag(ValueInput input) { update(this, input); }

        @Override
        public void onDataPacket(Connection connection, ValueInput input) { update(this, input); }
    }

    public static final class Singles extends SinglesStackBE {
        public Singles(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void handleUpdateTag(ValueInput input) { update(this, input); }

        @Override
        public void onDataPacket(Connection connection, ValueInput input) { update(this, input); }
    }

    public static final class Bar extends BarStackBE {
        public Bar(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void handleUpdateTag(ValueInput input) { update(this, input); }

        @Override
        public void onDataPacket(Connection connection, ValueInput input) { update(this, input); }
    }
}
